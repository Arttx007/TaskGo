package com.example.Estrela.Service;

import com.example.Estrela.DTO.AvaliacaoRequest;
import com.example.Estrela.DTO.IniciarAtendimentoRequest;
import com.example.Estrela.DTO.SolicitacaoRequest;
import com.example.Estrela.Entity.*;
import com.example.Estrela.exception.AcessoNegadoException;
import com.example.Estrela.exception.EstadoInvalidoException;
import com.example.Estrela.exception.RecursoIndisponivelException;
import com.example.Estrela.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Máquina de estados de RN02 e checagem de dono (US-05/US-07/US-09/US-10).
 */
@ExtendWith(MockitoExtension.class)
class FatoServicoServiceTest {

    @Mock private FatoServicoRepository repository;
    @Mock private ClienteRepository clienteRepo;
    @Mock private PrestadorRepository prestadorRepo;
    @Mock private ServicoOfertadoRepository servicoOfertadoRepo;
    @Mock private TempoRepository tempoRepo;
    @Mock private PagamentoService pagamentoService;
    @Mock private EnderecoClienteService enderecoClienteService;
    @Mock private TaxaCancelamentoService taxaCancelamentoService;

    @InjectMocks
    private FatoServicoService service;

    private Cliente cliente;
    private Prestador prestador;
    private ServicoOfertado servicoOfertado;
    private FatoServico solicitacao;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setIdCliente(1L);

        prestador = new Prestador();
        prestador.setIdPrestador(10L);
        prestador.setStatusKyc(StatusKyc.APROVADO);

        servicoOfertado = new ServicoOfertado();
        servicoOfertado.setId(100L);
        servicoOfertado.setPrestador(prestador);
        servicoOfertado.setStatus(StatusServico.ATIVO);
        servicoOfertado.setPreco(new BigDecimal("80.00"));

        solicitacao = new FatoServico();
        solicitacao.setId_servico(1000L);
        solicitacao.setCliente(cliente);
        solicitacao.setPrestador(prestador);
        solicitacao.setServicoOfertado(servicoOfertado);
        solicitacao.setValor(new BigDecimal("80.00"));
        solicitacao.setStatus(StatusSolicitacao.SOLICITADO);

