package com.example.Estrela.DTO;

/**
 * Dados públicos de um cliente — nunca inclui a senha.
 */
public record ClienteResponse(Long idCliente, String nome, Integer idade, String cidade,
                               String tipoCliente, String email) {
}
