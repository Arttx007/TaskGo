package com.example.Estrela.Service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Implementação simulada de {@link PagamentoGateway} — sempre aprova, exceto quando o chamador
 * pede explicitamente para simular falha. Não integra nenhum PSP real; troque este bean quando
 * a integração real for definida.
 */
@Service
public class PagamentoGatewayMock implements PagamentoGateway {

    @Override
    public ResultadoCobranca cobrar(BigDecimal valor, String metodo, boolean simularFalha) {
        if (simularFalha) {
            return new ResultadoCobranca(false, "Pagamento recusado pela operadora (simulado)");
        }
        return new ResultadoCobranca(true, null);
    }
}
