package com.example.Estrela.exception;

/**
 * Lançada quando a cobrança de um pagamento (US-06) é recusada pelo gateway. Mapeada para HTTP 402.
 */
public class PagamentoRecusadoException extends RuntimeException {
    public PagamentoRecusadoException(String message) {
        super(message);
    }
}
