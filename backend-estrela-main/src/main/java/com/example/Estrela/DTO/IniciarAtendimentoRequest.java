package com.example.Estrela.DTO;

import jakarta.validation.constraints.NotBlank;

/**
 * Código de confirmação que o prestador informa, no local, para iniciar o atendimento (RN02).
 *
 * <p>O cliente lê o código na própria tela e o passa ao prestador presencialmente. É assim que
 * ele confirma que quem chegou é quem foi contratado.
 *
 * @param pin código de quatro dígitos entregue ao cliente no aceite
 */
public record IniciarAtendimentoRequest(
        @NotBlank(message = "pin é obrigatório") String pin
) {
}
