package com.example.Estrela.Service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * RN01: taxa fixa abaixo do limiar, percentual a partir dele (inclusive).
 */
@ExtendWith(MockitoExtension.class)
class TaxaServiceTest {

    @Mock
    private ParametroNegocioService parametroNegocioService;

    @InjectMocks
    private TaxaService taxaService;

    private void configurarParametros() {
        lenient().when(parametroNegocioService.valor("taxa.limiar")).thenReturn(new BigDecimal("50.00"));
        lenient().when(parametroNegocioService.valor("taxa.fixa")).thenReturn(new BigDecimal("5.00"));
        lenient().when(parametroNegocioService.valor("taxa.percentual")).thenReturn(new BigDecimal("0.10"));
    }

    @Test
    void deveAplicarTaxaFixaAbaixoDoLimiar() {
        configurarParametros();

        ResultadoTaxa resultado = taxaService.calcular(new BigDecimal("30.00"));

        assertThat(resultado.valorTaxa()).isEqualByComparingTo("5.00");
        assertThat(resultado.valorLiquido()).isEqualByComparingTo("25.00");
    }

    @Test
    void deveAplicarTaxaPercentualNoLimiarExato() {
        // taxa.fixa propositalmente diferente de limiar*percentual (50*0.10=5.00), para o teste
        // realmente distinguir qual dos dois ramos de RN01 foi executado no valor-limite.
        lenient().when(parametroNegocioService.valor("taxa.limiar")).thenReturn(new BigDecimal("50.00"));
        lenient().when(parametroNegocioService.valor("taxa.fixa")).thenReturn(new BigDecimal("8.00"));
        lenient().when(parametroNegocioService.valor("taxa.percentual")).thenReturn(new BigDecimal("0.10"));

        ResultadoTaxa resultado = taxaService.calcular(new BigDecimal("50.00"));

        assertThat(resultado.valorTaxa()).isEqualByComparingTo("5.00");
        assertThat(resultado.valorLiquido()).isEqualByComparingTo("45.00");
    }

    @Test
    void deveAplicarTaxaPercentualAcimaDoLimiar() {
        configurarParametros();

        ResultadoTaxa resultado = taxaService.calcular(new BigDecimal("100.00"));

        assertThat(resultado.valorTaxa()).isEqualByComparingTo("10.00");
        assertThat(resultado.valorLiquido()).isEqualByComparingTo("90.00");
    }
}
