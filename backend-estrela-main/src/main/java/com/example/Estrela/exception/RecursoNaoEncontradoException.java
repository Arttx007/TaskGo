package com.example.Estrela.exception;

/**
 * Lançada quando um recurso solicitado (cliente, prestador, serviço, solicitação, etc.) não existe.
 * Mapeada para HTTP 404 por {@link GlobalExceptionHandler}.
 */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String message) {
        super(message);
    }
}
