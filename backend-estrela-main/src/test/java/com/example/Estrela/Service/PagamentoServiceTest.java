package com.example.Estrela.Service;

import com.example.Estrela.DTO.PagamentoExtratoResponse;
import com.example.Estrela.DTO.PagamentoRequest;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.ServicoOfertado;
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
import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void extratoDevolveATaxaPersistidaEnaoARecalculada() {
        // O pagamento foi feito quando a taxa apurada era 10,00. Se o extrato recalculasse,
        // uma mudança posterior em parametro_negocio reescreveria a história do lançamento.
        Pagamento pagamento = pagamentoComValores(new BigDecimal("100.00"), new BigDecimal("10.00"));
        when(pagamentoRepository.listarPorCliente(7L)).thenReturn(List.of(pagamento));

        // taxaService passa a devolver outra taxa; o extrato não deve consultá-lo.
        List<PagamentoExtratoResponse> extrato = pagamentoService.listarExtratoDoCliente(7L);

        assertThat(extrato).hasSize(1);
        assertThat(extrato.get(0).valorTaxa()).isEqualByComparingTo("10.00");
        assertThat(extrato.get(0).valorBruto()).isEqualByComparingTo("100.00");
        verify(taxaService, never()).calcular(any());
    }

    @Test
    void extratoTrazCategoriaPrestadorESituacaoDeCustodia() {
        Pagamento pagamento = pagamentoComValores(new BigDecimal("120.00"), new BigDecimal("12.00"));
        when(pagamentoRepository.listarPorCliente(7L)).thenReturn(List.of(pagamento));

        PagamentoExtratoResponse item = pagamentoService.listarExtratoDoCliente(7L).get(0);

        assertThat(item.categoria()).isEqualTo("Eletricista");
        assertThat(item.prestadorNome()).isEqualTo("Prestador Teste");
        assertThat(item.status()).isEqualTo(StatusPagamento.RETIDO);
        assertThat(item.metodoPagamento()).isEqualTo("cartao_mock");
    }

    @Test
    void extratoDeContaSemPagamentoVemVazio() {
        when(pagamentoRepository.listarPorCliente(7L)).thenReturn(List.of());

        assertThat(pagamentoService.listarExtratoDoCliente(7L)).isEmpty();
    }

    @Test
    void extratoDeSolicitacaoSemServicoOfertadoNaoEstoura() {
        Pagamento pagamento = pagamentoComValores(new BigDecimal("50.00"), new BigDecimal("5.00"));
        pagamento.getFatoServico().setServicoOfertado(null);
        when(pagamentoRepository.listarPorCliente(7L)).thenReturn(List.of(pagamento));

        assertThat(pagamentoService.listarExtratoDoCliente(7L).get(0).categoria()).isNull();
    }

    private Pagamento pagamentoComValores(BigDecimal bruto, BigDecimal taxa) {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(7L);

        prestador.setNome("Prestador Teste");

        ServicoOfertado ofertado = new ServicoOfertado();
        ofertado.setCategoria("Eletricista");

        FatoServico fato = new FatoServico();
        fato.setId_servico(99L);
        fato.setCliente(cliente);
        fato.setPrestador(prestador);
        fato.setServicoOfertado(ofertado);
        fato.setValor(bruto);

        Pagamento pagamento = new Pagamento();
        pagamento.setFatoServico(fato);
        pagamento.setValorBruto(bruto);
        pagamento.setValorTaxa(taxa);
        pagamento.setValorLiquido(bruto.subtract(taxa));
        pagamento.setStatus(StatusPagamento.RETIDO);
        pagamento.setMetodoPagamento("cartao_mock");
        pagamento.setCriadoEm(LocalDateTime.now());
        return pagamento;
    }
    // ---------------------------------------------------------------------
    // RN03: estorno parcial com taxa de cancelamento creditada ao prestador
    // ---------------------------------------------------------------------

    @Test
    void estornoParcialCreditaATaxaNoPrestadorEGuardaOsDoisValores() {
        Pagamento pagamento = retido(new BigDecimal("120.00"), new BigDecimal("12.00"));
        when(pagamentoRepository.findByFatoServico(servico)).thenReturn(Optional.of(pagamento));

        pagamentoService.estornarComTaxa(servico, new BigDecimal("18.00"));

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.ESTORNADO_PARCIAL);
        assertThat(pagamento.getValorTaxaCancelamento()).isEqualByComparingTo("18.00");
        assertThat(pagamento.getValorEstornado()).isEqualByComparingTo("102.00");
        assertThat(prestador.getSaldoDisponivel()).isEqualByComparingTo("18.00");
    }

    @Test
    void devolvidoMaisRetidoSomaExatamenteOValorPago() {
        Pagamento pagamento = retido(new BigDecimal("120.00"), new BigDecimal("12.00"));
        when(pagamentoRepository.findByFatoServico(servico)).thenReturn(Optional.of(pagamento));

        pagamentoService.estornarComTaxa(servico, new BigDecimal("18.00"));

        assertThat(pagamento.getValorEstornado().add(pagamento.getValorTaxaCancelamento()))
                .isEqualByComparingTo(pagamento.getValorBruto());
    }

    @Test
    void taxaAcimaDoValorPagoELimitadaAoValorPago() {
        Pagamento pagamento = retido(new BigDecimal("20.00"), new BigDecimal("5.00"));
        when(pagamentoRepository.findByFatoServico(servico)).thenReturn(Optional.of(pagamento));

        pagamentoService.estornarComTaxa(servico, new BigDecimal("50.00"));

        assertThat(pagamento.getValorTaxaCancelamento()).isEqualByComparingTo("20.00");
        assertThat(pagamento.getValorEstornado()).isEqualByComparingTo("0.00");
        assertThat(prestador.getSaldoDisponivel()).isEqualByComparingTo("20.00");
    }

    @Test
    void taxaZeroCaiNoEstornoIntegral() {
        Pagamento pagamento = retido(new BigDecimal("120.00"), new BigDecimal("12.00"));
        when(pagamentoRepository.findByFatoServico(servico)).thenReturn(Optional.of(pagamento));

        pagamentoService.estornarComTaxa(servico, BigDecimal.ZERO);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(pagamento.getValorEstornado()).isEqualByComparingTo("120.00");
        assertThat(prestador.getSaldoDisponivel()).isEqualByComparingTo("0.00");
    }

    @Test
    void estornoIntegralRegistraOValorDevolvido() {
        Pagamento pagamento = retido(new BigDecimal("120.00"), new BigDecimal("12.00"));
        when(pagamentoRepository.findByFatoServico(servico)).thenReturn(Optional.of(pagamento));

        pagamentoService.estornarSeRetido(servico);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(pagamento.getValorEstornado()).isEqualByComparingTo("120.00");
        assertThat(pagamento.getValorTaxaCancelamento()).isNull();
    }

    @Test
    void estornoParcialSemPagamentoRetidoNaoFazNada() {
        when(pagamentoRepository.findByFatoServico(servico)).thenReturn(Optional.empty());

        pagamentoService.estornarComTaxa(servico, new BigDecimal("18.00"));

        assertThat(prestador.getSaldoDisponivel()).isEqualByComparingTo("0.00");
    }

    @Test
    void estornoParcialNaoAlcancaPagamentoJaLiberado() {
        Pagamento pagamento = retido(new BigDecimal("120.00"), new BigDecimal("12.00"));
        pagamento.setStatus(StatusPagamento.LIBERADO);
        when(pagamentoRepository.findByFatoServico(servico)).thenReturn(Optional.of(pagamento));

        pagamentoService.estornarComTaxa(servico, new BigDecimal("18.00"));

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.LIBERADO);
        assertThat(prestador.getSaldoDisponivel()).isEqualByComparingTo("0.00");
    }

    private Pagamento retido(BigDecimal bruto, BigDecimal taxa) {
        Pagamento pagamento = new Pagamento();
        pagamento.setFatoServico(servico);
        pagamento.setValorBruto(bruto);
        pagamento.setValorTaxa(taxa);
        pagamento.setValorLiquido(bruto.subtract(taxa));
        pagamento.setStatus(StatusPagamento.RETIDO);
        pagamento.setCriadoEm(LocalDateTime.now());
        return pagamento;
    }
}
