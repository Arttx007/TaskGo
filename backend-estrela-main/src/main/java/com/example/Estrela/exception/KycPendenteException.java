package com.example.Estrela.exception;

/**
 * Lançada quando um prestador tenta uma ação que exige KYC aprovado (RN04) sem tê-lo.
 * Mapeada para HTTP 422.
 */
public class KycPendenteException extends RuntimeException {
    public KycPendenteException(String message) {
        super(message);
    }
}
