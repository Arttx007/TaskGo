package com.example.Estrela.DTO;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ParametroNegocioRequest(@NotNull(message = "valor é obrigatório") BigDecimal valor) {
}
