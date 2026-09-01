package com.example.Estrela.DTO;

import com.example.Estrela.Entity.TipoUsuario;

import java.time.LocalDateTime;

/**
 * Mensagem de uma conversa, como as duas partes a veem.
 *
 * @param id             identificador da mensagem
 * @param conteudo       texto escrito
 * @param remetenteTipo  papel de quem escreveu: cliente ou prestador
 * @param remetenteNome  nome de quem escreveu
 * @param criadoEm       momento do envio
 * @param lida           se a outra parte já leu
 */
public record MensagemResponse(Long id,
                               String conteudo,
                               TipoUsuario remetenteTipo,
                               String remetenteNome,
                               LocalDateTime criadoEm,
                               boolean lida) {
}
