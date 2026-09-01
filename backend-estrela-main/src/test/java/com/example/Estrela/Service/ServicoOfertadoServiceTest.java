package com.example.Estrela.Service;

import com.example.Estrela.DTO.BuscaServicoResponse;
import com.example.Estrela.DTO.CategoriaDisponivelResponse;
import com.example.Estrela.DTO.EstimativaPrecoResponse;
import com.example.Estrela.Entity.Localizacao;
import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.ServicoOfertado;
import com.example.Estrela.Entity.StatusKyc;
import com.example.Estrela.Entity.StatusServico;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.exception.ValidacaoException;
import com.example.Estrela.repository.LocalizacaoRepository;
import com.example.Estrela.repository.PrestadorRepository;
import com.example.Estrela.repository.ServicoOfertadoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Catálogo público de categorias e faixa de preço praticada.
 *
 * <p>A faixa só é devolvida com amostra de três ou mais serviços: abaixo disso, o mínimo e o máximo
 * da categoria seriam o preço de um prestador identificável.
 */
@ExtendWith(MockitoExtension.class)
class ServicoOfertadoServiceTest {

    @Mock
    private ServicoOfertadoRepository servicoOfertadoRepository;
    @Mock
    private PrestadorRepository prestadorRepository;
    @Mock
    private LocalizacaoRepository localizacaoRepository;
    @Mock
    private GeoService geoService;
    @Mock
    private ParametroNegocioService parametroNegocioService;

    @InjectMocks
    private ServicoOfertadoService servicoOfertadoService;

