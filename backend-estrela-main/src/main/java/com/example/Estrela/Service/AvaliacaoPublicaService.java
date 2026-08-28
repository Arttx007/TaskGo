package com.example.Estrela.Service;

import com.example.Estrela.DTO.AvaliacaoPublicaResponse;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.Localizacao;
import com.example.Estrela.Entity.ServicoOfertado;
import com.example.Estrela.Entity.StatusKyc;
import com.example.Estrela.Entity.StatusSolicitacao;
import com.example.Estrela.Entity.Tempo;
import com.example.Estrela.repository.FatoServicoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Leitura pública das avaliações mais recentes, para alimentar os depoimentos do site (US-09).
 *
 * <p>Existe para que a prova social exibida ao visitante venha de avaliação real em vez de
 * depoimento inventado. A contrapartida é privacidade: o recorte para primeiro nome acontece aqui,
 * antes de a resposta sair da aplicação.
 */
@Service
public class AvaliacaoPublicaService {

    /** Quantidade devolvida quando o cliente não informa limite. */
    static final int LIMITE_PADRAO = 6;

    /** Teto aceito: limite acima disso é truncado, não recusado. */
    static final int LIMITE_MAXIMO = 20;

    private final FatoServicoRepository fatoServicoRepository;

    public AvaliacaoPublicaService(FatoServicoRepository fatoServicoRepository) {
        this.fatoServicoRepository = fatoServicoRepository;
    }

    /**
     * Lista as avaliações mais recentes exibíveis publicamente.
     *
     * <p>Considera apenas solicitação em `AVALIADO`, com nota e comentário preenchidos, de prestador
     * `APROVADO` (RN04). Lista vazia é resposta legítima — o site esconde a seção em vez de mostrar
     * moldura sem conteúdo.
     *
     * @param limite quantidade desejada; nulo ou não positivo aplica {@value #LIMITE_PADRAO}, e valor
     *               acima de {@value #LIMITE_MAXIMO} é truncado para o teto em vez de recusado
     * @return avaliações da mais recente para a mais antiga; lista vazia quando não há nenhuma
     */
    public List<AvaliacaoPublicaResponse> listarRecentes(Integer limite) {
        int efetivo = (limite == null || limite <= 0) ? LIMITE_PADRAO : Math.min(limite, LIMITE_MAXIMO);

        return fatoServicoRepository
                .buscarAvaliacoesPublicas(StatusSolicitacao.AVALIADO, StatusKyc.APROVADO, PageRequest.of(0, efetivo))
                .stream()
                .map(this::paraResposta)
                .toList();
    }

    private AvaliacaoPublicaResponse paraResposta(FatoServico fato) {
        ServicoOfertado servico = fato.getServicoOfertado();
        Localizacao localizacao = fato.getLocalizacao();
        Tempo tempo = fato.getTempo();

        return new AvaliacaoPublicaResponse(
                fato.getAvaliacao(),
                fato.getComentarioAvaliacao(),
                primeiroNome(fato.getCliente()),
                servico != null ? servico.getCategoria() : null,
                localizacao != null ? localizacao.getCidade() : null,
                tempo != null ? tempo.getData() : null
        );
    }

    /**
     * @param cliente cliente que avaliou; tolera nulo para não quebrar a listagem por dado legado
     * @return apenas o primeiro nome, ou nulo quando não há nome registrado
     */
    private String primeiroNome(Cliente cliente) {
        if (cliente == null || cliente.getNome() == null) return null;

        String nome = cliente.getNome().trim();
        if (nome.isEmpty()) return null;

        int espaco = nome.indexOf(' ');
        return espaco < 0 ? nome : nome.substring(0, espaco);
    }
}
