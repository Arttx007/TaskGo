package com.example.Estrela.DTO;

import java.time.LocalDate;

/**
 * Avaliação real exibível a visitantes não autenticados, para alimentar os depoimentos do site.
 *
 * <p>Identifica quem avaliou apenas pelo primeiro nome, e não carrega id, e-mail nem nome completo:
 * o recorte é feito no backend porque privacidade que depende do consumidor não é garantia — uma
 * rota pública que devolve nome completo já vazou, independentemente do que a página renderize.
 *
 * @param nota                nota de 1 a 5 registrada pelo cliente
 * @param comentario          comentário escrito pelo cliente; nunca vazio (avaliação sem texto não é devolvida)
 * @param clientePrimeiroNome apenas o primeiro nome de quem avaliou
 * @param categoria           categoria do serviço avaliado; nulo para solicitação antiga sem serviço vinculado
 * @param cidade              cidade do atendimento; nulo quando a solicitação não tem localização
 * @param data                data do atendimento
 */
public record AvaliacaoPublicaResponse(int nota, String comentario, String clientePrimeiroNome, String categoria,
                                        String cidade, LocalDate data) {
}
