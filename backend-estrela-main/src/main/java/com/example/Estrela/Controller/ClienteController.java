package com.example.Estrela.Controller;

import com.example.Estrela.DTO.*;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Entity.EnderecoCliente;
import com.example.Estrela.Service.ClienteService;
import com.example.Estrela.Service.EnderecoClienteService;
import com.example.Estrela.Service.FavoritoService;
import com.example.Estrela.Service.NotificacaoService;
import com.example.Estrela.Service.PagamentoService;
import com.example.Estrela.security.TaskGoUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Cadastro e consulta de clientes, e a área que o cliente autenticado usa para manter a
 * própria conta e consultar o que ela produziu.
 *
 * <p>As rotas sob {@code /clientes/me} resolvem o cliente pelo token e MUST NOT aceitar
 * identificador informado por quem chama — assim uma conta não alcança a outra.
 *
 * <p>Todas as atualizações usam {@code PUT}: {@code PATCH} não está liberado no CORS
 * (ver {@code SecurityConfig}), então um endpoint PATCH seria inalcançável pelo frontend.
 */
@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final EnderecoClienteService enderecoService;
    private final PagamentoService pagamentoService;
    private final FavoritoService favoritoService;
    private final NotificacaoService notificacaoService;

    public ClienteController(ClienteService clienteService,
                             EnderecoClienteService enderecoService,
                             PagamentoService pagamentoService,
                             FavoritoService favoritoService,
                             NotificacaoService notificacaoService) {
        this.clienteService = clienteService;
        this.enderecoService = enderecoService;
        this.pagamentoService = pagamentoService;
        this.favoritoService = favoritoService;
        this.notificacaoService = notificacaoService;
    }

    @PostMapping
    public ClienteResponse criar(@Valid @RequestBody CadastroClienteRequest request) {
        return paraResposta(clienteService.criar(request));
    }

    @GetMapping
    public List<ClienteResponse> listar() {
        return clienteService.listar().stream().map(this::paraResposta).toList();
    }

    /**
     * Perfil da conta autenticada.
     *
     * @param usuario cliente autenticado, resolvido do token
     * @return os dados cadastrais da própria conta, sem a senha
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se a conta não existir (HTTP 404)
     */
    @GetMapping("/me")
    public ClientePerfilResponse meuPerfil(@AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraPerfil(clienteService.buscarPorId(usuario.getId()));
    }

    /**
     * Atualiza o perfil da conta autenticada.
     *
     * @param request dados novos
     * @param usuario cliente autenticado, resolvido do token
     * @return o perfil já atualizado
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se a conta não existir (HTTP 404)
     * @throws com.example.Estrela.exception.EstadoInvalidoException       se o e-mail já for de outra conta (HTTP 409)
     */
    @PutMapping("/me")
    public ClientePerfilResponse atualizarMeuPerfil(@Valid @RequestBody AtualizarPerfilClienteRequest request,
                                                    @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraPerfil(clienteService.atualizarPerfil(usuario.getId(), request));
    }

    /**
     * Substitui a foto de perfil da conta autenticada.
     *
     * @param foto    imagem PNG ou JPEG
     * @param usuario cliente autenticado, resolvido do token
     * @return o perfil com a referência da foto nova
     * @throws com.example.Estrela.exception.ArquivoInvalidoException se o arquivo não for imagem aceita (HTTP 400)
     */
    @PutMapping("/me/foto")
    public ClientePerfilResponse atualizarMinhaFoto(@RequestParam("foto") MultipartFile foto,
                                                    @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraPerfil(clienteService.atualizarFoto(usuario.getId(), foto));
    }

    /**
     * Desativa a conta autenticada. Não apaga: solicitações, pagamentos e avaliações permanecem.
     *
     * @param usuario cliente autenticado, resolvido do token
     * @throws com.example.Estrela.exception.EstadoInvalidoException se houver atendimento em aberto (HTTP 409)
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativarMinhaConta(@AuthenticationPrincipal TaskGoUserDetails usuario) {
        clienteService.desativar(usuario.getId());
    }

    /**
     * Endereços de atendimento da conta autenticada.
     *
     * @param usuario cliente autenticado, resolvido do token
     * @return endereços ativos, lista vazia quando não há nenhum
     */
    @GetMapping("/me/enderecos")
    public List<EnderecoClienteResponse> listarMeusEnderecos(
            @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return enderecoService.listar(usuario.getId()).stream()
                .map(EnderecoClienteResponse::de)
                .toList();
    }

    /**
     * Cadastra um endereço para a conta autenticada. O primeiro nasce padrão.
     *
     * @param request dados do endereço
     * @param usuario cliente autenticado, resolvido do token
     * @return o endereço criado
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se a conta não existir (HTTP 404)
     */
    @PostMapping("/me/enderecos")
    @ResponseStatus(HttpStatus.CREATED)
    public EnderecoClienteResponse criarMeuEndereco(@Valid @RequestBody EnderecoClienteRequest request,
                                                    @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return EnderecoClienteResponse.de(enderecoService.criar(usuario.getId(), request));
    }

    /**
     * Atualiza um endereço da conta autenticada.
     *
     * @param id      endereço a alterar
     * @param request dados novos
     * @param usuario cliente autenticado, resolvido do token
     * @return o endereço atualizado
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se o endereço não existir (HTTP 404)
     * @throws com.example.Estrela.exception.AcessoNegadoException         se for de outra conta (HTTP 403)
     */
    @PutMapping("/me/enderecos/{id}")
    public EnderecoClienteResponse atualizarMeuEndereco(@PathVariable Long id,
                                                        @Valid @RequestBody EnderecoClienteRequest request,
                                                        @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return EnderecoClienteResponse.de(enderecoService.atualizar(usuario.getId(), id, request));
    }

    /**
     * Remove logicamente um endereço da conta autenticada. Solicitações que o referenciam
     * continuam resolvendo o local do atendimento.
     *
     * @param id      endereço a remover
     * @param usuario cliente autenticado, resolvido do token
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se o endereço não existir (HTTP 404)
     * @throws com.example.Estrela.exception.AcessoNegadoException         se for de outra conta (HTTP 403)
     */
    @DeleteMapping("/me/enderecos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerMeuEndereco(@PathVariable Long id,
                                   @AuthenticationPrincipal TaskGoUserDetails usuario) {
        enderecoService.remover(usuario.getId(), id);
    }

    /**
     * Extrato de pagamentos do cliente autenticado, do mais recente para o mais antigo.
     *
     * <p>É a fonte única da tabela de transações e do comprovante no painel: todos os
     * valores vêm daqui, e a interface não calcula nem infere nenhum deles.
     *
     * @param usuario cliente autenticado, resolvido do token
     * @return lançamentos do cliente, lista vazia quando ele nunca pagou nada
     */
    @GetMapping("/me/pagamentos")
    public List<PagamentoExtratoResponse> listarMeusPagamentos(
            @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return pagamentoService.listarExtratoDoCliente(usuario.getId());
    }

    /**
     * Prestadores favoritados pela conta autenticada, do mais recente para o mais antigo.
     *
     * @param usuario cliente autenticado, resolvido do token
     * @return favoritos do cliente, lista vazia quando ele não marcou ninguém
     */
    @GetMapping("/me/favoritos")
    public List<FavoritoResponse> listarMeusFavoritos(@AuthenticationPrincipal TaskGoUserDetails usuario) {
        return favoritoService.listar(usuario.getId());
    }

    /**
     * Marca um prestador como favorito da conta autenticada.
     *
     * @param request prestador a favoritar
     * @param usuario cliente autenticado, resolvido do token
     * @return o favorito criado
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se o prestador não existir (HTTP 404)
     * @throws com.example.Estrela.exception.EstadoInvalidoException       se já for favorito (HTTP 409)
     */
    @PostMapping("/me/favoritos")
    @ResponseStatus(HttpStatus.CREATED)
    public FavoritoResponse marcarFavorito(@Valid @RequestBody FavoritoRequest request,
                                           @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return favoritoService.marcar(usuario.getId(), request.prestadorId());
    }

    /**
     * Remove um favorito da conta autenticada.
     *
     * @param prestadorId prestador a desfavoritar
     * @param usuario     cliente autenticado, resolvido do token
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se não estiver entre os favoritos (HTTP 404)
     */
    @DeleteMapping("/me/favoritos/{prestadorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerFavorito(@PathVariable Long prestadorId,
                                @AuthenticationPrincipal TaskGoUserDetails usuario) {
        favoritoService.remover(usuario.getId(), prestadorId);
    }

    /**
     * Avisos de atividade da conta autenticada, dos mais recentes para os mais antigos.
     *
     * <p>Apurados do estado da conta, não de registros armazenados: um aviso desaparece
     * quando o fato que o originou é resolvido, e por isso não há marcação de leitura.
     *
     * @param usuario cliente autenticado, resolvido do token
     * @return avisos pendentes, lista vazia quando não há nada a fazer
     */
    @GetMapping("/me/notificacoes")
    public List<NotificacaoResponse> listarMinhasNotificacoes(
            @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return notificacaoService.listarDoCliente(usuario.getId());
    }

    private ClienteResponse paraResposta(Cliente cliente) {
        return new ClienteResponse(cliente.getIdCliente(), cliente.getNome(), cliente.getIdade(),
                cliente.getCidade(), cliente.getTipo_cliente(), cliente.getEmail());
    }

    private ClientePerfilResponse paraPerfil(Cliente cliente) {
        return new ClientePerfilResponse(cliente.getIdCliente(), cliente.getNome(), cliente.getEmail(),
                cliente.getTelefone(), cliente.getIdade(), cliente.getCidade(),
                cliente.getTipo_cliente(), cliente.getFotoUrl());
    }
}
