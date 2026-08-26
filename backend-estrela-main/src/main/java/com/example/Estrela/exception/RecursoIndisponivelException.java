package com.example.Estrela.exception;

/**
 * Lançada quando um recurso existe mas não está disponível para a ação pedida (ex.: serviço
 * desativado, prestador sem KYC aprovado ao tentar solicitar — US-04). Mapeada para HTTP 422.
 */
public class RecursoIndisponivelException extends RuntimeException {
    public RecursoIndisponivelException(String message) {
        super(message);
    }
}
