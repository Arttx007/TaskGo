package com.example.Estrela.Service;

import com.example.Estrela.DTO.BuscaServicoResponse;
import com.example.Estrela.DTO.CategoriaDisponivelResponse;
import com.example.Estrela.DTO.EstimativaPrecoResponse;
import com.example.Estrela.DTO.ResultadoBuscaServico;
import com.example.Estrela.DTO.ServicoOfertadoRequest;
import com.example.Estrela.Entity.*;
import com.example.Estrela.exception.AcessoNegadoException;
import com.example.Estrela.exception.KycPendenteException;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.LocalizacaoRepository;
import com.example.Estrela.repository.PrestadorRepository;
import com.example.Estrela.repository.ServicoOfertadoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Catálogo de serviços publicados por prestadores (US-02) e busca por geolocalização (US-03).
 */
@Service
public class ServicoOfertadoService {

    private static final String CHAVE_RAIO_PADRAO_KM = "busca.raio-padrao-km";

    /**
     * Abaixo deste número de serviços a faixa de preço não é devolvida: com um ou dois serviços, o
     * "mínimo" e o "máximo" da categoria são o preço de um prestador identificável.
     */
    private static final int AMOSTRA_MINIMA_ESTIMATIVA = 3;

    private final ServicoOfertadoRepository servicoOfertadoRepository;
    private final PrestadorRepository prestadorRepository;
    private final LocalizacaoRepository localizacaoRepository;
    private final GeoService geoService;
    private final ParametroNegocioService parametroNegocioService;

    public ServicoOfertadoService(ServicoOfertadoRepository servicoOfertadoRepository,
                                   PrestadorRepository prestadorRepository,
                                   LocalizacaoRepository localizacaoRepository,
                                   GeoService geoService,
                                   ParametroNegocioService parametroNegocioService) {
        this.servicoOfertadoRepository = servicoOfertadoRepository;
        this.prestadorRepository = prestadorRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.geoService = geoService;
        this.parametroNegocioService = parametroNegocioService;
    }

