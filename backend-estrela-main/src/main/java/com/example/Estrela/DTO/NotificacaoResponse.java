package com.example.Estrela.DTO;

import java.time.LocalDateTime;

/**
 * Aviso de atividade do cliente, apurado do estado corrente da conta.
 *
 * <p>Não existe campo de leitura, e isso é deliberado: o aviso não é um registro
 * armazenado, é uma pendência calculada. Ele deixa de existir quando o fato que o originou
 * é resolvido, então marcar como lido não teria sentido — e persistir estado de leitura
 * exigiria tabela e evento de domínio, que é escopo da notificação enviada (US-11).
 *
 * @param tipo          natureza da pendência (ex.: {@code PAGAMENTO_PENDENTE})
 * @param texto         descrição apresentável ao cliente
 * @param solicitacaoId solicitação relacionada, ou {@code null} se o aviso não vier de uma
 * @param momento       quando ocorreu o fato que originou o aviso
 */
public record NotificacaoResponse(String tipo,
                                  String texto,
                                  Long solicitacaoId,
                                  LocalDateTime momento) {
}
