package com.example.Estrela.exception;

/**
 * Recusa uma requisição cujos parâmetros são individualmente válidos mas inconsistentes entre si —
 * o tipo de regra que a validação declarativa de {@code @Valid} não alcança, porque ela olha um
 * campo por vez.
 *
 * <p>Existe porque {@code MethodArgumentNotValidException} só cobre corpo de requisição anotado com
 * {@code @Valid}: uma combinação inválida de parâmetros de consulta não tem como produzir o 400
 * {@code VALIDACAO} sem uma exceção de domínio própria. Lance esta em vez de montar um
 * {@code ResponseEntity} de erro à mão — quem traduz para HTTP é o {@link GlobalExceptionHandler}.
 *
 * <p>Mapeada para <strong>HTTP 400</strong> com código estável {@code VALIDACAO}.
 */
public class ValidacaoException extends RuntimeException {

    /**
     * @param mensagem explicação legível de por que a combinação foi recusada, exibida ao cliente
     */
    public ValidacaoException(String mensagem) {
        super(mensagem);
    }
}
