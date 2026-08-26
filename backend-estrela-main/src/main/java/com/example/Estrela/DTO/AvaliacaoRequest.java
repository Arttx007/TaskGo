package com.example.Estrela.DTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param nota      nota de 1 a 5 (US-09)
 * @param comentario comentário opcional
 */
public record AvaliacaoRequest(
        @NotNull(message = "nota é obrigatória") @Min(value = 1, message = "nota deve ser entre 1 e 5") @Max(value = 5, message = "nota deve ser entre 1 e 5") Integer nota,
        String comentario
) {
}
