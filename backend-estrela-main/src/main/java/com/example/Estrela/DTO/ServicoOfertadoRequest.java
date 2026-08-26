package com.example.Estrela.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * @param categoria   categoria do serviço (ex.: "eletricista")
 * @param descricao   descrição livre do serviço
 * @param preco       valor cobrado pelo prestador — deve ser positivo (US-02, caso extremo)
 * @param localizacaoId dimensão de localização onde o serviço é oferecido
 */
public record ServicoOfertadoRequest(
        @NotBlank(message = "categoria é obrigatória") String categoria,
        String descricao,
        @DecimalMin(value = "0.01", message = "preço deve ser um valor positivo") BigDecimal preco,
        Long localizacaoId
) {
}
