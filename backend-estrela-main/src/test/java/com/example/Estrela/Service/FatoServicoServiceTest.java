package com.example.Estrela.Service;

import com.example.Estrela.DTO.AvaliacaoRequest;
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

        FatoServico criado = service.solicitar(1L, new SolicitacaoRequest(100L));

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

        assertThatThrownBy(() -> service.solicitar(1L, new SolicitacaoRequest(100L)))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void solicitarRejeitaServicoInativo() {
        servicoOfertado.setStatus(StatusServico.INATIVO);
        when(clienteRepo.findById(1L)).thenReturn(Optional.of(cliente));
        when(servicoOfertadoRepo.findById(100L)).thenReturn(Optional.of(servicoOfertado));

        assertThatThrownBy(() -> service.solicitar(1L, new SolicitacaoRequest(100L)))
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
        solicitacao.setStatus(StatusSolicitacao.ACEITO);
        when(repository.findById(1000L)).thenReturn(Optional.of(solicitacao));

        FatoServico resultado = service.concluir(1000L, 10L);

        verify(pagamentoService).liberar(solicitacao);
        assertThat(resultado.getStatus()).isEqualTo(StatusSolicitacao.CONCLUIDO);
    }

    @Test
    void concluirRejeitaClienteTentandoConcluir() {
        solicitacao.setStatus(StatusSolicitacao.ACEITO);
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
}
