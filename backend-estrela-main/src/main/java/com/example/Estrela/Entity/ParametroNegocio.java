package com.example.Estrela.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Parâmetro de negócio ajustável sem deploy (ex.: limiar e valores de RN01) — chave/valor no banco.
 */
@Entity
@Table(name = "parametro_negocio")
@Data
public class ParametroNegocio {

    @Id
    private String chave;

    private BigDecimal valor;
    private String descricao;
}
