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

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}
