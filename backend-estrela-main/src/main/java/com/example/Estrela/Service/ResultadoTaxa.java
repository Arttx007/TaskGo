package com.example.Estrela.Service;

import java.math.BigDecimal;

/**
 * Resultado do cálculo de RN01 sobre um valor de serviço.
 */
public record ResultadoTaxa(BigDecimal valorTaxa, BigDecimal valorLiquido) {
}
