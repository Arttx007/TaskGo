package com.example.Estrela.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param nome          nome do prestador
 * @param especialidade especialidade/categoria principal
 * @param cidade        cidade de atuação
 * @param email         e-mail, usado no login
 * @param senha         senha em texto puro (hasheada antes de persistir)
 */
public record CadastroPrestadorRequest(
        @NotBlank(message = "nome é obrigatório") String nome,
        String especialidade,
        String cidade,
        @NotBlank(message = "email é obrigatório") @Email(message = "email inválido") String email,
        @NotBlank(message = "senha é obrigatória") @Size(min = 8, message = "senha deve ter ao menos 8 caracteres") String senha
) {
}
