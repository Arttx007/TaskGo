package com.example.Estrela.Entity;

import jakarta.persistence.*;
import lombok.Data;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    /**
     * Endereço de atendimento escolhido pelo cliente na abertura, ou {@code null}
     * quando a solicitação foi aberta sem informar endereço.
     */
    @ManyToOne
    @JoinColumn(name = "id_endereco_cliente")
    private EnderecoCliente enderecoCliente;

    /**
     * Código de quatro dígitos gerado no aceite, entregue apenas ao cliente dono e
     * exigido do prestador para iniciar o atendimento (RN02).
     */
    private String pinConfirmacao;

    private LocalDateTime criadoEm;
    private LocalDateTime aceitoEm;
    private LocalDateTime iniciadoEm;
    private LocalDateTime concluidoEm;
}
