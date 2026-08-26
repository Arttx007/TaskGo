package com.example.Estrela.DTO;

import com.example.Estrela.Entity.StatusSaque;

import java.math.BigDecimal;

public record SaqueResponse(Long id, BigDecimal valor, StatusSaque status, BigDecimal saldoRestante) {
}
