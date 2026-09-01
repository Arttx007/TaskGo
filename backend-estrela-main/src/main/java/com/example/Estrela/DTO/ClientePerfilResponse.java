package com.example.Estrela.DTO;

/**
 * Perfil da conta de cliente autenticada.
 *
 * <p>Nunca inclui a senha, em nenhuma forma — nem em texto puro nem em hash.
 *
 * @param idCliente   identificador da conta
 * @param nome        nome completo
 * @param email       e-mail usado no login
 * @param telefone    telefone de contato, ou {@code null} se nunca informado
 * @param idade       idade informada, ou {@code null}
 * @param cidade      cidade informada, ou {@code null}
 * @param tipoCliente segmentação livre do cliente
 * @param fotoUrl     caminho relativo da foto de perfil, ou {@code null} se nunca enviada
 */
public record ClientePerfilResponse(Long idCliente,
                                    String nome,
                                    String email,
                                    String telefone,
                                    Integer idade,
                                    String cidade,
                                    String tipoCliente,
                                    String fotoUrl) {
}
