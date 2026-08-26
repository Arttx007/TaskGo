package com.example.Estrela.DTO;

import com.example.Estrela.Entity.StatusPagamento;

import java.math.BigDecimal;

public record PagamentoResponse(Long id, StatusPagamento status, BigDecimal valorBruto,
                                 BigDecimal valorTaxa, BigDecimal valorLiquido, String metodoPagamento) {
}
