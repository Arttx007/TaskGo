package com.example.Estrela.DTO;

import jakarta.validation.constraints.NotNull;

/**
 * @param servicoOfertadoId  item do catálogo sendo solicitado — o id do cliente vem do JWT autenticado,
 *                           nunca deste corpo, para impedir que um cliente solicite em nome de outro.
 * @param enderecoClienteId  endereço de atendimento, <b>opcional</b>. Aditivo por decisão: exigi-lo
 *                           quebraria a solicitação feita da página pública de busca, onde o cliente
 *                           não tem endereços carregados. Sem ele, o acompanhamento se apresenta sem
 *                           local em vez de presumir um.
 */
public record SolicitacaoRequest(@NotNull(message = "servicoOfertadoId é obrigatório") Long servicoOfertadoId,
                                  Long enderecoClienteId) {
}
