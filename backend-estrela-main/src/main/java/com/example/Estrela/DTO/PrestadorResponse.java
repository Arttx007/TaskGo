package com.example.Estrela.DTO;

import com.example.Estrela.Entity.StatusKyc;

import java.math.BigDecimal;

/**
 * Dados públicos de um prestador — nunca inclui a senha nem o saldo (ver {@link CarteiraResponse}).
 */
public record PrestadorResponse(Long idPrestador, String nome, String especialidade, BigDecimal notaMedia,
                                 String cidade, String email, StatusKyc statusKyc) {
}
