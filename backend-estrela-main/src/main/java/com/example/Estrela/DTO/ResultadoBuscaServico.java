package com.example.Estrela.DTO;

import java.util.List;

/**
 * @param resultados lista de serviços encontrados, ordenada por proximidade quando lat/lon foram informados
 * @param mensagem   preenchido apenas quando {@code resultados} está vazio, explicando o motivo (US-03, caso extremo)
 */
public record ResultadoBuscaServico(List<BuscaServicoResponse> resultados, String mensagem) {
}
