package com.example.Estrela.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import jakarta.persistence.Id;

import java.math.BigDecimal;

@Entity
@Table(name = "dim_prestador")
@Data
public class Prestador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPrestador;

    private String nome;
    private String especialidade;
    private BigDecimal nota_media;
    private String cidade;

    @Column(unique = true)
    private String email;
    private String senha;

    @Enumerated(EnumType.STRING)
    private StatusKyc statusKyc = StatusKyc.PENDENTE;

    private String documentoIdentidadeUrl;
    private String comprovantePixUrl;
    private String chavePix;

    private BigDecimal saldoDisponivel = BigDecimal.ZERO;

    @Version
    private Long version;
}
