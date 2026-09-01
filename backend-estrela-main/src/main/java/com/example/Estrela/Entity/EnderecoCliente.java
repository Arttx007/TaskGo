package com.example.Estrela.Entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Endereço de atendimento cadastrado por um {@link Cliente}.
 *
 * <p>Não se confunde com {@link Localizacao}: aquela é a dimensão compartilhada do star
 * schema, usada como localização do serviço ofertado, e não tem rua, número ou CEP nem
 * pertence a um cliente. Esta é o endereço onde o atendimento acontece.
 *
 * <p>Exatamente um endereço de cada cliente é o padrão, invariante garantida em
 * {@code EnderecoClienteService} ao gravar. A remoção é lógica ({@code ativo = false}),
 * porque um endereço já referenciado por uma solicitação não pode desaparecer do
 * histórico.
 */
@Entity
@Table(name = "endereco_cliente")
@Data
public class EnderecoCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    /** Nome curto dado pelo cliente para reconhecer o endereço (ex.: "Casa", "Trabalho"). */
    private String apelido;

    private String cep;
    private String rua;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String uf;

    /**
     * Coordenadas do endereço, quando conhecidas. Ausentes, a busca por proximidade e a
     * apuração da taxa de cancelamento por distância caem para o comportamento sem
     * distância — nunca falham por isso.
     */
    private Double latitude;
    private Double longitude;

    /** Endereço usado por padrão quando o cliente não escolhe outro. */
    private Boolean padrao = false;

    /** Endereço visível na lista do cliente. Removido logicamente, permanece no histórico. */
    private Boolean ativo = true;
}
