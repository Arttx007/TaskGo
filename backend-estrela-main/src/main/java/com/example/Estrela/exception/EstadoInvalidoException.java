package com.example.Estrela.exception;

/**
 * Lançada quando uma transição de estado (RN02) ou ação é inválida no estado atual do recurso
 * (ex.: concluir sem pagamento confirmado, avaliar duas vezes, cancelar já concluído). Mapeada para HTTP 409.
 */
public class EstadoInvalidoException extends RuntimeException {
    public EstadoInvalidoException(String message) {
        super(message);
    }
}