        lenient().when(repository.save(any(FatoServico.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void solicitarCriaComStatusSolicitado() {
        when(clienteRepo.findById(1L)).thenReturn(Optional.of(cliente));
        when(servicoOfertadoRepo.findById(100L)).thenReturn(Optional.of(servicoOfertado));
        when(repository.findByCliente_IdCliente(1L)).thenReturn(List.of());
        when(tempoRepo.findByData(any(LocalDate.class))).thenReturn(Optional.of(new Tempo()));

        FatoServico criado = service.solicitar(1L, new SolicitacaoRequest(100L, null));

        assertThat(criado.getStatus()).isEqualTo(StatusSolicitacao.SOLICITADO);
        assertThat(criado.getValor()).isEqualByComparingTo("80.00");
    }

    @Test
    void solicitarRejeitaDuplicidadeComMesmoPrestador() {
        FatoServico existente = new FatoServico();
        existente.setPrestador(prestador);
        existente.setStatus(StatusSolicitacao.ACEITO);

        when(clienteRepo.findById(1L)).thenReturn(Optional.of(cliente));
        when(servicoOfertadoRepo.findById(100L)).thenReturn(Optional.of(servicoOfertado));
        when(repository.findByCliente_IdCliente(1L)).thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.solicitar(1L, new SolicitacaoRequest(100L, null)))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void solicitarRejeitaServicoInativo() {
        servicoOfertado.setStatus(StatusServico.INATIVO);
        when(clienteRepo.findById(1L)).thenReturn(Optional.of(cliente));
        when(servicoOfertadoRepo.findById(100L)).thenReturn(Optional.of(servicoOfertado));

        assertThatThrownBy(() -> service.solicitar(1L, new SolicitacaoRequest(100L, null)))
                .isInstanceOf(RecursoIndisponivelException.class);
    }

    @Test
    void aceitarMudaDeSolicitadoParaAceito() {
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        FatoServico resultado = service.aceitar(1000L, 10L);

        assertThat(resultado.getStatus()).isEqualTo(StatusSolicitacao.ACEITO);
    }

    @Test
    void aceitarRejeitaPrestadorQueNaoEDono() {
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.aceitar(1000L, 999L))
                .isInstanceOf(AcessoNegadoException.class);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.SOLICITADO);
    }

    @Test
    void aceitarRejeitaSeNaoEstiverSolicitado() {
        solicitacao.setStatus(StatusSolicitacao.CANCELADO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.aceitar(1000L, 10L))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void concluirExigePagamentoRetidoEDelegaLiberacao() {
        solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        FatoServico resultado = service.concluir(1000L, 10L);

        verify(pagamentoService).liberar(solicitacao);
        assertThat(resultado.getStatus()).isEqualTo(StatusSolicitacao.CONCLUIDO);
    }

    @Test
    void concluirRejeitaClienteTentandoConcluir() {
        solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.concluir(1000L, 1L))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void avaliarRejeitaSeNaoConcluido() {
        solicitacao.setStatus(StatusSolicitacao.ACEITO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.avaliar(1000L, 1L, new AvaliacaoRequest(5, "ótimo")))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void avaliarRejeitaSegundaAvaliacao() {
        solicitacao.setStatus(StatusSolicitacao.AVALIADO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.avaliar(1000L, 1L, new AvaliacaoRequest(5, null)))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void avaliarConcluidoRecalculaNotaMediaDoPrestador() {
        solicitacao.setStatus(StatusSolicitacao.CONCLUIDO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));
        when(repository.findByPrestador_IdPrestador(10L)).thenReturn(List.of(solicitacao));

        FatoServico resultado = service.avaliar(1000L, 1L, new AvaliacaoRequest(4, "bom"));

        assertThat(resultado.getStatus()).isEqualTo(StatusSolicitacao.AVALIADO);
        assertThat(resultado.getAvaliacao()).isEqualTo(4);
        verify(prestadorRepo).save(prestador);
    }

    @Test
    void cancelarRejeitaSeJaConcluido() {
        solicitacao.setStatus(StatusSolicitacao.CONCLUIDO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.cancelar(1000L, 1L, TipoUsuario.CLIENTE))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void cancelarAceitoComPagamentoRetidoDisparaEstorno() {
        solicitacao.setStatus(StatusSolicitacao.ACEITO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        FatoServico resultado = service.cancelar(1000L, 1L, TipoUsuario.CLIENTE);

        verify(pagamentoService).estornarSeRetido(solicitacao);
        assertThat(resultado.getStatus()).isEqualTo(StatusSolicitacao.CANCELADO);
    }

    @Test
    void cancelarRejeitaQuemNaoParticipaDaSolicitacao() {
        solicitacao.setStatus(StatusSolicitacao.SOLICITADO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.cancelar(1000L, 999L, TipoUsuario.CLIENTE))
                .isInstanceOf(AcessoNegadoException.class);
    }
    // ---------------------------------------------------------------------
    // RN02: codigo de confirmacao e EM_ANDAMENTO
    // ---------------------------------------------------------------------

    @Test
    void aceitarGeraCodigoDeQuatroDigitosERegistraOMomento() {
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        FatoServico aceita = service.aceitar(1000L, 10L);

        assertThat(aceita.getPinConfirmacao()).hasSize(4).containsOnlyDigits();
        assertThat(aceita.getAceitoEm()).isNotNull();
    }

    @Test
    void iniciarComCodigoCorretoLevaAEmAndamento() {
        solicitacao.setStatus(StatusSolicitacao.ACEITO);
        solicitacao.setPinConfirmacao("1234");
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));
        when(pagamentoService.possuiPagamentoRetido(solicitacao)).thenReturn(true);

        FatoServico resultado = service.iniciar(1000L, 10L, new IniciarAtendimentoRequest("1234"));

        assertThat(resultado.getStatus()).isEqualTo(StatusSolicitacao.EM_ANDAMENTO);
        assertThat(resultado.getIniciadoEm()).isNotNull();
    }

    @Test
    void iniciarComCodigoErradoRecusaENaoAlteraOCodigo() {
        solicitacao.setStatus(StatusSolicitacao.ACEITO);
        solicitacao.setPinConfirmacao("1234");
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));
        when(pagamentoService.possuiPagamentoRetido(solicitacao)).thenReturn(true);

        assertThatThrownBy(() -> service.iniciar(1000L, 10L, new IniciarAtendimentoRequest("9999")))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.iniciar(1000L, 10L, new IniciarAtendimentoRequest("0000")))
                .isInstanceOf(AcessoNegadoException.class);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.ACEITO);
        assertThat(solicitacao.getPinConfirmacao()).isEqualTo("1234");

