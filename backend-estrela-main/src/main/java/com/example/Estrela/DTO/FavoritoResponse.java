package com.example.Estrela.DTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Prestador favoritado, como o cliente dono o vê.
 *
 * <p>{@code disponivel} vem falso quando o prestador deixou de estar com verificação
 * aprovada. Nesse caso ele <b>continua na lista</b>, sinalizado: desaparecer sem explicação
 * deixaria o cliente sem entender por que não consegue mais contratá-lo.
 *
 * <p>Não traz dado de contato do prestador — favoritar não dá acesso à pessoa fora da
 * plataforma.
 *
 * @param prestadorId          identificador do prestador
 * @param nome                 nome do prestador
 * @param especialidade        especialidade declarada, ou {@code null}
 * @param cidade               cidade do prestador, ou {@code null}
 * @param notaMedia            nota média, ou {@code null} se ainda não foi avaliado
 * @param servicosAtivos       quantos serviços ativos ele oferece agora
 * @param disponivel           se pode ser contratado (verificação aprovada e com serviço ativo)
 * @param favoritadoEm         quando o cliente o marcou
 */
public record FavoritoResponse(Long prestadorId,
                               String nome,
                               String especialidade,
                               String cidade,
                               BigDecimal notaMedia,
                               int servicosAtivos,
                               boolean disponivel,
                               LocalDateTime favoritadoEm) {
}
