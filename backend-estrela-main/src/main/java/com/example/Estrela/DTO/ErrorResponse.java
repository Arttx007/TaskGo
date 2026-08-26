package com.example.Estrela.DTO;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Forma consistente de erro devolvida por {@link com.example.Estrela.exception.GlobalExceptionHandler}
 * para toda exceção mapeada.
 *
 * @param timestamp   momento em que o erro ocorreu
 * @param status      código HTTP
 * @param error       identificador curto do tipo de erro (ex.: "ESTADO_INVALIDO")
 * @param message     mensagem legível descrevendo o erro
 * @param path        caminho da requisição que gerou o erro
 * @param fieldErrors erros de validação por campo, quando aplicável (ex.: `@Valid`); vazio caso contrário
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
