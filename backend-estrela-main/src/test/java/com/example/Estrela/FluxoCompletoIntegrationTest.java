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

        // 10. Prestador conclui — saldo é creditado
        mockMvc.perform(put("/servicos/" + solicitacaoId + "/concluir")
                        .header("Authorization", "Bearer " + tokenPrestador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

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
