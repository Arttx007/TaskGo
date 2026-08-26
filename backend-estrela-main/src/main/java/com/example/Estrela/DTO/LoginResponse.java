package com.example.Estrela.DTO;

import com.example.Estrela.Entity.TipoUsuario;

/**
 * @param token       JWT a ser enviado em requisições subsequentes no header {@code Authorization}
 * @param tipoUsuario papel do usuário autenticado
 * @param id          identificador do usuário autenticado
 * @param nome        nome do usuário autenticado
 */
public record LoginResponse(String token, TipoUsuario tipoUsuario, Long id, String nome) {
}
