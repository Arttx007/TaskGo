package com.example.Estrela.DTO;

import java.math.BigDecimal;

/**
 * Item de resultado da busca por geolocalização (US-03).
 *
 * @param distanciaKm distância até o ponto de busca — nulo quando a busca caiu no fallback por cidade (sem lat/lon)
 * @param latitude    posição aproximada do serviço, para situá-lo em um mapa; nulo quando a localização
 *                    não tem coordenadas cadastradas
 * @param longitude   posição aproximada do serviço; nulo nas mesmas condições de {@code latitude}
 */
public record BuscaServicoResponse(Long servicoOfertadoId, String categoria, String descricao, BigDecimal preco,
                                    Long prestadorId, String prestadorNome, BigDecimal notaMediaPrestador,
                                    Double distanciaKm, Double latitude, Double longitude) {
}
