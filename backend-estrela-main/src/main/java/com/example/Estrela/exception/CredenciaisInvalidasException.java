package com.example.Estrela.exception;

/**
 * Lançada quando o login falha por e-mail não encontrado ou senha incorreta. Mapeada para HTTP 401
 * (contrato de {@code POST /auth/login}).
 */
public class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException(String message) {
        super(message);
    }
}
