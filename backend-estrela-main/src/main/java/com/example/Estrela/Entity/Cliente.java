package com.example.Estrela.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.Data;
import jakarta.persistence.Id;

@Entity
@Table(name = "dim_cliente")
@Data
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCliente;

    private String nome;
    private Integer idade;
    private String cidade;
    private String tipo_cliente;
    private String email;
    private String senha;

    /** Telefone de contato informado pelo cliente no próprio perfil. */
    private String telefone;

    /** Caminho relativo da foto de perfil, ou {@code null} quando nunca foi enviada. */
    private String fotoUrl;

    /** Conta ativa. Desativação lógica preserva solicitações, pagamentos e avaliações. */
    private Boolean ativo = true;
}