    /**
     * Publica um novo serviço no catálogo do prestador (US-02).
     *
     * @param prestadorId id do prestador autenticado
     * @param request     dados do serviço
     * @return o serviço criado
     * @throws RecursoNaoEncontradoException se o prestador não existir
     * @throws KycPendenteException          se o prestador não estiver com KYC aprovado (RN04)
     */
    public ServicoOfertado criar(Long prestadorId, ServicoOfertadoRequest request) {
        Prestador prestador = buscarPrestador(prestadorId);
        exigirKycAprovado(prestador);

        ServicoOfertado servico = new ServicoOfertado();
        servico.setPrestador(prestador);
        servico.setCategoria(request.categoria());
        servico.setDescricao(request.descricao());
        servico.setPreco(request.preco());
        servico.setStatus(StatusServico.ATIVO);

        if (request.localizacaoId() != null) {
            Localizacao localizacao = localizacaoRepository.findById(request.localizacaoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Localização não encontrada"));
            servico.setLocalizacao(localizacao);
        }

        return servicoOfertadoRepository.save(servico);
    }

    public List<ServicoOfertado> listarMeus(Long prestadorId) {
        return servicoOfertadoRepository.findByPrestador_IdPrestador(prestadorId);
    }

    /**
     * Atualiza um serviço existente do prestador (US-02).
     *
     * @throws AcessoNegadoException se o serviço não pertencer ao prestador autenticado
     */
    public ServicoOfertado atualizar(Long servicoId, Long prestadorIdAutenticado, ServicoOfertadoRequest request) {
        ServicoOfertado servico = buscarEValidarDono(servicoId, prestadorIdAutenticado);

        servico.setCategoria(request.categoria());
        servico.setDescricao(request.descricao());
        servico.setPreco(request.preco());

        if (request.localizacaoId() != null) {
            Localizacao localizacao = localizacaoRepository.findById(request.localizacaoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Localização não encontrada"));
            servico.setLocalizacao(localizacao);
        }

        return servicoOfertadoRepository.save(servico);
    }

    public ServicoOfertado alternarAtivo(Long servicoId, Long prestadorIdAutenticado, boolean ativo) {
        ServicoOfertado servico = buscarEValidarDono(servicoId, prestadorIdAutenticado);
        servico.setStatus(ativo ? StatusServico.ATIVO : StatusServico.INATIVO);
        return servicoOfertadoRepository.save(servico);
    }

    public void excluir(Long servicoId, Long prestadorIdAutenticado) {
        ServicoOfertado servico = buscarEValidarDono(servicoId, prestadorIdAutenticado);
        servicoOfertadoRepository.delete(servico);
    }

    /**
     * Lista as categorias com ao menos um serviço disponível ao público, com a contagem de cada uma.
     *
     * <p>A agregação acontece no banco, via {@code GROUP BY} no repository, e não carregando os
     * serviços para contar em memória: é uma contagem, e a convenção do backend reserva o
     * processamento em memória para o que não é portável em SQL — caso do Haversine, não deste.
     *
     * @return categorias ordenadas da mais ofertada para a menos; lista vazia quando não há oferta
     */
    public List<CategoriaDisponivelResponse> listarCategoriasDisponiveis() {
        return servicoOfertadoRepository.agregarCategoriasDisponiveis(StatusServico.ATIVO, StatusKyc.APROVADO);
    }

    /**
     * Apura a faixa de preço praticada numa categoria, a partir dos serviços efetivamente publicados
     * (`ATIVO` de prestador `APROVADO`, RN04). Não é predição: são os preços que os prestadores
     * cobram hoje.
     *
     * <p>A mediana é calculada em Java, e não em SQL, pelo mesmo motivo do Haversine em
     * {@link GeoService}: {@code PERCENTILE_CONT} não é portável entre PostgreSQL (dev) e H2
     * (testes), e as amostras aqui são pequenas.
     *
     * <p>Amostra abaixo de {@value #AMOSTRA_MINIMA_ESTIMATIVA} não devolve faixa, apenas mensagem.
     * Ausência de dado é resposta bem-sucedida com mensagem, não erro — mesma convenção de
     * {@link #buscar}.
     *
     * @param categoria categoria a apurar; obrigatória
     * @return faixa apurada, ou apenas a mensagem quando não há amostra suficiente
     */
    public EstimativaPrecoResponse estimarPreco(String categoria) {
        List<BigDecimal> precos = servicoOfertadoRepository
                .findByStatusAndCategoriaIgnoreCase(StatusServico.ATIVO, categoria).stream()
                .filter(s -> s.getPrestador() != null && s.getPrestador().getStatusKyc() == StatusKyc.APROVADO)
                .map(ServicoOfertado::getPreco)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();

        if (precos.isEmpty()) {
            return new EstimativaPrecoResponse(categoria, null, null, null, 0,
                    "Ainda não há preços cadastrados nesta categoria");
        }
        if (precos.size() < AMOSTRA_MINIMA_ESTIMATIVA) {
            return new EstimativaPrecoResponse(categoria, null, null, null, precos.size(),
                    "Ainda não há preços suficientes nesta categoria para calcular uma faixa");
        }

        return new EstimativaPrecoResponse(categoria, precos.get(0), mediana(precos),
                precos.get(precos.size() - 1), precos.size(), null);
    }

    /**
     * @param precosOrdenados preços já ordenados de forma crescente, com ao menos um elemento
     * @return o valor central; com quantidade par, a média dos dois centrais
     */
    private BigDecimal mediana(List<BigDecimal> precosOrdenados) {
        int n = precosOrdenados.size();
        int meio = n / 2;

        if (n % 2 != 0) {
            return precosOrdenados.get(meio);
        }
        return precosOrdenados.get(meio - 1).add(precosOrdenados.get(meio))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    /**
     * Busca serviços ativos de prestadores aprovados por proximidade (US-03). Quando lat/lon não
     * são informados, cai no fallback por nome de cidade (cenário de exceção do Gherkin: "busca
     * manual por endereço").
     */
    public ResultadoBuscaServico buscar(String categoria, Double lat, Double lon, Double raioKm, String cidade) {
        List<ServicoOfertado> candidatos;
        boolean comCoordenadas = lat != null && lon != null;

        if (comCoordenadas) {
            candidatos = servicoOfertadoRepository.findByStatusAndCategoriaIgnoreCase(StatusServico.ATIVO, categoria);
        } else if (cidade != null && !cidade.isBlank()) {
            candidatos = servicoOfertadoRepository
                    .findByStatusAndCategoriaIgnoreCaseAndLocalizacao_CidadeIgnoreCase(StatusServico.ATIVO, categoria, cidade);
        } else {
            candidatos = List.of();
        }

        double raioEfetivoKm = raioKm != null ? raioKm : parametroNegocioService.valor(CHAVE_RAIO_PADRAO_KM).doubleValue();

        List<BuscaServicoResponse> resultados = candidatos.stream()
                .filter(s -> s.getPrestador() != null && s.getPrestador().getStatusKyc() == StatusKyc.APROVADO)
                .map(s -> paraResposta(s, lat, lon, comCoordenadas))
                .filter(r -> !comCoordenadas || r.distanciaKm() == null || r.distanciaKm() <= raioEfetivoKm)
                .sorted((a, b) -> {
                    if (a.distanciaKm() == null || b.distanciaKm() == null) return 0;
                    return Double.compare(a.distanciaKm(), b.distanciaKm());
                })
                .toList();

        if (resultados.isEmpty()) {
            return new ResultadoBuscaServico(List.of(), "Nenhum resultado encontrado nesta região");
        }
        return new ResultadoBuscaServico(resultados, null);
    }

    private BuscaServicoResponse paraResposta(ServicoOfertado servico, Double lat, Double lon, boolean comCoordenadas) {
        Double distanciaKm = null;
        Localizacao localizacao = servico.getLocalizacao();

        if (comCoordenadas && localizacao != null && localizacao.getLatitude() != null && localizacao.getLongitude() != null) {
            distanciaKm = geoService.distanciaKm(lat, lon, localizacao.getLatitude(), localizacao.getLongitude());
        }

        Prestador prestador = servico.getPrestador();
        return new BuscaServicoResponse(
                servico.getId(),
                servico.getCategoria(),
                servico.getDescricao(),
                servico.getPreco(),
                prestador.getIdPrestador(),
                prestador.getNome(),
                prestador.getNota_media(),
                distanciaKm
        );
    }

    private ServicoOfertado buscarEValidarDono(Long servicoId, Long prestadorIdAutenticado) {
        ServicoOfertado servico = servicoOfertadoRepository.findById(servicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço ofertado não encontrado"));
        if (!servico.getPrestador().getIdPrestador().equals(prestadorIdAutenticado)) {
            throw new AcessoNegadoException("Este serviço pertence a outro prestador");
        }
        return servico;
    }

    private Prestador buscarPrestador(Long prestadorId) {
        return prestadorRepository.findById(prestadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Prestador não encontrado"));
    }

    private void exigirKycAprovado(Prestador prestador) {
        if (prestador.getStatusKyc() != StatusKyc.APROVADO) {
            throw new KycPendenteException("Verificação de cadastro (KYC) precisa ser concluída antes de publicar serviços");
        }
    }
}
