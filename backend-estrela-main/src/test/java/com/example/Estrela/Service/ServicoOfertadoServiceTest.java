package com.example.Estrela.Service;

import com.example.Estrela.DTO.CategoriaDisponivelResponse;
import com.example.Estrela.DTO.EstimativaPrecoResponse;
import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.ServicoOfertado;
import com.example.Estrela.Entity.StatusKyc;
import com.example.Estrela.Entity.StatusServico;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
}