        assertThat(service.iniciar(1000L, 10L, new IniciarAtendimentoRequest("1234")).getStatus())
                .isEqualTo(StatusSolicitacao.EM_ANDAMENTO);
    }

    @Test
    void iniciarSemPagamentoRetidoERecusado() {
        solicitacao.setStatus(StatusSolicitacao.ACEITO);
        solicitacao.setPinConfirmacao("1234");
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));
        when(pagamentoService.possuiPagamentoRetido(solicitacao)).thenReturn(false);

        assertThatThrownBy(() -> service.iniciar(1000L, 10L, new IniciarAtendimentoRequest("1234")))
                .isInstanceOf(EstadoInvalidoException.class);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.ACEITO);
    }

    @Test
    void iniciarAPartirDeEstadoQueNaoPermiteERecusado() {
        solicitacao.setStatus(StatusSolicitacao.SOLICITADO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.iniciar(1000L, 10L, new IniciarAtendimentoRequest("1234")))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void iniciarPorPrestadorQueNaoEODonoERecusado() {
        solicitacao.setStatus(StatusSolicitacao.ACEITO);
        solicitacao.setPinConfirmacao("1234");
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.iniciar(1000L, 999L, new IniciarAtendimentoRequest("1234")))
                .isInstanceOf(AcessoNegadoException.class);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.ACEITO);
    }

    @Test
    void concluirAPartirDeAceitoERecusadoESemCreditarSaldo() {
        solicitacao.setStatus(StatusSolicitacao.ACEITO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> service.concluir(1000L, 10L))
                .isInstanceOf(EstadoInvalidoException.class);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusSolicitacao.ACEITO);
        verify(pagamentoService, never()).liberar(solicitacao);
    }

    @Test
    void concluirRegistraOMomentoDaConclusao() {
        solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThat(service.concluir(1000L, 10L).getConcluidoEm()).isNotNull();
    }

    @Test
    void solicitacaoDuplicadaConsideraEmAndamento() {
        FatoServico emAndamento = new FatoServico();
        emAndamento.setPrestador(prestador);
        emAndamento.setStatus(StatusSolicitacao.EM_ANDAMENTO);

        when(clienteRepo.findById(1L)).thenReturn(Optional.of(cliente));
        when(servicoOfertadoRepo.findById(100L)).thenReturn(Optional.of(servicoOfertado));
        when(repository.findByCliente_IdCliente(1L)).thenReturn(List.of(emAndamento));

        assertThatThrownBy(() -> service.solicitar(1L, new SolicitacaoRequest(100L, null)))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    // ---------------------------------------------------------------------
    // RN03: estorno no cancelamento, com e sem taxa
    // ---------------------------------------------------------------------

    @Test
    void clienteCancelandoDentroDaCarenciaEstornaIntegral() {
        solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));
        when(taxaCancelamentoService.carenciaVencida(solicitacao)).thenReturn(false);

        service.cancelar(1000L, 1L, TipoUsuario.CLIENTE);

        verify(pagamentoService).estornarSeRetido(solicitacao);
        verify(pagamentoService, never()).estornarComTaxa(any(), any());
    }

    @Test
    void clienteCancelandoDepoisDaCarenciaRetemTaxaParaOPrestador() {
        solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));
        when(taxaCancelamentoService.carenciaVencida(solicitacao)).thenReturn(true);
        when(taxaCancelamentoService.calcular(solicitacao)).thenReturn(new BigDecimal("16.00"));

        FatoServico resultado = service.cancelar(1000L, 1L, TipoUsuario.CLIENTE);

        verify(pagamentoService).estornarComTaxa(solicitacao, new BigDecimal("16.00"));
        verify(pagamentoService, never()).estornarSeRetido(solicitacao);
        assertThat(resultado.getStatus()).isEqualTo(StatusSolicitacao.CANCELADO);
    }

    @Test
    void prestadorCancelandoDepoisDaCarenciaEstornaIntegral() {
        solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        service.cancelar(1000L, 10L, TipoUsuario.PRESTADOR);

        verify(pagamentoService).estornarSeRetido(solicitacao);
        verify(pagamentoService, never()).estornarComTaxa(any(), any());
    }

    @Test
    void cancelarDeSolicitadoSemPagamentoNaoEstoura() {
        solicitacao.setStatus(StatusSolicitacao.SOLICITADO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        assertThat(service.cancelar(1000L, 1L, TipoUsuario.CLIENTE).getStatus())
                .isEqualTo(StatusSolicitacao.CANCELADO);
        verify(pagamentoService).estornarSeRetido(solicitacao);
    }

    // ---------------------------------------------------------------------
    // O codigo nao vaza para o prestador
    // ---------------------------------------------------------------------

    @Test
    void codigoDeConfirmacaoVemApenasParaOClienteDono() {
        solicitacao.setStatus(StatusSolicitacao.ACEITO);
        solicitacao.setPinConfirmacao("4321");

        assertThat(service.paraResposta(solicitacao, TipoUsuario.CLIENTE).pinConfirmacao())
                .isEqualTo("4321");
        assertThat(service.paraResposta(solicitacao, TipoUsuario.PRESTADOR).pinConfirmacao())
                .isNull();
    }

    @Test
    void solicitacaoAindaNaoAceitaNaoTemCodigo() {
        solicitacao.setStatus(StatusSolicitacao.SOLICITADO);

        assertThat(service.paraResposta(solicitacao, TipoUsuario.CLIENTE).pinConfirmacao()).isNull();
    }

    @Test
    void respostaTrazOsMomentosJaOcorridosENulosOsDemais() {
        solicitacao.setStatus(StatusSolicitacao.ACEITO);
        solicitacao.setCriadoEm(java.time.LocalDateTime.now().minusHours(3));
        solicitacao.setAceitoEm(java.time.LocalDateTime.now().minusHours(2));

        var resposta = service.paraResposta(solicitacao, TipoUsuario.CLIENTE);

        assertThat(resposta.criadoEm()).isNotNull();
        assertThat(resposta.aceitoEm()).isNotNull();
        assertThat(resposta.iniciadoEm()).isNull();
        assertThat(resposta.concluidoEm()).isNull();
    }
}
