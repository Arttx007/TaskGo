package com.example.Estrela.DTO;

import jakarta.validation.constraints.NotNull;

/**
 * @param servicoOfertadoId item do catálogo sendo solicitado — o id do cliente vem do JWT autenticado,
 *                          nunca deste corpo, para impedir que um cliente solicite em nome de outro.
 */
public record SolicitacaoRequest(@NotNull(message = "servicoOfertadoId é obrigatório") Long servicoOfertadoId) {
}
