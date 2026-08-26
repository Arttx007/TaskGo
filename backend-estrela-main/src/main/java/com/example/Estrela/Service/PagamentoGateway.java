package com.example.Estrela.Service;

import java.math.BigDecimal;

/**
 * Abstração sobre o provedor de pagamento (PSP) real, ainda não escolhido no MVP (fora de escopo
 * do PRD — ver spec.md, seção de riscos). Troque a implementação {@link PagamentoGatewayMock} por
 * um cliente de PSP real quando essa decisão de produto for tomada.
 */
public interface PagamentoGateway {

    /**
     * @param valor        valor a cobrar do cliente
     * @param metodo       identificador do método de pagamento
     * @param simularFalha quando {@code true}, força uma recusa (usado em testes/demo)
     * @return resultado da cobrança
     */
    ResultadoCobranca cobrar(BigDecimal valor, String metodo, boolean simularFalha);

    record ResultadoCobranca(boolean aprovado, String motivoRecusa) {
    }
}
