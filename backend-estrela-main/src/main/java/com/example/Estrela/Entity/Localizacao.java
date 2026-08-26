package com.example.Estrela.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.Data;
import jakarta.persistence.Id;

@Entity
@Table(name = "dim_localizacao")
@Data
public class Localizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_localizacao;

    private String cidade;
    private String estado;
    private String bairro;

    private Double latitude;
    private Double longitude;
}