package com.example.Estrela;

import com.example.Estrela.Entity.ParametroNegocio;
import com.example.Estrela.repository.ParametroNegocioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fluxo completo do MVP (US-01..US-10) ponta a ponta via MockMvc + H2, e o cenário de autorização
 * cruzada exigido em plan.md §Backend.12 (prestador B não pode agir sobre pedido do prestador A).
 * Não cobre upload real de documento de KYC (multipart) — a aprovação aqui é feita diretamente
 * pelo admin, sem depender do arquivo já ter sido enviado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FluxoCompletoIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ParametroNegocioRepository parametroNegocioRepository;

    // Instância própria em vez de @Autowired: neste projeto (Spring Boot 4) nenhum bean ObjectMapper
    // é exposto no contexto por padrão, só é usado aqui para ler o JSON das respostas do MockMvc.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void seedParametrosDeNegocio() {
        salvarParametro("taxa.limiar", "50.00");
        salvarParametro("taxa.fixa", "5.00");
        salvarParametro("taxa.percentual", "0.10");
        salvarParametro("cancelamento.carencia-minutos", "2");
        salvarParametro("cancelamento.taxa-percentual-perto", "0.15");
        salvarParametro("cancelamento.taxa-percentual-longe", "0.20");
        salvarParametro("cancelamento.limiar-distancia-km", "10");
        salvarParametro("cancelamento.taxa-teto", "50.00");
    }

    private void salvarParametro(String chave, String valor) {
        if (parametroNegocioRepository.findById(chave).isEmpty()) {
            ParametroNegocio parametro = new ParametroNegocio();
            parametro.setChave(chave);
            parametro.setValor(new BigDecimal(valor));
            parametroNegocioRepository.save(parametro);
        }
    }

    @Test
    void percorreOCicloCompletoDeUmAtendimento() throws Exception {
        String sufixo = String.valueOf(System.identityHashCode(this));

        // 1. Cadastro do prestador (KYC PENDENTE)
        JsonNode prestadorCriado = postJson("/prestadores", """
                {"nome":"Carlos Eletricista","especialidade":"Eletricista","cidade":"São Paulo",
                 "email":"carlos.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isCreated());
        long prestadorId = prestadorCriado.get("idPrestador").asLong();
        assertThat(prestadorCriado.get("statusKyc").asText()).isEqualTo("PENDENTE");

        // 2. Admin aprova o KYC
        String tokenAdmin = login("admin@taskgo.com", "admin123", "ADMIN");
        mockMvc.perform(put("/admin/prestadores/" + prestadorId + "/kyc/aprovar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusKyc").value("APROVADO"));

        // 3. Localização com coordenadas, para a busca geo funcionar (endpoint exige autenticação)
        JsonNode localizacao = postJsonAutenticado("/localizacoes", """
                {"cidade":"São Paulo","estado":"SP","bairro":"Centro","latitude":-23.55,"longitude":-46.63}
                """, tokenAdmin, status().isOk());
        long localizacaoId = localizacao.get("id_localizacao").asLong();

        // 4. Prestador loga e publica um serviço no catálogo
        String tokenPrestador = login("carlos.%s@teste.com".formatted(sufixo), "senha12345", "PRESTADOR");
        MvcResult servicoCriadoResult = mockMvc.perform(post("/servicos-ofertados")
                        .header("Authorization", "Bearer " + tokenPrestador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoria":"Eletricista","descricao":"Instalações e reparos",
                                 "preco":80.00,"localizacaoId":%d}
                                """.formatted(localizacaoId)))
                .andExpect(status().isCreated())
                .andReturn();
        long servicoOfertadoId = json(servicoCriadoResult).get("id").asLong();

        // 5. Cliente busca por geolocalização e encontra o serviço
        mockMvc.perform(get("/servicos-ofertados/buscar")
                        .param("categoria", "Eletricista")
                        .param("lat", "-23.55")
                        .param("lon", "-46.63")
                        .param("raioKm", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].servicoOfertadoId").value(servicoOfertadoId));

        // 6. Cadastro e login do cliente
        postJson("/clientes", """
                {"nome":"Maria Cliente","cidade":"São Paulo","tipoCliente":"residencial",
                 "email":"maria.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isOk());
        String tokenCliente = login("maria.%s@teste.com".formatted(sufixo), "senha12345", "CLIENTE");

        // 7. Cliente solicita o serviço
        MvcResult solicitacaoResult = mockMvc.perform(post("/servicos")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"servicoOfertadoId\":" + servicoOfertadoId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SOLICITADO"))
                .andReturn();
        long solicitacaoId = json(solicitacaoResult).get("id").asLong();

        // 8. Prestador aceita
        mockMvc.perform(put("/servicos/" + solicitacaoId + "/aceitar")
                        .header("Authorization", "Bearer " + tokenPrestador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACEITO"));

        // 9. Cliente paga — taxa percentual (80 >= limiar 50): 8.00 de taxa, 72.00 líquido
        mockMvc.perform(post("/servicos/" + solicitacaoId + "/pagamento")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodoPagamento\":\"cartao\",\"simularFalha\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIDO"))
                .andExpect(jsonPath("$.valorTaxa").value(8.00))
                .andExpect(jsonPath("$.valorLiquido").value(72.00));

        // 9b. O código de confirmação foi entregue ao cliente no aceite, e NÃO ao prestador
        MvcResult detalheCliente = mockMvc.perform(get("/servicos/" + solicitacaoId)
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinConfirmacao").isNotEmpty())
                .andReturn();
        String pin = json(detalheCliente).get("pinConfirmacao").asText();

        mockMvc.perform(get("/servicos/" + solicitacaoId)
                        .header("Authorization", "Bearer " + tokenPrestador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinConfirmacao").doesNotExist());

        // 9c. Concluir sem iniciar deixou de existir: ACEITO -> CONCLUIDO é recusado
        mockMvc.perform(put("/servicos/" + solicitacaoId + "/concluir")
                        .header("Authorization", "Bearer " + tokenPrestador))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ESTADO_INVALIDO"));

        // 9d. Código errado é recusado sem alterar o estado
        mockMvc.perform(put("/servicos/" + solicitacaoId + "/iniciar")
                        .header("Authorization", "Bearer " + tokenPrestador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0000\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACESSO_NEGADO"));

        // 9e. Prestador inicia o atendimento com o código que o cliente lhe passou
        mockMvc.perform(put("/servicos/" + solicitacaoId + "/iniciar")
                        .header("Authorization", "Bearer " + tokenPrestador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.iniciadoEm").isNotEmpty());

        // 10. Prestador conclui — saldo é creditado
        mockMvc.perform(put("/servicos/" + solicitacaoId + "/concluir")
                        .header("Authorization", "Bearer " + tokenPrestador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.concluidoEm").isNotEmpty());

        mockMvc.perform(get("/prestadores/" + prestadorId + "/saldo")
                        .header("Authorization", "Bearer " + tokenPrestador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoDisponivel").value(72.00));

        // 11. Cliente avalia
        mockMvc.perform(put("/servicos/" + solicitacaoId + "/avaliar")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":5,\"comentario\":\"Excelente atendimento\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVALIADO"));

        // 12. Prestador saca parte do saldo via Pix
        mockMvc.perform(post("/prestadores/" + prestadorId + "/saques")
                        .header("Authorization", "Bearer " + tokenPrestador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":30.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoRestante").value(42.00));

        // 13. Extrato do cliente reflete o pagamento, com a taxa apurada persistida
        mockMvc.perform(get("/clientes/me/pagamentos")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].solicitacaoId").value(solicitacaoId))
                .andExpect(jsonPath("$[0].valorBruto").value(80.00))
                .andExpect(jsonPath("$[0].valorTaxa").value(8.00))
                .andExpect(jsonPath("$[0].status").value("LIBERADO"))
                .andExpect(jsonPath("$[0].prestadorNome").value("Carlos Eletricista"))
                .andExpect(jsonPath("$[0].categoria").value("Eletricista"))
                .andExpect(jsonPath("$[0].valorTaxaCancelamento").doesNotExist());
    }

    @Test
    void extratoDoClienteExigeAutenticacaoENaoAlcancaOutraConta() throws Exception {
        String sufixo = String.valueOf(System.identityHashCode(this));

        // Sem token a rota é recusada
        mockMvc.perform(get("/clientes/me/pagamentos"))
                .andExpect(status().is4xxClientError());

        // Cliente novo, que nunca pagou nada, recebe lista vazia — e não dado de exemplo
        postJson("/clientes", """
                {"nome":"Cliente Extrato","email":"extrato.%s@teste.com","senha":"senha12345","cidade":"Recife"}
                """.formatted(sufixo), status().isOk());
        String token = login("extrato." + sufixo + "@teste.com", "senha12345", "CLIENTE");

        mockMvc.perform(get("/clientes/me/pagamentos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void prestadorNaoPodeAceitarSolicitacaoDeOutroPrestador() throws Exception {
        String sufixo = String.valueOf(System.identityHashCode(this));

        JsonNode prestadorA = postJson("/prestadores", """
                {"nome":"Prestador A","email":"prestadorA.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isCreated());
        long prestadorAId = prestadorA.get("idPrestador").asLong();

        postJson("/prestadores", """
                {"nome":"Prestador B","email":"prestadorB.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isCreated());

        String tokenAdmin = login("admin@taskgo.com", "admin123", "ADMIN");
        mockMvc.perform(put("/admin/prestadores/" + prestadorAId + "/kyc/aprovar")
                .header("Authorization", "Bearer " + tokenAdmin));

        String tokenPrestadorA = login("prestadorA.%s@teste.com".formatted(sufixo), "senha12345", "PRESTADOR");
        MvcResult servicoResult = mockMvc.perform(post("/servicos-ofertados")
                        .header("Authorization", "Bearer " + tokenPrestadorA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"Pintor\",\"preco\":60.00}"))
                .andExpect(status().isCreated())
                .andReturn();
        long servicoOfertadoId = json(servicoResult).get("id").asLong();

        postJson("/clientes", """
                {"nome":"Cliente Teste","email":"clienteX.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isOk());
        String tokenCliente = login("clienteX.%s@teste.com".formatted(sufixo), "senha12345", "CLIENTE");

        MvcResult solicitacaoResult = mockMvc.perform(post("/servicos")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"servicoOfertadoId\":" + servicoOfertadoId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        long solicitacaoId = json(solicitacaoResult).get("id").asLong();

        String tokenPrestadorB = login("prestadorB.%s@teste.com".formatted(sufixo), "senha12345", "PRESTADOR");

        mockMvc.perform(put("/servicos/" + solicitacaoId + "/aceitar")
                        .header("Authorization", "Bearer " + tokenPrestadorB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACESSO_NEGADO"));
    }


    @Test
    void clienteManteemOProprioPerfilEEnderecos() throws Exception {
        String sufixo = String.valueOf(System.identityHashCode(this));
        String email = "perfil." + sufixo + "@teste.com";

        postJson("/clientes", """
                {"nome":"Perfil Original","email":"%s","senha":"senha12345","cidade":"Recife"}
                """.formatted(email), status().isOk());
        String token = login(email, "senha12345", "CLIENTE");

        // Perfil vem da conta, sem senha
        mockMvc.perform(get("/clientes/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Perfil Original"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.fotoUrl").doesNotExist());

        // Atualização persiste
        mockMvc.perform(put("/clientes/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Perfil Alterado","email":"%s","telefone":"(81) 98888-7777","cidade":"Olinda"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Perfil Alterado"))
                .andExpect(jsonPath("$.telefone").value("(81) 98888-7777"));

        // E-mail inválido responde 400 VALIDACAO com o campo apontado
        mockMvc.perform(put("/clientes/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"email\":\"nao-e-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDACAO"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        // Primeiro endereço nasce padrão
        mockMvc.perform(post("/clientes/me/enderecos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apelido":"Casa","cep":"50000-000","rua":"Rua A","numero":"10",
                                 "bairro":"Centro","cidade":"Recife","uf":"PE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.padrao").value(true))
                .andExpect(jsonPath("$.uf").value("PE"));

        // CEP inválido é recusado
        mockMvc.perform(post("/clientes/me/enderecos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apelido":"Ruim","cep":"123","rua":"Rua B","numero":"1",
                                 "bairro":"Centro","cidade":"Recife","uf":"PE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDACAO"));

        mockMvc.perform(get("/clientes/me/enderecos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void contaDesativadaNaoConsegueMaisEntrar() throws Exception {
        String sufixo = String.valueOf(System.identityHashCode(this));
        String email = "desativar." + sufixo + "@teste.com";

        postJson("/clientes", """
                {"nome":"Vai Sair","email":"%s","senha":"senha12345"}
                """.formatted(email), status().isOk());
        String token = login(email, "senha12345", "CLIENTE");

        mockMvc.perform(delete("/clientes/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senha12345","tipoUsuario":"CLIENTE"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void enderecoDeOutraContaNaoPodeSerAlteradoNemRemovido() throws Exception {
        String sufixo = String.valueOf(System.identityHashCode(this));

        postJson("/clientes", """
                {"nome":"Dono","email":"dono.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isOk());
        postJson("/clientes", """
                {"nome":"Intruso","email":"intruso.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isOk());

        String tokenDono = login("dono." + sufixo + "@teste.com", "senha12345", "CLIENTE");
        String tokenIntruso = login("intruso." + sufixo + "@teste.com", "senha12345", "CLIENTE");

        JsonNode endereco = postJsonAutenticado("/clientes/me/enderecos", """
                {"apelido":"Casa","cep":"50000-000","rua":"Rua A","numero":"10",
                 "bairro":"Centro","cidade":"Recife","uf":"PE"}
                """, tokenDono, status().isCreated());
        long enderecoId = endereco.get("id").asLong();

        mockMvc.perform(delete("/clientes/me/enderecos/" + enderecoId)
                        .header("Authorization", "Bearer " + tokenIntruso))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACESSO_NEGADO"));

        // O endereço do dono permanece intacto
        mockMvc.perform(get("/clientes/me/enderecos").header("Authorization", "Bearer " + tokenDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }


    @Test
    void catalogoDoPrestadorEAutenticadoENaoExpoeContato() throws Exception {
        String sufixo = String.valueOf(System.identityHashCode(this));

        JsonNode prestadorCriado = postJson("/prestadores", """
                {"nome":"Cat Prestador","especialidade":"Encanador","cidade":"Recife",
                 "email":"cat.prest.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isCreated());
        long prestadorId = prestadorCriado.get("idPrestador").asLong();

        // Sem token a rota é recusada
        mockMvc.perform(get("/prestadores/" + prestadorId + "/servicos-ofertados"))
                .andExpect(status().is4xxClientError());

        String tokenAdmin = login("admin@taskgo.com", "admin123", "ADMIN");
        mockMvc.perform(put("/admin/prestadores/" + prestadorId + "/kyc/aprovar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        String tokenPrestador = login("cat.prest." + sufixo + "@teste.com", "senha12345", "PRESTADOR");
        JsonNode localizacao = postJsonAutenticado("/localizacoes", """
                {"cidade":"Recife","estado":"PE","bairro":"Centro","latitude":-8.05,"longitude":-34.9}
                """, tokenPrestador, status().isOk());

        postJsonAutenticado("/servicos-ofertados", """
                {"categoria":"Encanador","descricao":"Reparos","preco":150.00,"localizacaoId":%d}
                """.formatted(localizacao.get("id_localizacao").asLong()), tokenPrestador, status().isCreated());

        postJson("/clientes", """
                {"nome":"Cat Cliente","email":"cat.cli.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isOk());
        String tokenCliente = login("cat.cli." + sufixo + "@teste.com", "senha12345", "CLIENTE");

        mockMvc.perform(get("/prestadores/" + prestadorId + "/servicos-ofertados")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoria").value("Encanador"))
                .andExpect(jsonPath("$[0].preco").value(150.00))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].chavePix").doesNotExist())
                .andExpect(jsonPath("$[0].documentoIdentidadeUrl").doesNotExist())
                .andExpect(jsonPath("$[0].comprovantePixUrl").doesNotExist());
    }

    @Test
    void clienteFavoritaListaERemovePrestador() throws Exception {
        String sufixo = String.valueOf(System.identityHashCode(this));

        JsonNode prestadorCriado = postJson("/prestadores", """
                {"nome":"Fav Prestador","especialidade":"Pintor","cidade":"Recife",
                 "email":"fav.prest.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isCreated());
        long prestadorId = prestadorCriado.get("idPrestador").asLong();

        postJson("/clientes", """
                {"nome":"Fav Cliente","email":"fav.cli.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isOk());
        postJson("/clientes", """
                {"nome":"Outro Cliente","email":"fav.outro.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isOk());

        String token = login("fav.cli." + sufixo + "@teste.com", "senha12345", "CLIENTE");
        String tokenOutro = login("fav.outro." + sufixo + "@teste.com", "senha12345", "CLIENTE");

        // Lista nasce vazia — sem card de exemplo
        mockMvc.perform(get("/clientes/me/favoritos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/clientes/me/favoritos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prestadorId\":%d}".formatted(prestadorId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Fav Prestador"))
                .andExpect(jsonPath("$.disponivel").value(false)); // aprovado? não, e sem serviço ativo

        // Favoritar de novo é recusado
        mockMvc.perform(post("/clientes/me/favoritos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prestadorId\":%d}".formatted(prestadorId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ESTADO_INVALIDO"));

        // Prestador inexistente é 404
        mockMvc.perform(post("/clientes/me/favoritos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prestadorId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RECURSO_NAO_ENCONTRADO"));

        // O favorito não aparece para outra conta
        mockMvc.perform(get("/clientes/me/favoritos").header("Authorization", "Bearer " + tokenOutro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Remover quem não é favorito da conta é 404
        mockMvc.perform(delete("/clientes/me/favoritos/" + prestadorId)
                        .header("Authorization", "Bearer " + tokenOutro))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/clientes/me/favoritos/" + prestadorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/clientes/me/favoritos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }


    @Test
    void avisosDeAtividadeSaoDerivadosEnaoAlcancamOutraConta() throws Exception {
        String sufixo = String.valueOf(System.identityHashCode(this));

        postJson("/clientes", """
                {"nome":"Sem Pendencia","email":"avisos.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isOk());
        String token = login("avisos." + sufixo + "@teste.com", "senha12345", "CLIENTE");

        // Conta recém-criada não tem pendência alguma — e nenhum card de exemplo
        mockMvc.perform(get("/clientes/me/notificacoes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Sem token a rota é recusada
        mockMvc.perform(get("/clientes/me/notificacoes"))
                .andExpect(status().is4xxClientError());
    }


    @Test
    void conversaDaSolicitacaoERestritaAsDuasPartes() throws Exception {
        String sufixo = String.valueOf(System.identityHashCode(this));

        JsonNode prestadorCriado = postJson("/prestadores", """
                {"nome":"Chat Prestador","especialidade":"Eletricista","cidade":"Recife",
                 "email":"chat.prest.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isCreated());
        long prestadorId = prestadorCriado.get("idPrestador").asLong();

        String tokenAdmin = login("admin@taskgo.com", "admin123", "ADMIN");
        mockMvc.perform(put("/admin/prestadores/" + prestadorId + "/kyc/aprovar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        String tokenPrestador = login("chat.prest." + sufixo + "@teste.com", "senha12345", "PRESTADOR");
        JsonNode localizacao = postJsonAutenticado("/localizacoes", """
                {"cidade":"Recife","estado":"PE","bairro":"Centro","latitude":-8.05,"longitude":-34.9}
                """, tokenPrestador, status().isOk());
        JsonNode servicoCriado = postJsonAutenticado("/servicos-ofertados", """
                {"categoria":"Eletricista","descricao":"Reparos","preco":100.00,"localizacaoId":%d}
                """.formatted(localizacao.get("id_localizacao").asLong()), tokenPrestador, status().isCreated());

        postJson("/clientes", """
                {"nome":"Chat Cliente","email":"chat.cli.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isOk());
        postJson("/clientes", """
                {"nome":"Intruso","email":"chat.intruso.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isOk());
        String tokenCliente = login("chat.cli." + sufixo + "@teste.com", "senha12345", "CLIENTE");
        String tokenIntruso = login("chat.intruso." + sufixo + "@teste.com", "senha12345", "CLIENTE");

        JsonNode solicitacao = postJsonAutenticado("/servicos", """
                {"servicoOfertadoId":%d}
                """.formatted(servicoCriado.get("id").asLong()), tokenCliente, status().isCreated());
        long solicitacaoId = solicitacao.get("id").asLong();

        // Conversa nasce vazia — sem bolha de exemplo
        mockMvc.perform(get("/servicos/" + solicitacaoId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/servicos/" + solicitacaoId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conteudo\":\"Estou em casa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remetenteTipo").value("CLIENTE"))
                .andExpect(jsonPath("$.remetenteNome").value("Chat Cliente"))
                .andExpect(jsonPath("$.lida").value(false));

        // O prestador vê a mesma conversa
        mockMvc.perform(get("/servicos/" + solicitacaoId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenPrestador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conteudo").value("Estou em casa"));

        // Terceiro recebe 403 nos três verbos
        mockMvc.perform(get("/servicos/" + solicitacaoId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenIntruso))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACESSO_NEGADO"));
        mockMvc.perform(post("/servicos/" + solicitacaoId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenIntruso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conteudo\":\"invadindo\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/servicos/" + solicitacaoId + "/mensagens/lidas")
                        .header("Authorization", "Bearer " + tokenIntruso))
                .andExpect(status().isForbidden());

        // Conteúdo vazio é recusado
        mockMvc.perform(post("/servicos/" + solicitacaoId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conteudo\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDACAO"));

        // Prestador marca como lida; a mensagem do cliente passa a lida
        mockMvc.perform(put("/servicos/" + solicitacaoId + "/mensagens/lidas")
                        .header("Authorization", "Bearer " + tokenPrestador))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/servicos/" + solicitacaoId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(jsonPath("$[0].lida").value(true));

        // Solicitação cancelada continua legível mas não aceita mensagem nova
        mockMvc.perform(put("/servicos/" + solicitacaoId + "/cancelar")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk());
        mockMvc.perform(get("/servicos/" + solicitacaoId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(post("/servicos/" + solicitacaoId + "/mensagens")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conteudo\":\"depois do fim\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ESTADO_INVALIDO"));
    }


    @Test
    void clienteQueDesisteDepoisDaCarenciaPagaTaxaQueVaiParaOPrestador() throws Exception {
        String sufixo = String.valueOf(System.identityHashCode(this));

        // Carência zero para o teste não precisar esperar dois minutos. O parâmetro existe
        // justamente para ser ajustável sem deploy — aqui isso vira alavanca de teste.
        salvarOuAtualizarParametro("cancelamento.carencia-minutos", "0");

        JsonNode prestadorCriado = postJson("/prestadores", """
                {"nome":"Taxa Prestador","especialidade":"Eletricista","cidade":"Recife",
                 "email":"taxa.prest.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isCreated());
        long prestadorId = prestadorCriado.get("idPrestador").asLong();

        String tokenAdmin = login("admin@taskgo.com", "admin123", "ADMIN");
        mockMvc.perform(put("/admin/prestadores/" + prestadorId + "/kyc/aprovar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        String tokenPrestador = login("taxa.prest." + sufixo + "@teste.com", "senha12345", "PRESTADOR");
        JsonNode localizacao = postJsonAutenticado("/localizacoes", """
                {"cidade":"Recife","estado":"PE","bairro":"Centro","latitude":-8.05,"longitude":-34.90}
                """, tokenPrestador, status().isOk());
        JsonNode servicoCriado = postJsonAutenticado("/servicos-ofertados", """
                {"categoria":"Eletricista","descricao":"Reparos","preco":120.00,"localizacaoId":%d}
                """.formatted(localizacao.get("id_localizacao").asLong()), tokenPrestador, status().isCreated());

        postJson("/clientes", """
                {"nome":"Taxa Cliente","email":"taxa.cli.%s@teste.com","senha":"senha12345"}
                """.formatted(sufixo), status().isOk());
        String tokenCliente = login("taxa.cli." + sufixo + "@teste.com", "senha12345", "CLIENTE");

        // Endereço perto da localização do prestador: percentual menor, 15% de 120 = 18,00
        JsonNode endereco = postJsonAutenticado("/clientes/me/enderecos", """
                {"apelido":"Casa","cep":"50000-000","rua":"Rua A","numero":"10","bairro":"Centro",
                 "cidade":"Recife","uf":"PE","latitude":-8.051,"longitude":-34.901}
                """, tokenCliente, status().isCreated());

        JsonNode solicitacao = postJsonAutenticado("/servicos", """
                {"servicoOfertadoId":%d,"enderecoClienteId":%d}
                """.formatted(servicoCriado.get("id").asLong(), endereco.get("id").asLong()),
                tokenCliente, status().isCreated());
        long solicitacaoId = solicitacao.get("id").asLong();

        mockMvc.perform(put("/servicos/" + solicitacaoId + "/aceitar")
                        .header("Authorization", "Bearer " + tokenPrestador))
                .andExpect(status().isOk());

        mockMvc.perform(post("/servicos/" + solicitacaoId + "/pagamento")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodoPagamento\":\"cartao\",\"simularFalha\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIDO"));

        MvcResult detalhe = mockMvc.perform(get("/servicos/" + solicitacaoId)
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andReturn();
        String pin = json(detalhe).get("pinConfirmacao").asText();

        mockMvc.perform(put("/servicos/" + solicitacaoId + "/iniciar")
                        .header("Authorization", "Bearer " + tokenPrestador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));

        // A tela do cliente já sabe quanto seria retido, para poder avisar antes de confirmar
        mockMvc.perform(get("/servicos/" + solicitacaoId)
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxaCancelamentoPrevista").value(18.00))
                .andExpect(jsonPath("$.enderecoAtendimento.apelido").value("Casa"));

        // O cliente desiste: 18,00 vão para o prestador, 102,00 voltam, plataforma fica com nada
        mockMvc.perform(put("/servicos/" + solicitacaoId + "/cancelar")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        mockMvc.perform(get("/prestadores/" + prestadorId + "/saldo")
                        .header("Authorization", "Bearer " + tokenPrestador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoDisponivel").value(18.00));

        mockMvc.perform(get("/clientes/me/pagamentos")
                        .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ESTORNADO_PARCIAL"))
                .andExpect(jsonPath("$[0].valorTaxaCancelamento").value(18.00))
                .andExpect(jsonPath("$[0].valorEstornado").value(102.00))
                .andExpect(jsonPath("$[0].valorBruto").value(120.00));
    }

    private void salvarOuAtualizarParametro(String chave, String valor) {
        ParametroNegocio parametro = parametroNegocioRepository.findById(chave)
                .orElseGet(ParametroNegocio::new);
        parametro.setChave(chave);
        parametro.setValor(new BigDecimal(valor));
        parametroNegocioRepository.save(parametro);
    }

    private String login(String email, String senha, String tipoUsuario) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"%s","tipoUsuario":"%s"}
                                """.formatted(email, senha, tipoUsuario)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("token").asText();
    }

    private JsonNode postJson(String url, String body, org.springframework.test.web.servlet.ResultMatcher statusEsperado) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(statusEsperado)
                .andReturn();
        return json(result);
    }

    private JsonNode postJsonAutenticado(String url, String body, String token,
                                          org.springframework.test.web.servlet.ResultMatcher statusEsperado) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(statusEsperado)
                .andReturn();
        return json(result);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
