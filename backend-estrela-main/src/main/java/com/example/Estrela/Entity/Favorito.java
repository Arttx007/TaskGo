package com.example.Estrela.Entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Marcação de um {@link Prestador} como favorito por um {@link Cliente}.
 *
 * <p>O par cliente+prestador é único: marcar de novo quem já é favorito é recusado com
 * {@code EstadoInvalidoException} em vez de criar registro duplicado.
 *
 * <p>Favoritar não é contratar. Uma solicitação é sempre aberta contra um
 * {@link ServicoOfertado}, então o caminho de contratação a partir de um favorito passa
 * pelo catálogo de serviços ativos daquele prestador.
 */
@Entity
@Table(name = "favorito")
@Data
public class Favorito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "id_prestador")
    private Prestador prestador;

    private LocalDateTime criadoEm;
}
