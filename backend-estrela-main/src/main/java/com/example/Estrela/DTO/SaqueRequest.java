package com.example.Estrela.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SaqueRequest(
        @NotNull(message = "valor é obrigatório") @DecimalMin(value = "0.01", message = "valor deve ser positivo") BigDecimal valor
) {
}
