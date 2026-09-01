package com.example.Estrela.DTO;

import com.example.Estrela.Entity.StatusPagamento;
import com.example.Estrela.Entity.StatusSolicitacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Solicitação de serviço como cliente ou prestador a veem.
 *
 * <p>{@code pinConfirmacao} é preenchido <b>apenas para o cliente dono</b>. O prestador
 * recebe {@code null} em qualquer consulta, inclusive na listagem: o código existe para o
 * cliente confirmar que quem chegou é quem foi contratado, e vazá-lo ao prestador anularia o
 * mecanismo inteiro.
 *
 * <p>Momento não ocorrido vem {@code null}, e a interface apresenta a etapa como não
 * cumprida — em vez de exibir horário estimado.
 *
 * @param statusPagamento           situação do pagamento, ou {@code null} se ainda não houve cobrança
 * @param pinConfirmacao            código de confirmação, só para o cliente dono e só após o aceite
 * @param enderecoAtendimento       local combinado, ou {@code null} se a solicitação foi aberta sem endereço
 * @param taxaCancelamentoPrevista  quanto seria retido se o cliente cancelasse agora; zero se integral
 */
public record SolicitacaoResponse(Long id, StatusSolicitacao status, Long clienteId, String clienteNome,
                                   Long prestadorId, String prestadorNome, Long servicoOfertadoId,
                                   String categoria, BigDecimal valor, Integer avaliacao, String comentarioAvaliacao,
                                   StatusPagamento statusPagamento,
                                   String pinConfirmacao,
                                   EnderecoClienteResponse enderecoAtendimento,
                                   BigDecimal taxaCancelamentoPrevista,
                                   LocalDateTime criadoEm,
                                   LocalDateTime aceitoEm,
                                   LocalDateTime iniciadoEm,
                                   LocalDateTime concluidoEm) {
}
