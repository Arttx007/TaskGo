package com.example.Estrela.DTO;

import jakarta.validation.constraints.NotNull;

/**
 * Pedido para marcar um prestador como favorito.
 *
 * @param prestadorId prestador a favoritar
 */
public record FavoritoRequest(@NotNull(message = "prestadorId é obrigatório") Long prestadorId) {
}
