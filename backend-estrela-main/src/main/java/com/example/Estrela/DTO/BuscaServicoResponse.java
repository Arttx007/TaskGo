package com.example.Estrela.DTO;

import java.math.BigDecimal;

/**
 * Item de resultado da busca por geolocalização (US-03).
 *
 * @param distanciaKm distância até o ponto de busca — nulo quando a busca caiu no fallback por cidade (sem lat/lon)
 */
public record BuscaServicoResponse(Long servicoOfertadoId, String categoria, String descricao, BigDecimal preco,
                                    Long prestadorId, String prestadorNome, BigDecimal notaMediaPrestador,
                                    Double distanciaKm) {
}
