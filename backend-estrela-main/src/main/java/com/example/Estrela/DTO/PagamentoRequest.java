package com.example.Estrela.DTO;

/**
 * @param metodoPagamento identificador do método de pagamento (mock — sem PSP real integrado)
 * @param simularFalha    quando {@code true}, força o {@code PagamentoGatewayMock} a recusar a cobrança (testes)
 */
public record PagamentoRequest(String metodoPagamento, boolean simularFalha) {
}
