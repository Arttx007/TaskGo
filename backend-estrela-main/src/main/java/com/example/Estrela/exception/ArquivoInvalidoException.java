package com.example.Estrela.exception;

/**
 * Lançada quando um arquivo enviado (ex.: documento de KYC, US-01) tem tipo ou tamanho inválido.
 * Mapeada para HTTP 400.
 */
public class ArquivoInvalidoException extends RuntimeException {
    public ArquivoInvalidoException(String message) {
        super(message);
    }
}
