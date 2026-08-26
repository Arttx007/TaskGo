package com.example.Estrela.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param nome        nome do cliente
 * @param idade       idade do cliente
 * @param cidade      cidade do cliente
 * @param tipoCliente segmentação livre do cliente (ex.: "residencial", "empresarial")
 * @param email       e-mail, usado no login
 * @param senha       senha em texto puro (hasheada antes de persistir)
 */
public record CadastroClienteRequest(
        @NotBlank(message = "nome é obrigatório") String nome,
        Integer idade,
        String cidade,
        String tipoCliente,
        @NotBlank(message = "email é obrigatório") @Email(message = "email inválido") String email,
        @NotBlank(message = "senha é obrigatória") @Size(min = 8, message = "senha deve ter ao menos 8 caracteres") String senha
) {
}
