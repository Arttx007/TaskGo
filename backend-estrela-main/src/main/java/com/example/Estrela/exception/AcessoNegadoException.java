package com.example.Estrela.exception;

/**
 * Lançada quando o usuário autenticado não é o dono do recurso sobre o qual tenta agir
 * (ex.: prestador tentando aceitar solicitação de outro prestador). Mapeada para HTTP 403.
 */
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String message) {
        super(message);
    }
}
