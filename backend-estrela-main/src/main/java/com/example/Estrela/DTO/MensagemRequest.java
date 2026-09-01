package com.example.Estrela.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mensagem enviada por uma das partes de uma solicitação.
 *
 * <p>Só texto: anexo exigiria decidir armazenamento, limite e moderação, e é escopo próprio.
 *
 * @param conteudo texto da mensagem, obrigatório e limitado a 2000 caracteres
 */
public record MensagemRequest(
        @NotBlank(message = "conteudo é obrigatório")
        @Size(max = 2000, message = "conteudo deve ter no máximo 2000 caracteres") String conteudo
) {
}
