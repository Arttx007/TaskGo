package com.example.Estrela.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Dados que o cliente pode alterar no próprio perfil.
 *
 * <p>Senha não entra aqui: alterar senha é outra operação, com confirmação da atual, e
 * misturar as duas faria um formulário de perfil poder trocar credencial por descuido.
 *
 * @param nome     nome completo, obrigatório
 * @param email    e-mail, obrigatório e único entre as contas de cliente
 * @param telefone telefone de contato, opcional
 * @param idade    idade, opcional
 * @param cidade   cidade, opcional
 */
public record AtualizarPerfilClienteRequest(
        @NotBlank(message = "nome é obrigatório") String nome,
        @NotBlank(message = "email é obrigatório") @Email(message = "email inválido") String email,
        @Pattern(regexp = "^$|^\\(?\\d{2}\\)?[\\s-]?\\d{4,5}-?\\d{4}$",
                message = "telefone inválido") String telefone,
        Integer idade,
        String cidade
) {
}
