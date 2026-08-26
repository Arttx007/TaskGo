package com.example.Estrela.Entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Item do catálogo publicado por um {@link Prestador} — distinto de {@link FatoServico},
 * que representa uma solicitação/transação concreta sobre um destes itens.
 */
@Entity
@Table(name = "servico_ofertado")
@Data
public class ServicoOfertado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_prestador")
    private Prestador prestador;

    private String categoria;
    private String descricao;
    private BigDecimal preco;

    @Enumerated(EnumType.STRING)
    private StatusServico status = StatusServico.ATIVO;

    @ManyToOne
    @JoinColumn(name = "id_localizacao")
    private Localizacao localizacao;
}
