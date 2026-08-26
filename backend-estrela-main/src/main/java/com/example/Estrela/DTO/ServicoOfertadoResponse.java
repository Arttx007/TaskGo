package com.example.Estrela.DTO;

import com.example.Estrela.Entity.StatusServico;

import java.math.BigDecimal;

public record ServicoOfertadoResponse(Long id, Long prestadorId, String categoria, String descricao,
                                       BigDecimal preco, StatusServico status) {
}
