package com.example.Estrela.Controller;

import com.example.Estrela.DTO.*;
import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.Pagamento;
import com.example.Estrela.Service.FatoServicoService;
import com.example.Estrela.Service.MensagemService;
import com.example.Estrela.Service.PagamentoService;
import com.example.Estrela.security.TaskGoUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ciclo de vida de uma solicitação de serviço (RN02): solicitar, aceitar/recusar, pagar, concluir,
 * avaliar, cancelar.
 */
@RestController
@RequestMapping("/servicos")
public class FatoServicoController {

    private final FatoServicoService service;
    private final PagamentoService pagamentoService;
    private final MensagemService mensagemService;

    public FatoServicoController(FatoServicoService service,
                                 PagamentoService pagamentoService,
                                 MensagemService mensagemService) {
        this.service = service;
        this.pagamentoService = pagamentoService;
        this.mensagemService = mensagemService;
    }

    /**
     * Conversa de uma solicitação, da mensagem mais antiga para a mais recente.
     *
     * @param id      solicitação consultada
     * @param usuario conta autenticada, resolvida do token
     * @return mensagens em ordem cronológica, lista vazia quando ninguém escreveu ainda
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se a solicitação não existir (HTTP 404)
     * @throws com.example.Estrela.exception.AcessoNegadoException         se quem consulta não participa dela (HTTP 403)
     */
    @GetMapping("/{id}/mensagens")
    public List<MensagemResponse> listarMensagens(@PathVariable Long id,
                                                  @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return mensagemService.listar(id, usuario.getId(), usuario.getRole());
    }

    /**
     * Envia uma mensagem na conversa de uma solicitação ativa.
     *
     * @param id      solicitação
     * @param request conteúdo da mensagem
     * @param usuario conta autenticada, resolvida do token
     * @return a mensagem registrada
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se a solicitação não existir (HTTP 404)
     * @throws com.example.Estrela.exception.AcessoNegadoException         se quem escreve não participa dela (HTTP 403)
     * @throws com.example.Estrela.exception.EstadoInvalidoException       se a solicitação estiver encerrada (HTTP 409)
     */
    @PostMapping("/{id}/mensagens")
    @ResponseStatus(HttpStatus.CREATED)
    public MensagemResponse enviarMensagem(@PathVariable Long id,
                                           @Valid @RequestBody MensagemRequest request,
                                           @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return mensagemService.enviar(id, usuario.getId(), usuario.getRole(), request);
    }

    /**
     * Marca como lidas as mensagens que a outra parte escreveu nesta solicitação.
     *
     * @param id      solicitação
     * @param usuario conta autenticada, resolvida do token
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se a solicitação não existir (HTTP 404)
     * @throws com.example.Estrela.exception.AcessoNegadoException         se quem marca não participa dela (HTTP 403)
     */
    @PutMapping("/{id}/mensagens/lidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarMensagensLidas(@PathVariable Long id,
                                     @AuthenticationPrincipal TaskGoUserDetails usuario) {
        mensagemService.marcarLidas(id, usuario.getId(), usuario.getRole());
    }

    /**
     * @throws com.example.Estrela.exception.EstadoInvalidoException      se já houver solicitação em aberto com o mesmo prestador (HTTP 409)
     * @throws com.example.Estrela.exception.RecursoIndisponivelException se o serviço/prestador não estiver disponível (HTTP 422)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitacaoResponse solicitar(@Valid @RequestBody SolicitacaoRequest request,
                                          @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraResposta(service.solicitar(usuario.getId(), request));
    }

    @GetMapping("/minhas")
    public List<SolicitacaoResponse> listarMinhas(@AuthenticationPrincipal TaskGoUserDetails usuario) {
        return service.listarMinhas(usuario.getId(), usuario.getRole()).stream().map(this::paraResposta).toList();
    }

    @PutMapping("/{id}/aceitar")
    public SolicitacaoResponse aceitar(@PathVariable Long id, @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraResposta(service.aceitar(id, usuario.getId()));
    }

    @PutMapping("/{id}/recusar")
    public SolicitacaoResponse recusar(@PathVariable Long id, @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraResposta(service.recusar(id, usuario.getId()));
    }

    /**
     * @throws com.example.Estrela.exception.PagamentoRecusadoException se a cobrança for recusada (HTTP 402)
     */
    @PostMapping("/{id}/pagamento")
    public PagamentoResponse pagar(@PathVariable Long id, @RequestBody PagamentoRequest request,
                                    @AuthenticationPrincipal TaskGoUserDetails usuario) {
        Pagamento pagamento = service.pagar(id, usuario.getId(), request);
        return new PagamentoResponse(pagamento.getId(), pagamento.getStatus(), pagamento.getValorBruto(),
                pagamento.getValorTaxa(), pagamento.getValorLiquido(), pagamento.getMetodoPagamento());
    }

    /**
     * @throws com.example.Estrela.exception.EstadoInvalidoException se não houver pagamento confirmado em custódia (HTTP 409)
     */
    @PutMapping("/{id}/concluir")
    public SolicitacaoResponse concluir(@PathVariable Long id, @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraResposta(service.concluir(id, usuario.getId()));
    }

    @PutMapping("/{id}/avaliar")
    public SolicitacaoResponse avaliar(@PathVariable Long id, @Valid @RequestBody AvaliacaoRequest request,
                                        @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraResposta(service.avaliar(id, usuario.getId(), request));
    }

    @PutMapping("/{id}/cancelar")
    public SolicitacaoResponse cancelar(@PathVariable Long id, @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraResposta(service.cancelar(id, usuario.getId(), usuario.getRole()));
    }

    private SolicitacaoResponse paraResposta(FatoServico servico) {
        return new SolicitacaoResponse(
                servico.getId_servico(),
                servico.getStatus(),
                servico.getCliente().getIdCliente(),
                servico.getCliente().getNome(),
                servico.getPrestador().getIdPrestador(),
                servico.getPrestador().getNome(),
                servico.getServicoOfertado() != null ? servico.getServicoOfertado().getId() : null,
                servico.getServicoOfertado() != null ? servico.getServicoOfertado().getCategoria() : null,
                servico.getValor(),
                servico.getAvaliacao(),
                servico.getComentarioAvaliacao(),
                pagamentoService.obterStatus(servico)
        );
    }
}
