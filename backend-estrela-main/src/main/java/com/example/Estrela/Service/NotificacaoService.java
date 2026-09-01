package com.example.Estrela.Service;

import com.example.Estrela.DTO.NotificacaoResponse;
import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.StatusPagamento;
import com.example.Estrela.Entity.StatusSolicitacao;
import com.example.Estrela.repository.FatoServicoRepository;
import com.example.Estrela.repository.MensagemRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Avisos de atividade do cliente, <b>apurados do estado</b> e não armazenados.
 *
 * <p>Uma tabela de notificações exigiria produzir o registro no momento do fato — eventos de
 * domínio disparados por {@code FatoServicoService} e {@code PagamentoService} — mais estado
 * de leitura e política de retenção. Isso é a infraestrutura da US-11, fora de escopo.
 *
 * <p>A consequência aceita é que o feed responde apenas "o que exige minha atenção agora", e
 * não guarda histórico informativo. Em troca, um aviso desaparece sozinho quando o cliente
 * age — que é justamente o comportamento desejado de um contador de pendências — e nada
 * precisa ser marcado como lido.
 */
@Service
public class NotificacaoService {

    private final FatoServicoRepository fatoServicoRepository;
    private final PagamentoService pagamentoService;
    private final MensagemRepository mensagemRepository;

    public NotificacaoService(FatoServicoRepository fatoServicoRepository,
                              PagamentoService pagamentoService,
                              MensagemRepository mensagemRepository) {
        this.fatoServicoRepository = fatoServicoRepository;
        this.pagamentoService = pagamentoService;
        this.mensagemRepository = mensagemRepository;
    }

    /**
     * Avisos do cliente, dos mais recentes para os mais antigos.
     *
     * @param clienteId cliente autenticado
     * @return avisos pendentes, lista vazia quando não há nada a fazer
     */
    public List<NotificacaoResponse> listarDoCliente(Long clienteId) {
        List<NotificacaoResponse> avisos = new ArrayList<>();

        for (FatoServico servico : fatoServicoRepository.findByCliente_IdCliente(clienteId)) {
            avisoDaSolicitacao(servico).ifPresent(avisos::add);
        }

        long naoLidas = mensagemRepository.contarNaoLidasDoCliente(clienteId);
        if (naoLidas > 0) {
            avisos.add(new NotificacaoResponse("MENSAGENS_NAO_LIDAS",
                    naoLidas == 1
                            ? "Você tem 1 mensagem não lida"
                            : "Você tem " + naoLidas + " mensagens não lidas",
                    null, LocalDateTime.now()));
        }

        avisos.sort(Comparator.comparing(NotificacaoResponse::momento,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return avisos;
    }

    private java.util.Optional<NotificacaoResponse> avisoDaSolicitacao(FatoServico servico) {
        StatusSolicitacao status = servico.getStatus();
        Long id = servico.getId_servico();
        String prestador = servico.getPrestador() != null ? servico.getPrestador().getNome() : "o profissional";

        if (status == StatusSolicitacao.ACEITO && !pagamentoService.possuiPagamentoRetido(servico)) {
            return java.util.Optional.of(new NotificacaoResponse("PAGAMENTO_PENDENTE",
                    prestador + " aceitou seu pedido. Falta pagar para o atendimento começar",
                    id, servico.getAceitoEm()));
        }

        if (status == StatusSolicitacao.EM_ANDAMENTO) {
            return java.util.Optional.of(new NotificacaoResponse("ATENDIMENTO_EM_ANDAMENTO",
                    "Atendimento de " + prestador + " em andamento",
                    id, servico.getIniciadoEm()));
        }

        if (status == StatusSolicitacao.CONCLUIDO) {
            return java.util.Optional.of(new NotificacaoResponse("AVALIACAO_PENDENTE",
                    "Como foi o atendimento de " + prestador + "? Sua avaliação está pendente",
                    id, servico.getConcluidoEm()));
        }

        if (status == StatusSolicitacao.RECUSADO) {
            return java.util.Optional.of(new NotificacaoResponse("SOLICITACAO_RECUSADA",
                    prestador + " recusou seu pedido",
                    id, servico.getAceitoEm() != null ? servico.getAceitoEm() : servico.getCriadoEm()));
        }

        if (status == StatusSolicitacao.CANCELADO) {
            StatusPagamento pagamento = pagamentoService.obterStatus(servico);
            if (pagamento == StatusPagamento.ESTORNADO || pagamento == StatusPagamento.ESTORNADO_PARCIAL) {
                return java.util.Optional.of(new NotificacaoResponse("CANCELAMENTO_ESTORNADO",
                        pagamento == StatusPagamento.ESTORNADO_PARCIAL
                                ? "Pedido cancelado. Parte do valor foi retida como taxa de cancelamento"
                                : "Pedido cancelado. O valor foi devolvido integralmente",
                        id, servico.getCriadoEm()));
            }
        }

        return java.util.Optional.empty();
    }
}
