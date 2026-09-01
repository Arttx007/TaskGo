package com.example.Estrela.Entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro de custódia de um pagamento sobre uma solicitação ({@link FatoServico}) — RN03.
 */
@Entity
@Table(name = "pagamento")
@Data
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "id_fato_servico")
    private FatoServico fatoServico;

    private BigDecimal valorBruto;
    private BigDecimal valorTaxa;
    private BigDecimal valorLiquido;

    @Enumerated(EnumType.STRING)
    private StatusPagamento status;

    private String metodoPagamento;

    /**
     * Valor efetivamente devolvido ao cliente no estorno. Igual a {@code valorBruto}
     * num estorno integral, e {@code valorBruto - valorTaxaCancelamento} num parcial.
     */
    private BigDecimal valorEstornado;

    /**
     * Taxa de cancelamento retida e creditada ao prestador (RN03), ou {@code null}
     * quando não houve cancelamento fora da carência.
     */
    private BigDecimal valorTaxaCancelamento;

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}
