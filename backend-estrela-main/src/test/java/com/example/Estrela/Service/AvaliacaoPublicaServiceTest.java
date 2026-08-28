package com.example.Estrela.Service;

import com.example.Estrela.DTO.AvaliacaoPublicaResponse;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.Localizacao;
import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.ServicoOfertado;
import com.example.Estrela.Entity.StatusKyc;
import com.example.Estrela.Entity.StatusSolicitacao;
import com.example.Estrela.Entity.Tempo;
import com.example.Estrela.repository.FatoServicoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Leitura pública de avaliações: recorte de privacidade e política de limite.
 *
 * <p>A seleção em si (status, comentário não vazio, KYC do prestador, ordenação) é responsabilidade
 * da consulta, coberta em {@code FatoServicoRepositoryTest}. Aqui se testa o que o service decide.
 */
@ExtendWith(MockitoExtension.class)
class AvaliacaoPublicaServiceTest {

    @Mock
    private FatoServicoRepository fatoServicoRepository;

    @InjectMocks
    private AvaliacaoPublicaService avaliacaoPublicaService;

    @Test
    void deveExporApenasOPrimeiroNomeDeQuemAvaliou() {
        mockarRetorno(avaliacao(5, "Excelente atendimento", "Ana Carolina Silva Souza"));

        List<AvaliacaoPublicaResponse> resultado = avaliacaoPublicaService.listarRecentes(null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).clientePrimeiroNome()).isEqualTo("Ana");
    }

    @Test
    void nomeSemSobrenomeEhDevolvidoInteiro() {
        mockarRetorno(avaliacao(4, "Bom", "Arthur"));

        assertThat(avaliacaoPublicaService.listarRecentes(null).get(0).clientePrimeiroNome()).isEqualTo("Arthur");
    }

    @Test
    void deveDevolverNotaComentarioCategoriaCidadeEData() {
        mockarRetorno(avaliacao(5, "Chegou no horário", "Maria Souza"));

        AvaliacaoPublicaResponse r = avaliacaoPublicaService.listarRecentes(null).get(0);

        assertThat(r.nota()).isEqualTo(5);
        assertThat(r.comentario()).isEqualTo("Chegou no horário");
        assertThat(r.categoria()).isEqualTo("eletricista");
        assertThat(r.cidade()).isEqualTo("Recife");
        assertThat(r.data()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void limiteAusenteAplicaOPadrao() {
        mockarRetorno();

        avaliacaoPublicaService.listarRecentes(null);

        assertThat(capturarPageable().getPageSize()).isEqualTo(AvaliacaoPublicaService.LIMITE_PADRAO);
    }

    @Test
    void limiteAcimaDoTetoEhTruncadoEmVezDeRecusado() {
        mockarRetorno();

        avaliacaoPublicaService.listarRecentes(500);

        assertThat(capturarPageable().getPageSize()).isEqualTo(AvaliacaoPublicaService.LIMITE_MAXIMO);
    }

    @Test
    void limiteNaoPositivoAplicaOPadrao() {
        mockarRetorno();

        avaliacaoPublicaService.listarRecentes(0);

        assertThat(capturarPageable().getPageSize()).isEqualTo(AvaliacaoPublicaService.LIMITE_PADRAO);
    }

    @Test
    void limiteDentroDoTetoEhRespeitado() {
        mockarRetorno();

        avaliacaoPublicaService.listarRecentes(3);

        assertThat(capturarPageable().getPageSize()).isEqualTo(3);
    }

    @Test
    void semAvaliacaoDevolveListaVazia() {
        mockarRetorno();

        assertThat(avaliacaoPublicaService.listarRecentes(null)).isEmpty();
    }

    @Test
    void deveConsultarSomenteAvaliadoDePrestadorAprovado() {
        mockarRetorno();

        avaliacaoPublicaService.listarRecentes(null);

        verify(fatoServicoRepository).buscarAvaliacoesPublicas(
                org.mockito.ArgumentMatchers.eq(StatusSolicitacao.AVALIADO),
                org.mockito.ArgumentMatchers.eq(StatusKyc.APROVADO),
                any(Pageable.class));
    }

    private Pageable capturarPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(fatoServicoRepository).buscarAvaliacoesPublicas(any(), any(), captor.capture());
        return captor.getValue();
    }

    private void mockarRetorno(FatoServico... fatos) {
        when(fatoServicoRepository.buscarAvaliacoesPublicas(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(fatos));
    }

    private FatoServico avaliacao(int nota, String comentario, String nomeCliente) {
        Cliente cliente = new Cliente();
        cliente.setNome(nomeCliente);

        Prestador prestador = new Prestador();
        prestador.setStatusKyc(StatusKyc.APROVADO);

        ServicoOfertado servico = new ServicoOfertado();
        servico.setCategoria("eletricista");

        Localizacao localizacao = new Localizacao();
        localizacao.setCidade("Recife");

        Tempo tempo = new Tempo();
        tempo.setData(LocalDate.of(2026, 8, 20));

        FatoServico fato = new FatoServico();
        fato.setCliente(cliente);
        fato.setPrestador(prestador);
        fato.setServicoOfertado(servico);
        fato.setLocalizacao(localizacao);
        fato.setTempo(tempo);
        fato.setStatus(StatusSolicitacao.AVALIADO);
        fato.setAvaliacao(nota);
        fato.setComentarioAvaliacao(comentario);
        return fato;
    }
}