    @Test
    void deveDelegarCatalogoDeCategoriasAoRepository() {
        when(servicoOfertadoRepository.agregarCategoriasDisponiveis(StatusServico.ATIVO, StatusKyc.APROVADO))
                .thenReturn(List.of(new CategoriaDisponivelResponse("eletricista", 4L),
                        new CategoriaDisponivelResponse("encanador", 2L)));

        List<CategoriaDisponivelResponse> resultado = servicoOfertadoService.listarCategoriasDisponiveis();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).categoria()).isEqualTo("eletricista");
        assertThat(resultado.get(0).totalServicos()).isEqualTo(4);
    }

    @Test
    void deveApurarFaixaComAmostraSuficiente() {
        mockarServicos("120.00", "400.00", "180.00", "200.00");

        EstimativaPrecoResponse resultado = servicoOfertadoService.estimarPreco("eletricista");

        assertThat(resultado.amostra()).isEqualTo(4);
        assertThat(resultado.minimo()).isEqualByComparingTo("120.00");
        assertThat(resultado.maximo()).isEqualByComparingTo("400.00");
        assertThat(resultado.mensagem()).isNull();
    }

    @Test
    void medianaComQuantidadeParEhMediaDosDoisCentrais() {
        mockarServicos("120.00", "180.00", "200.00", "400.00");

        EstimativaPrecoResponse resultado = servicoOfertadoService.estimarPreco("eletricista");

        assertThat(resultado.mediana()).isEqualByComparingTo("190.00");
    }

    @Test
    void medianaComQuantidadeImparEhOValorCentral() {
        mockarServicos("100.00", "150.00", "900.00");

        EstimativaPrecoResponse resultado = servicoOfertadoService.estimarPreco("eletricista");

        assertThat(resultado.mediana()).isEqualByComparingTo("150.00");
    }

    @Test
    void naoDeveRevelarFaixaComUmUnicoServico() {
        mockarServicos("120.00");

        EstimativaPrecoResponse resultado = servicoOfertadoService.estimarPreco("eletricista");

        assertThat(resultado.amostra()).isEqualTo(1);
        assertThat(resultado.minimo()).isNull();
        assertThat(resultado.mediana()).isNull();
        assertThat(resultado.maximo()).isNull();
        assertThat(resultado.mensagem()).contains("suficientes");
    }

    @Test
    void naoDeveRevelarFaixaComDoisServicos() {
        mockarServicos("150.00", "190.00");

        EstimativaPrecoResponse resultado = servicoOfertadoService.estimarPreco("eletricista");

        assertThat(resultado.amostra()).isEqualTo(2);
        assertThat(resultado.minimo()).isNull();
        assertThat(resultado.mensagem()).contains("suficientes");
    }

    @Test
    void categoriaSemServicoDevolveMensagemESucesso() {
        when(servicoOfertadoRepository.findByStatusAndCategoriaIgnoreCase(any(), anyString()))
                .thenReturn(List.of());

        EstimativaPrecoResponse resultado = servicoOfertadoService.estimarPreco("inexistente");

        assertThat(resultado.amostra()).isZero();
        assertThat(resultado.minimo()).isNull();
        assertThat(resultado.mensagem()).isNotBlank();
    }

    @Test
    void precoDePrestadorNaoAprovadoNaoEntraNaApuracao() {
        ServicoOfertado aprovado1 = servico("100.00", StatusKyc.APROVADO);
        ServicoOfertado aprovado2 = servico("200.00", StatusKyc.APROVADO);
        ServicoOfertado aprovado3 = servico("300.00", StatusKyc.APROVADO);
        ServicoOfertado pendente = servico("9999.00", StatusKyc.PENDENTE);

        when(servicoOfertadoRepository.findByStatusAndCategoriaIgnoreCase(any(), anyString()))
                .thenReturn(List.of(aprovado1, pendente, aprovado2, aprovado3));

        EstimativaPrecoResponse resultado = servicoOfertadoService.estimarPreco("eletricista");

        assertThat(resultado.amostra()).isEqualTo(3);
        assertThat(resultado.maximo()).isEqualByComparingTo("300.00");
        assertThat(resultado.mediana()).isEqualByComparingTo("200.00");
    }

    // --- Coordenadas na resposta da busca ---

    @Test
    void resultadoTrazCoordenadaArredondadaEmTresCasas() {
        ServicoOfertado servico = servicoComLocalizacao("100.00", StatusKyc.APROVADO, -8.1215567, -34.9005432);
        mockarBusca(servico);
        when(geoService.distanciaKm(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(1.5);

        BuscaServicoResponse r = servicoOfertadoService
                .buscar("eletricista", -8.12, -34.90, 10.0, null).resultados().get(0);

        assertThat(r.latitude()).isEqualTo(-8.122);
        assertThat(r.longitude()).isEqualTo(-34.901);
    }

    @Test
    void distanciaEhCalculadaComACoordenadaCheiaNaoComAArredondada() {
        ServicoOfertado servico = servicoComLocalizacao("100.00", StatusKyc.APROVADO, -8.1215567, -34.9005432);
        mockarBusca(servico);
        when(geoService.distanciaKm(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(1.5);

        servicoOfertadoService.buscar("eletricista", -8.12, -34.90, 10.0, null);

        verify(geoService).distanciaKm(-8.12, -34.90, -8.1215567, -34.9005432);
    }

    @Test
    void servicoSemCoordenadasVemSemPosicaoESemDistanciaESemSerDescartado() {
        mockarBusca(servico("100.00", StatusKyc.APROVADO));

        List<BuscaServicoResponse> r = servicoOfertadoService
                .buscar("eletricista", -8.12, -34.90, 1.0, null).resultados();

        assertThat(r).hasSize(1);
        assertThat(r.get(0).latitude()).isNull();
        assertThat(r.get(0).longitude()).isNull();
        assertThat(r.get(0).distanciaKm()).isNull();
    }

    // --- Filtros de nota e preço ---

    @Test
    void buscaSemFiltrosNaoDescartaNinguemPorNotaOuPreco() {
        mockarBusca(comNota("100.00", "4.9"), comNota("900.00", "2.0"), servico("50.00", StatusKyc.APROVADO));

        assertThat(servicoOfertadoService.buscar("eletricista", null, null, null, "Recife").resultados()).hasSize(3);
    }

    @Test
    void notaMinimaDescartaAbaixoDoPedido() {
        mockarBusca(comNota("100.00", "4.9"), comNota("100.00", "3.0"));

        List<BuscaServicoResponse> r = servicoOfertadoService
                .buscar("eletricista", null, null, null, "Recife", new BigDecimal("4.5"), null, null, null)
                .resultados();

        assertThat(r).hasSize(1);
        assertThat(r.get(0).notaMediaPrestador()).isEqualByComparingTo("4.9");
    }

    @Test
    void notaMinimaNoLimiteExatoEhIncluida() {
        mockarBusca(comNota("100.00", "4.50"));

        assertThat(servicoOfertadoService
                .buscar("eletricista", null, null, null, "Recife", new BigDecimal("4.5"), null, null, null)
                .resultados()).hasSize(1);
    }

    @Test
    void prestadorSemNotaEhDescartadoQuandoNotaMinimaEhInformada() {
        mockarBusca(servico("100.00", StatusKyc.APROVADO));

        assertThat(servicoOfertadoService
                .buscar("eletricista", null, null, null, "Recife", new BigDecimal("4.0"), null, null, null)
                .resultados()).isEmpty();
    }

    @Test
    void prestadorSemNotaApareceQuandoNotaMinimaEhOmitida() {
        mockarBusca(servico("100.00", StatusKyc.APROVADO));

        assertThat(servicoOfertadoService.buscar("eletricista", null, null, null, "Recife").resultados()).hasSize(1);
    }

    @Test
    void faixaDePrecoFechadaIncluiOsExtremos() {
        mockarBusca(comNota("199.99", "4.0"), comNota("200.00", "4.0"), comNota("500.00", "4.0"),
                comNota("500.01", "4.0"));

        List<BuscaServicoResponse> r = servicoOfertadoService
                .buscar("eletricista", null, null, null, "Recife", null,
                        new BigDecimal("200.00"), new BigDecimal("500.00"), null)
                .resultados();

        assertThat(r).extracting(BuscaServicoResponse::preco)
                .containsExactly(new BigDecimal("200.00"), new BigDecimal("500.00"));
    }

    @Test
    void faixaAbertaEmUmaDasPontasNaoLimitaAOutra() {
        mockarBusca(comNota("100.00", "4.0"), comNota("900.00", "4.0"));

        assertThat(servicoOfertadoService
                .buscar("eletricista", null, null, null, "Recife", null, null, new BigDecimal("200.00"), null)
                .resultados()).hasSize(1);

        mockarBusca(comNota("100.00", "4.0"), comNota("900.00", "4.0"));

        assertThat(servicoOfertadoService
                .buscar("eletricista", null, null, null, "Recife", null, new BigDecimal("200.00"), null, null)
                .resultados()).hasSize(1);
    }

    @Test
    void filtroSemCorrespondenciaDevolveListaVaziaComMensagemNaoErro() {
        mockarBusca(comNota("100.00", "2.0"));

        var resultado = servicoOfertadoService
                .buscar("eletricista", null, null, null, "Recife", new BigDecimal("4.9"), null, null, null);

        assertThat(resultado.resultados()).isEmpty();
        assertThat(resultado.mensagem()).isNotBlank();
    }

    // --- Vitrine de prestadores sem avaliação ---

    @Test
    void apenasSemAvaliacaoDevolveSoQuemNaoTemNota() {
        mockarBusca(comNota("100.00", "4.9"), servico("150.00", StatusKyc.APROVADO));

        List<BuscaServicoResponse> r = servicoOfertadoService
                .buscar("eletricista", null, null, null, "Recife", null, null, null, true)
                .resultados();

        assertThat(r).hasSize(1);
        assertThat(r.get(0).notaMediaPrestador()).isNull();
    }

    @Test
    void apenasSemAvaliacaoCompoeComFaixaDePreco() {
        mockarBusca(servico("100.00", StatusKyc.APROVADO), servico("900.00", StatusKyc.APROVADO));

        assertThat(servicoOfertadoService
                .buscar("eletricista", null, null, null, "Recife", null, null, new BigDecimal("200.00"), true)
                .resultados()).hasSize(1);
    }

    @Test
    void apenasSemAvaliacaoFalsoNaoAlteraABusca() {
        mockarBusca(comNota("100.00", "4.9"), servico("150.00", StatusKyc.APROVADO));

        assertThat(servicoOfertadoService
                .buscar("eletricista", null, null, null, "Recife", null, null, null, false)
                .resultados()).hasSize(2);
    }

    @Test
    void combinarApenasSemAvaliacaoComNotaMinimaEhRecusado() {
        assertThatThrownBy(() -> servicoOfertadoService
                .buscar("eletricista", null, null, null, "Recife", new BigDecimal("4.0"), null, null, true))
                .isInstanceOf(ValidacaoException.class);

        verifyNoInteractions(servicoOfertadoRepository);
    }

    private void mockarBusca(ServicoOfertado... servicos) {
        org.mockito.Mockito.reset(servicoOfertadoRepository);
        lenient().when(servicoOfertadoRepository
                .findByStatusAndCategoriaIgnoreCaseAndLocalizacao_CidadeIgnoreCase(any(), anyString(), anyString()))
                .thenReturn(List.of(servicos));
        lenient().when(servicoOfertadoRepository.findByStatusAndCategoriaIgnoreCase(any(), anyString()))
                .thenReturn(List.of(servicos));
        lenient().when(parametroNegocioService.valor("busca.raio-padrao-km")).thenReturn(new BigDecimal("10"));
    }

    private ServicoOfertado comNota(String preco, String notaMedia) {
        ServicoOfertado s = servico(preco, StatusKyc.APROVADO);
        s.getPrestador().setNota_media(new BigDecimal(notaMedia));
        return s;
    }

    private ServicoOfertado servicoComLocalizacao(String preco, StatusKyc kyc, double lat, double lon) {
        ServicoOfertado s = servico(preco, kyc);
        Localizacao l = new Localizacao();
        l.setCidade("Recife");
        l.setLatitude(lat);
        l.setLongitude(lon);
        s.setLocalizacao(l);
        return s;
    }

    private void mockarServicos(String... precos) {
        List<ServicoOfertado> servicos = java.util.Arrays.stream(precos)
                .map(p -> servico(p, StatusKyc.APROVADO))
                .toList();
        when(servicoOfertadoRepository.findByStatusAndCategoriaIgnoreCase(any(), anyString())).thenReturn(servicos);
    }

    private ServicoOfertado servico(String preco, StatusKyc statusKyc) {
        Prestador prestador = new Prestador();
        prestador.setIdPrestador(1L);
        prestador.setNome("Prestador");
        prestador.setStatusKyc(statusKyc);

        ServicoOfertado servico = new ServicoOfertado();
        servico.setPrestador(prestador);
        servico.setCategoria("eletricista");
        servico.setPreco(new BigDecimal(preco));
        servico.setStatus(StatusServico.ATIVO);
        return servico;
    }
    @Test
    void catalogoDoPrestadorTrazApenasOsAtivos() {
        Prestador aprovado = new Prestador();
        aprovado.setIdPrestador(10L);
        aprovado.setStatusKyc(StatusKyc.APROVADO);
        when(prestadorRepository.findById(10L)).thenReturn(Optional.of(aprovado));
        when(servicoOfertadoRepository.findByPrestador_IdPrestadorAndStatus(10L, StatusServico.ATIVO))
                .thenReturn(List.of(new ServicoOfertado(), new ServicoOfertado()));

        assertThat(servicoOfertadoService.listarAtivosDoPrestador(10L)).hasSize(2);
    }

    @Test
    void catalogoDePrestadorNaoAprovadoVemVazioEmVezDeErro() {
        // RN04: remove da oferta sem revelar a terceiros o estado de verificação de ninguém.
        Prestador pendente = new Prestador();
        pendente.setIdPrestador(11L);
        pendente.setStatusKyc(StatusKyc.PENDENTE);
        when(prestadorRepository.findById(11L)).thenReturn(Optional.of(pendente));

        assertThat(servicoOfertadoService.listarAtivosDoPrestador(11L)).isEmpty();
        verify(servicoOfertadoRepository, org.mockito.Mockito.never())
                .findByPrestador_IdPrestadorAndStatus(any(), any());
    }

    @Test
    void catalogoDePrestadorRejeitadoTambemVemVazio() {
        Prestador rejeitado = new Prestador();
        rejeitado.setIdPrestador(12L);
        rejeitado.setStatusKyc(StatusKyc.REJEITADO);
        when(prestadorRepository.findById(12L)).thenReturn(Optional.of(rejeitado));

        assertThat(servicoOfertadoService.listarAtivosDoPrestador(12L)).isEmpty();
    }

    @Test
    void catalogoDePrestadorInexistenteE404() {
        when(prestadorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicoOfertadoService.listarAtivosDoPrestador(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
