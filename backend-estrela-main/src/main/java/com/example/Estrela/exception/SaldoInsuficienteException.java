package com.example.Estrela.exception;

/**
 * Lançada quando um saque (US-08) é solicitado com valor maior que o saldo disponível do prestador.
 * Mapeada para HTTP 422.
 */
public class SaldoInsuficienteException extends RuntimeException {
    public SaldoInsuficienteException(String message) {
        super(message);
    }
}
