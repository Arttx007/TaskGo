package com.example.Estrela.Service;

import com.example.Estrela.DTO.PagamentoRequest;
import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.Pagamento;
import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.StatusPagamento;
import com.example.Estrela.exception.EstadoInvalidoException;
import com.example.Estrela.exception.PagamentoRecusadoException;
import com.example.Estrela.repository.PagamentoRepository;
import com.example.Estrela.repository.PrestadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RN03: custódia de pagamento — cobrança (US-06), liberação na conclusão (US-07).
 */
@ExtendWith(MockitoExtension.class)
class PagamentoServiceTest {

    @Mock private PagamentoRepository pagamentoRepository;
    @Mock private PrestadorRepository prestadorRepository;
    @Mock private TaxaService taxaService;
    @Mock private PagamentoGateway pagamentoGateway;

    @InjectMocks
    private PagamentoService pagamentoService;

    private FatoServico servico;
    private Prestador prestador;

    @BeforeEach
    void setUp() {
        prestador = new Prestador();
        prestador.setIdPrestador(10L);
        prestador.setSaldoDisponivel(BigDecimal.ZERO);

        servico = new FatoServico();
        servico.setPrestador(prestador);
        servico.setValor(new BigDecimal("100.00"));

        lenient().when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void pagarComSucessoRetemValorEmCustodia() {
        when(taxaService.calcular(new BigDecimal("100.00")))
                .thenReturn(new ResultadoTaxa(new BigDecimal("10.00"), new BigDecimal("90.00")));
        when(pagamentoGateway.cobrar(any(), any(), eq(false)))
                .thenReturn(new PagamentoGateway.ResultadoCobranca(true, null));

        Pagamento pagamento = pagamentoService.pagar(servico, new PagamentoRequest("cartao", false));

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.RETIDO);
        assertThat(pagamento.getValorTaxa()).isEqualByComparingTo("10.00");
        assertThat(pagamento.getValorLiquido()).isEqualByComparingTo("90.00");
    }

    @Test
    void pagarComFalhaNaoPersisteNadaEmCustodia() {
        when(taxaService.calcular(any())).thenReturn(new ResultadoTaxa(BigDecimal.TEN, new BigDecimal("90.00")));
        when(pagamentoGateway.cobrar(any(), any(), eq(true)))
                .thenReturn(new PagamentoGateway.ResultadoCobranca(false, "recusado"));

        assertThatThrownBy(() -> pagamentoService.pagar(servico, new PagamentoRequest("cartao", true)))
                .isInstanceOf(PagamentoRecusadoException.class);

        verify(pagamentoRepository, never()).save(any());
    }

    @Test
    void liberarCreditaSaldoLiquidoAoPrestador() {
        Pagamento pagamento = new Pagamento();
        pagamento.setFatoServico(servico);
        pagamento.setStatus(StatusPagamento.RETIDO);
        pagamento.setValorLiquido(new BigDecimal("90.00"));

        when(pagamentoRepository.findByFatoServico(servico)).thenReturn(Optional.of(pagamento));

        pagamentoService.liberar(servico);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.LIBERADO);
        assertThat(prestador.getSaldoDisponivel()).isEqualByComparingTo("90.00");
        verify(prestadorRepository).save(prestador);
    }

    @Test
    void liberarRejeitaSeNaoHouverPagamentoRetido() {
        when(pagamentoRepository.findByFatoServico(servico)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pagamentoService.liberar(servico))
                .isInstanceOf(EstadoInvalidoException.class);

        verify(prestadorRepository, never()).save(any());
    }

    @Test
    void estornarSeRetidoMarcaComoEstornadoSemDebitarPrestador() {
        Pagamento pagamento = new Pagamento();
        pagamento.setFatoServico(servico);
        pagamento.setStatus(StatusPagamento.RETIDO);
        pagamento.setValorLiquido(new BigDecimal("90.00"));

        when(pagamentoRepository.findByFatoServico(servico)).thenReturn(Optional.of(pagamento));

        pagamentoService.estornarSeRetido(servico);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        verify(prestadorRepository, never()).save(any());
    }
}
