package com.example.Estrela.DTO;

import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.StatusKyc;

import java.math.BigDecimal;

/**
 * Dados públicos de um prestador — nunca inclui a senha nem o saldo (ver {@link CarteiraResponse}).
 */
public record PrestadorResponse(Long idPrestador, String nome, String especialidade, BigDecimal notaMedia,
                                 String cidade, String email, StatusKyc statusKyc) {

    /**
     * Converte a entidade na sua representação pública. Existe aqui, e não em cada Controller,
     * porque o mesmo mapeamento é usado tanto no cadastro do prestador quanto nas rotas de
     * administração — mantê-lo em um lugar só evita que as duas respostas divirjam.
     *
     * @param prestador entidade a converter; não pode ser nulo
     * @return a representação pública correspondente
     */
    public static PrestadorResponse de(Prestador prestador) {
        return new PrestadorResponse(prestador.getIdPrestador(), prestador.getNome(), prestador.getEspecialidade(),
                prestador.getNota_media(), prestador.getCidade(), prestador.getEmail(), prestador.getStatusKyc());
    }
}
