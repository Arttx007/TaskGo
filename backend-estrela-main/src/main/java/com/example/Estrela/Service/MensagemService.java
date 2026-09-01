package com.example.Estrela.Service;

import com.example.Estrela.DTO.MensagemRequest;
import com.example.Estrela.DTO.MensagemResponse;
import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.Mensagem;
import com.example.Estrela.Entity.StatusSolicitacao;
import com.example.Estrela.Entity.TipoUsuario;
import com.example.Estrela.exception.AcessoNegadoException;
import com.example.Estrela.exception.EstadoInvalidoException;
import com.example.Estrela.repository.MensagemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Conversa entre o cliente e o prestador de uma solicitação.
 *
 * <p>Não existe conversa fora de uma solicitação, e apenas as duas partes dela leem ou
 * escrevem — inclusive o admin recebe 403. Isso é deliberado: a conversa é registro do que
 * foi combinado entre duas pessoas, e abri-la a terceiros seria decisão de produto própria.
 *
 * <p>A conversa continua <b>legível</b> depois de a solicitação encerrar, para que o
 * combinado permaneça verificável, mas não aceita mensagem nova.
 */
@Service
public class MensagemService {

    /** Estados em que a solicitação está encerrada e não aceita mensagem nova. */
    private static final Set<StatusSolicitacao> ENCERRADAS = Set.of(
            StatusSolicitacao.RECUSADO, StatusSolicitacao.CANCELADO, StatusSolicitacao.AVALIADO);

    private final MensagemRepository mensagemRepository;
    private final FatoServicoService fatoServicoService;

    public MensagemService(MensagemRepository mensagemRepository,
                           FatoServicoService fatoServicoService) {
        this.mensagemRepository = mensagemRepository;
        this.fatoServicoService = fatoServicoService;
    }

    /**
     * Conversa de uma solicitação, da mensagem mais antiga para a mais recente.
     *
     * @param solicitacaoId solicitação consultada
     * @param usuarioId     conta autenticada
     * @param papel         papel da conta autenticada
     * @return mensagens em ordem cronológica, lista vazia quando ninguém escreveu ainda
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se a solicitação não existir (HTTP 404)
     * @throws AcessoNegadoException se quem consulta não é parte da solicitação (HTTP 403)
     */
    public List<MensagemResponse> listar(Long solicitacaoId, Long usuarioId, TipoUsuario papel) {
        FatoServico servico = exigirParte(solicitacaoId, usuarioId, papel);

        return mensagemRepository.listarConversa(servico.getId_servico()).stream()
                .map(m -> paraResposta(m, servico))
                .toList();
    }

    /**
     * Registra uma mensagem na conversa de uma solicitação ativa.
     *
     * @param solicitacaoId solicitação
     * @param usuarioId     conta autenticada
     * @param papel         papel da conta autenticada
     * @param request       conteúdo da mensagem
     * @return a mensagem registrada
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se a solicitação não existir (HTTP 404)
     * @throws AcessoNegadoException   se quem escreve não é parte da solicitação (HTTP 403)
     * @throws EstadoInvalidoException se a solicitação já estiver encerrada (HTTP 409)
     */
    @Transactional
    public MensagemResponse enviar(Long solicitacaoId, Long usuarioId, TipoUsuario papel,
                                   MensagemRequest request) {
        FatoServico servico = exigirParte(solicitacaoId, usuarioId, papel);

        if (ENCERRADAS.contains(servico.getStatus())) {
            throw new EstadoInvalidoException(
                    "Esta solicitação já foi encerrada e não aceita novas mensagens");
        }

        Mensagem mensagem = new Mensagem();
        mensagem.setFatoServico(servico);
        mensagem.setRemetenteTipo(papel);
        mensagem.setRemetenteId(usuarioId);
        mensagem.setConteudo(request.conteudo().trim());
        mensagem.setCriadoEm(LocalDateTime.now());

        return paraResposta(mensagemRepository.save(mensagem), servico);
    }

    /**
     * Marca como lidas as mensagens que a outra parte escreveu.
     *
     * <p>Não alcança as próprias mensagens de quem marca: "lida" significa que o destinatário
     * viu, e ninguém precisa confirmar leitura do que escreveu.
     *
     * @param solicitacaoId solicitação
     * @param usuarioId     conta autenticada
     * @param papel         papel da conta autenticada
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se a solicitação não existir (HTTP 404)
     * @throws AcessoNegadoException se quem marca não é parte da solicitação (HTTP 403)
     */
    @Transactional
    public void marcarLidas(Long solicitacaoId, Long usuarioId, TipoUsuario papel) {
        FatoServico servico = exigirParte(solicitacaoId, usuarioId, papel);

        LocalDateTime agora = LocalDateTime.now();
        mensagemRepository.listarNaoLidasPara(servico.getId_servico(), papel)
                .forEach(m -> {
                    m.setLidaEm(agora);
                    mensagemRepository.save(m);
                });
    }

    private FatoServico exigirParte(Long solicitacaoId, Long usuarioId, TipoUsuario papel) {
        return fatoServicoService.buscarEValidarParte(solicitacaoId, usuarioId, papel);
    }

    private MensagemResponse paraResposta(Mensagem mensagem, FatoServico servico) {
        String nome = mensagem.getRemetenteTipo() == TipoUsuario.CLIENTE
                ? (servico.getCliente() != null ? servico.getCliente().getNome() : null)
                : (servico.getPrestador() != null ? servico.getPrestador().getNome() : null);

        return new MensagemResponse(mensagem.getId(), mensagem.getConteudo(),
                mensagem.getRemetenteTipo(), nome, mensagem.getCriadoEm(),
                mensagem.getLidaEm() != null);
    }
}
