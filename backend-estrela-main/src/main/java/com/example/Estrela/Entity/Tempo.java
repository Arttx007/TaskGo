package com.example.Estrela.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.Data;
import jakarta.persistence.Id;

import java.time.LocalDate;

@Entity
@Table(name = "dim_tempo")
@Data
public class Tempo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_tempo;

    private LocalDate data;
    private Integer dia;
    private Integer mes;
    private Integer ano;
    private String dia_semana;
}
