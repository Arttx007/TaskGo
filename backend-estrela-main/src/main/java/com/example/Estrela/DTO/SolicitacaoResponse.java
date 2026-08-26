package com.example.Estrela.DTO;

import com.example.Estrela.Entity.StatusPagamento;
import com.example.Estrela.Entity.StatusSolicitacao;

import java.math.BigDecimal;

/**
 * @param statusPagamento situação do pagamento desta solicitação, ou {@code null} se ainda não houve cobrança
 */
public record SolicitacaoResponse(Long id, StatusSolicitacao status, Long clienteId, String clienteNome,
                                   Long prestadorId, String prestadorNome, Long servicoOfertadoId,
                                   String categoria, BigDecimal valor, Integer avaliacao, String comentarioAvaliacao,
                                   StatusPagamento statusPagamento) {
}
