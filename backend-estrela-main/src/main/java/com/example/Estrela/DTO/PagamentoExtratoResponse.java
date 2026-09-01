package com.example.Estrela.DTO;

import com.example.Estrela.Entity.StatusPagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lançamento do extrato de pagamentos de um cliente.
 *
 * <p>Todos os valores são os <b>persistidos no momento do pagamento</b>, nunca
 * recalculados: a taxa de serviço vem de parâmetros ajustáveis sem deploy (RN01), então
 * recalcular mostraria a taxa de hoje num pagamento antigo.
 *
 * <p>{@code valorEstornado} e {@code valorTaxaCancelamento} vêm preenchidos apenas quando
 * houve cancelamento — o segundo somente no estorno parcial, em que a taxa foi creditada
 * ao prestador (RN03). Num pagamento vigente ou liberado ambos são {@code null}.
 *
 * @param solicitacaoId        solicitação a que o pagamento se refere
 * @param categoria            categoria do serviço pago
 * @param prestadorNome        nome do prestador que atendeu
 * @param valorBruto           valor pago pelo cliente
 * @param valorTaxa            taxa de serviço da plataforma apurada no pagamento (RN01)
 * @param status               situação de custódia registrada
 * @param metodoPagamento      meio informado no pagamento
 * @param criadoEm             momento do pagamento
 * @param valorEstornado       valor devolvido ao cliente, ou {@code null} se não houve estorno
 * @param valorTaxaCancelamento taxa retida para o prestador, ou {@code null} fora do estorno parcial
 */
public record PagamentoExtratoResponse(Long solicitacaoId,
                                       String categoria,
                                       String prestadorNome,
                                       BigDecimal valorBruto,
                                       BigDecimal valorTaxa,
                                       StatusPagamento status,
                                       String metodoPagamento,
                                       LocalDateTime criadoEm,
                                       BigDecimal valorEstornado,
                                       BigDecimal valorTaxaCancelamento) {
}
