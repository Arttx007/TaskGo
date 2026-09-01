package com.example.Estrela.Entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Mensagem trocada entre o cliente e o prestador de uma solicitação
 * ({@link FatoServico}).
 *
 * <p>Toda mensagem pertence a uma solicitação, e apenas as duas partes dela leem ou
 * escrevem: não existe conversa fora de uma solicitação.
 *
 * <p>O remetente é identificado por papel mais identificador, e não por uma associação
 * JPA, porque cliente e prestador vivem em tabelas distintas e o projeto não tem tabela
 * de usuário unificada. O papel diz em qual tabela {@code remetenteId} deve ser lido.
 */
@Entity
@Table(name = "mensagem")
@Data
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_fato_servico")
    private FatoServico fatoServico;

    /** Papel de quem escreveu: {@code CLIENTE} ou {@code PRESTADOR}. */
    @Enumerated(EnumType.STRING)
    private TipoUsuario remetenteTipo;

    /** Identificador de quem escreveu, na tabela indicada por {@code remetenteTipo}. */
    private Long remetenteId;

    private String conteudo;

    private LocalDateTime criadoEm;

    /** Momento em que a outra parte marcou a mensagem como lida, ou {@code null}. */
    private LocalDateTime lidaEm;
}
