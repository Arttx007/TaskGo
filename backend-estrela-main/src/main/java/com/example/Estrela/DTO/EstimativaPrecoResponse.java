package com.example.Estrela.DTO;

import java.math.BigDecimal;

/**
 * Faixa de preço realmente praticada numa categoria, apurada sobre os serviços publicados.
 *
 * <p>Não é predição nem estimativa gerada: são os preços que os prestadores cobram hoje, filtrados
 * por serviço `ATIVO` de prestador `APROVADO` (RN04).
 *
 * <p>Quando a amostra tem menos de três serviços, {@code minimo}, {@code mediana} e {@code maximo}
 * vêm nulos e apenas {@code mensagem} é preenchida — com um ou dois serviços a faixa revelaria o
 * preço de um prestador identificável.
 *
 * @param categoria categoria consultada
 * @param minimo    menor preço da amostra, ou nulo quando a amostra é insuficiente
 * @param mediana   preço mediano da amostra, ou nulo quando a amostra é insuficiente
 * @param maximo    maior preço da amostra, ou nulo quando a amostra é insuficiente
 * @param amostra   quantidade de serviços considerados
 * @param mensagem  preenchida apenas quando não há faixa a devolver, explicando o motivo
 */
public record EstimativaPrecoResponse(String categoria, BigDecimal minimo, BigDecimal mediana, BigDecimal maximo,
                                       int amostra, String mensagem) {
}
