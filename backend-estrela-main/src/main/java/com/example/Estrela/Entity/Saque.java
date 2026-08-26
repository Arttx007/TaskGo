package com.example.Estrela.Entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pedido de saque Pix de um {@link Prestador} sobre o saldo disponível (RN03, US-08).
 */
@Entity
@Table(name = "saque")
@Data
public class Saque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_prestador")
    private Prestador prestador;

    private BigDecimal valor;
    private String chavePixDestino;

    @Enumerated(EnumType.STRING)
    private StatusSaque status;

    private LocalDateTime criadoEm;
}
