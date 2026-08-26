package com.example.Estrela.Entity;

import jakarta.persistence.*;
import lombok.Data;
import jakarta.persistence.Id;

import java.math.BigDecimal;

@Entity
@Table(name = "fato_servicos")
@Data
public class FatoServico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id_servico;

    @ManyToOne
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "id_prestador")
    private Prestador prestador;

    @ManyToOne
    @JoinColumn(name = "id_tempo")
    private Tempo tempo;

    @ManyToOne
    @JoinColumn(name = "id_localizacao")
    private Localizacao localizacao;

    @ManyToOne
    @JoinColumn(name = "id_servico_ofertado")
    private ServicoOfertado servicoOfertado;

    private BigDecimal valor;
    private Integer tempo_execucao;

    @Enumerated(EnumType.STRING)
    private StatusSolicitacao status;

    private Integer avaliacao;
    private String comentarioAvaliacao;
}
