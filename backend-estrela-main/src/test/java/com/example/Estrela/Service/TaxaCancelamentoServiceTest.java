package com.example.Estrela.Service;

import com.example.Estrela.Entity.EnderecoCliente;
import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.Localizacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * RN03: taxa de cancelamento retida para o prestador quando o cliente desiste de um
 * atendimento já iniciado.
 *
 * <p>Os valores exercitados aqui são os semeados em {@code V8}: carência de 2 minutos, 15%
 * dentro de 10 km, 20% acima, teto de R$ 50,00.
 */
@ExtendWith(MockitoExtension.class)
class TaxaCancelamentoServiceTest {

    @Mock private ParametroNegocioService parametroNegocioService;
    @Mock private GeoService geoService;

    @InjectMocks
    private TaxaCancelamentoService taxaCancelamentoService;

    @BeforeEach
    void setUp() {
        lenient().when(parametroNegocioService.valor("cancelamento.carencia-minutos"))
                .thenReturn(new BigDecimal("2"));
        lenient().when(parametroNegocioService.valor("cancelamento.taxa-percentual-perto"))
                .thenReturn(new BigDecimal("0.15"));
        lenient().when(parametroNegocioService.valor("cancelamento.taxa-percentual-longe"))
                .thenReturn(new BigDecimal("0.20"));
        lenient().when(parametroNegocioService.valor("cancelamento.limiar-distancia-km"))
                .thenReturn(new BigDecimal("10"));
        lenient().when(parametroNegocioService.valor("cancelamento.taxa-teto"))
                .thenReturn(new BigDecimal("50.00"));
    }

    @Test
    void servicoDe120PertoRetem18() {
        FatoServico servico = comDistancia(new BigDecimal("120.00"), 3.0);

        assertThat(taxaCancelamentoService.calcular(servico)).isEqualByComparingTo("18.00");
    }

    @Test
    void servicoDe120LongeRetem24() {
        FatoServico servico = comDistancia(new BigDecimal("120.00"), 25.0);

        assertThat(taxaCancelamentoService.calcular(servico)).isEqualByComparingTo("24.00");
    }

    @Test
    void servicoDe500LongeBateNoTetoDe50() {
        FatoServico servico = comDistancia(new BigDecimal("500.00"), 25.0);

        assertThat(taxaCancelamentoService.calcular(servico)).isEqualByComparingTo("50.00");
    }

    @Test
    void servicoDe5000LongeTambemBateNoTeto() {
        FatoServico servico = comDistancia(new BigDecimal("5000.00"), 25.0);

        assertThat(taxaCancelamentoService.calcular(servico)).isEqualByComparingTo("50.00");
    }

    @Test
    void distanciaExatamenteNoLimiarUsaOPercentualMenor() {
        FatoServico servico = comDistancia(new BigDecimal("200.00"), 10.0);

        assertThat(taxaCancelamentoService.calcular(servico)).isEqualByComparingTo("30.00");
    }

    @Test
    void solicitacaoSemEnderecoUsaOPercentualMenor() {
        FatoServico servico = new FatoServico();
        servico.setValor(new BigDecimal("120.00"));
        servico.setLocalizacao(localizacaoCom(-8.05, -34.9));

        assertThat(taxaCancelamentoService.calcular(servico)).isEqualByComparingTo("18.00");
    }

    @Test
    void enderecoSemCoordenadasUsaOPercentualMenor() {
        FatoServico servico = new FatoServico();
        servico.setValor(new BigDecimal("120.00"));
        servico.setEnderecoCliente(new EnderecoCliente());
        servico.setLocalizacao(localizacaoCom(-8.05, -34.9));

        assertThat(taxaCancelamentoService.calcular(servico)).isEqualByComparingTo("18.00");
    }

    @Test
    void localizacaoDoPrestadorSemCoordenadasUsaOPercentualMenor() {
        FatoServico servico = new FatoServico();
        servico.setValor(new BigDecimal("120.00"));
        EnderecoCliente endereco = new EnderecoCliente();
        endereco.setLatitude(-8.0);
        endereco.setLongitude(-34.9);
        servico.setEnderecoCliente(endereco);
        servico.setLocalizacao(new Localizacao());

        assertThat(taxaCancelamentoService.calcular(servico)).isEqualByComparingTo("18.00");
    }

    @Test
    void taxaNuncaExcedeOValorPago() {
        when(parametroNegocioService.valor("cancelamento.taxa-teto")).thenReturn(new BigDecimal("50.00"));
        FatoServico servico = comDistancia(new BigDecimal("20.00"), 25.0);

        BigDecimal taxa = taxaCancelamentoService.calcular(servico);

        assertThat(taxa).isEqualByComparingTo("4.00");
        assertThat(taxa).isLessThanOrEqualTo(new BigDecimal("20.00"));
    }

    @Test
    void tetoAcimaDoValorDoServicoLimitaAoValorDoServico() {
        when(parametroNegocioService.valor("cancelamento.taxa-teto")).thenReturn(new BigDecimal("500.00"));
        when(parametroNegocioService.valor("cancelamento.taxa-percentual-longe"))
                .thenReturn(new BigDecimal("2.00")); // 200%, cenário absurdo de configuração
        FatoServico servico = comDistancia(new BigDecimal("30.00"), 25.0);

        assertThat(taxaCancelamentoService.calcular(servico)).isEqualByComparingTo("30.00");
    }

    @Test
    void servicoSemValorNaoGeraTaxa() {
        FatoServico servico = new FatoServico();

        assertThat(taxaCancelamentoService.calcular(servico)).isEqualByComparingTo("0.00");
    }

    @Test
    void carenciaNaoVencidaNaoRetemNada() {
        FatoServico servico = comDistancia(new BigDecimal("120.00"), 3.0);
        servico.setIniciadoEm(LocalDateTime.now().minusSeconds(30));

        assertThat(taxaCancelamentoService.carenciaVencida(servico)).isFalse();
        assertThat(taxaCancelamentoService.retencaoPrevista(servico)).isEqualByComparingTo("0.00");
    }

    @Test
    void carenciaVencidaRetemAPrevisao() {
        FatoServico servico = comDistancia(new BigDecimal("120.00"), 3.0);
        servico.setIniciadoEm(LocalDateTime.now().minusMinutes(5));

        assertThat(taxaCancelamentoService.carenciaVencida(servico)).isTrue();
        assertThat(taxaCancelamentoService.retencaoPrevista(servico)).isEqualByComparingTo("18.00");
    }

    @Test
    void atendimentoNuncaIniciadoNaoTemCarenciaAVencer() {
        FatoServico servico = comDistancia(new BigDecimal("120.00"), 3.0);

        assertThat(taxaCancelamentoService.carenciaVencida(servico)).isFalse();
        assertThat(taxaCancelamentoService.retencaoPrevista(servico)).isEqualByComparingTo("0.00");
    }

    private FatoServico comDistancia(BigDecimal valor, double distanciaKm) {
        FatoServico servico = new FatoServico();
        servico.setValor(valor);

        EnderecoCliente endereco = new EnderecoCliente();
        endereco.setLatitude(-8.00);
        endereco.setLongitude(-34.90);
        servico.setEnderecoCliente(endereco);
        servico.setLocalizacao(localizacaoCom(-8.05, -34.95));

        lenient().when(geoService.distanciaKm(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(distanciaKm);
        return servico;
    }

    private Localizacao localizacaoCom(Double lat, Double lon) {
        Localizacao localizacao = new Localizacao();
        localizacao.setLatitude(lat);
        localizacao.setLongitude(lon);
        return localizacao;
    }
}
