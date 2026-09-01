package com.example.Estrela.Service;

import com.example.Estrela.DTO.NotificacaoResponse;
import com.example.Estrela.Entity.*;
import com.example.Estrela.repository.FatoServicoRepository;
import com.example.Estrela.repository.MensagemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Avisos de atividade apurados do estado da conta, sem tabela e sem estado de leitura.
 */
@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    @Mock private FatoServicoRepository fatoServicoRepository;
    @Mock private PagamentoService pagamentoService;
    @Mock private MensagemRepository mensagemRepository;

    @InjectMocks
    private NotificacaoService notificacaoService;

    private Prestador prestador;

    @BeforeEach
    void setUp() {
        prestador = new Prestador();
        prestador.setIdPrestador(10L);
        prestador.setNome("Carlos");

        lenient().when(mensagemRepository.contarNaoLidasDoCliente(anyLong())).thenReturn(0L);
    }

    @Test
    void duasPendenciasDistintasGeramDoisAvisosApontandoParaSuasSolicitacoes() {
        FatoServico aceitaNaoPaga = solicitacao(1L, StatusSolicitacao.ACEITO);
        aceitaNaoPaga.setAceitoEm(LocalDateTime.now().minusHours(2));
        FatoServico concluida = solicitacao(2L, StatusSolicitacao.CONCLUIDO);
        concluida.setConcluidoEm(LocalDateTime.now().minusHours(1));

        when(fatoServicoRepository.findByCliente_IdCliente(7L))
                .thenReturn(List.of(aceitaNaoPaga, concluida));
        when(pagamentoService.possuiPagamentoRetido(aceitaNaoPaga)).thenReturn(false);

        List<NotificacaoResponse> avisos = notificacaoService.listarDoCliente(7L);

        assertThat(avisos).hasSize(2);
        assertThat(avisos).extracting(NotificacaoResponse::tipo)
                .containsExactly("AVALIACAO_PENDENTE", "PAGAMENTO_PENDENTE");
        assertThat(avisos).extracting(NotificacaoResponse::solicitacaoId).containsExactly(2L, 1L);
    }

    @Test
    void solicitacaoAceitaEJaPagaNaoGeraAvisoDePagamento() {
        FatoServico aceitaPaga = solicitacao(1L, StatusSolicitacao.ACEITO);
        when(fatoServicoRepository.findByCliente_IdCliente(7L)).thenReturn(List.of(aceitaPaga));
        when(pagamentoService.possuiPagamentoRetido(aceitaPaga)).thenReturn(true);

        assertThat(notificacaoService.listarDoCliente(7L)).isEmpty();
    }

    @Test
    void avisoDeAvaliacaoDesapareceDepoisDeAvaliar() {
        FatoServico concluida = solicitacao(1L, StatusSolicitacao.CONCLUIDO);
        when(fatoServicoRepository.findByCliente_IdCliente(7L)).thenReturn(List.of(concluida));

        assertThat(notificacaoService.listarDoCliente(7L))
                .extracting(NotificacaoResponse::tipo).containsExactly("AVALIACAO_PENDENTE");

        // O cliente avalia: o mesmo estado passa a AVALIADO e o aviso deixa de existir,
        // sem que nenhuma marcação de leitura tenha sido feita.
        concluida.setStatus(StatusSolicitacao.AVALIADO);

        assertThat(notificacaoService.listarDoCliente(7L)).isEmpty();
    }

    @Test
    void atendimentoEmAndamentoGeraAvisoProprio() {
        FatoServico emAndamento = solicitacao(1L, StatusSolicitacao.EM_ANDAMENTO);
        emAndamento.setIniciadoEm(LocalDateTime.now());
        when(fatoServicoRepository.findByCliente_IdCliente(7L)).thenReturn(List.of(emAndamento));

        assertThat(notificacaoService.listarDoCliente(7L))
                .extracting(NotificacaoResponse::tipo).containsExactly("ATENDIMENTO_EM_ANDAMENTO");
    }

    @Test
    void solicitacaoRecusadaGeraAviso() {
        FatoServico recusada = solicitacao(1L, StatusSolicitacao.RECUSADO);
        recusada.setCriadoEm(LocalDateTime.now());
        when(fatoServicoRepository.findByCliente_IdCliente(7L)).thenReturn(List.of(recusada));

        assertThat(notificacaoService.listarDoCliente(7L))
                .extracting(NotificacaoResponse::tipo).containsExactly("SOLICITACAO_RECUSADA");
    }

    @Test
    void cancelamentoComEstornoParcialAvisaQueHouveRetencao() {
        FatoServico cancelada = solicitacao(1L, StatusSolicitacao.CANCELADO);
        cancelada.setCriadoEm(LocalDateTime.now());
        when(fatoServicoRepository.findByCliente_IdCliente(7L)).thenReturn(List.of(cancelada));
        when(pagamentoService.obterStatus(cancelada)).thenReturn(StatusPagamento.ESTORNADO_PARCIAL);

        NotificacaoResponse aviso = notificacaoService.listarDoCliente(7L).get(0);

        assertThat(aviso.tipo()).isEqualTo("CANCELAMENTO_ESTORNADO");
        assertThat(aviso.texto()).contains("taxa de cancelamento");
    }

    @Test
    void cancelamentoSemPagamentoNaoGeraAviso() {
        FatoServico cancelada = solicitacao(1L, StatusSolicitacao.CANCELADO);
        when(fatoServicoRepository.findByCliente_IdCliente(7L)).thenReturn(List.of(cancelada));
        when(pagamentoService.obterStatus(cancelada)).thenReturn(null);

        assertThat(notificacaoService.listarDoCliente(7L)).isEmpty();
    }

    @Test
    void mensagensNaoLidasGeramUmAvisoAgregado() {
        when(fatoServicoRepository.findByCliente_IdCliente(7L)).thenReturn(List.of());
        when(mensagemRepository.contarNaoLidasDoCliente(7L)).thenReturn(3L);

        NotificacaoResponse aviso = notificacaoService.listarDoCliente(7L).get(0);

        assertThat(aviso.tipo()).isEqualTo("MENSAGENS_NAO_LIDAS");
        assertThat(aviso.texto()).contains("3 mensagens");
        assertThat(aviso.solicitacaoId()).isNull();
    }

    @Test
    void umaMensagemNaoLidaUsaSingular() {
        when(fatoServicoRepository.findByCliente_IdCliente(7L)).thenReturn(List.of());
        when(mensagemRepository.contarNaoLidasDoCliente(7L)).thenReturn(1L);

        assertThat(notificacaoService.listarDoCliente(7L).get(0).texto()).contains("1 mensagem não lida");
    }

    @Test
    void contaSemPendenciaDevolveListaVazia() {
        when(fatoServicoRepository.findByCliente_IdCliente(7L)).thenReturn(List.of(
                solicitacao(1L, StatusSolicitacao.AVALIADO)));

        assertThat(notificacaoService.listarDoCliente(7L)).isEmpty();
    }

    private FatoServico solicitacao(Long id, StatusSolicitacao status) {
        FatoServico servico = new FatoServico();
        servico.setId_servico(id);
        servico.setStatus(status);
        servico.setPrestador(prestador);
        return servico;
    }
}
