package com.example.Estrela.repository;

import com.example.Estrela.Entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ServicoOfertadoRepositoryTest {

    @Autowired
    private ServicoOfertadoRepository servicoOfertadoRepository;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @Autowired
    private LocalizacaoRepository localizacaoRepository;

    @Test
    void buscaPorStatusECategoriaIgnorandoCaixa() {
        Prestador prestador = prestadorRepository.save(novoPrestador("eletricista1@teste.com"));

        servicoOfertadoRepository.save(novoServico(prestador, "Eletricista", StatusServico.ATIVO));
        servicoOfertadoRepository.save(novoServico(prestador, "Eletricista", StatusServico.INATIVO));
        servicoOfertadoRepository.save(novoServico(prestador, "Encanador", StatusServico.ATIVO));

        List<ServicoOfertado> resultado = servicoOfertadoRepository
                .findByStatusAndCategoriaIgnoreCase(StatusServico.ATIVO, "eletricista");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getCategoria()).isEqualTo("Eletricista");
    }

    @Test
    void buscaPorCidadeComoFallbackSemCoordenadas() {
        Prestador prestador = prestadorRepository.save(novoPrestador("pintor1@teste.com"));
        Localizacao localizacao = localizacaoRepository.save(novaLocalizacao("Recife"));

        ServicoOfertado servico = novoServico(prestador, "Pintor", StatusServico.ATIVO);
        servico.setLocalizacao(localizacao);
        servicoOfertadoRepository.save(servico);

        List<ServicoOfertado> resultado = servicoOfertadoRepository
                .findByStatusAndCategoriaIgnoreCaseAndLocalizacao_CidadeIgnoreCase(StatusServico.ATIVO, "pintor", "recife");

        assertThat(resultado).hasSize(1);
    }

    @Test
    void naoPermiteDoisPrestadoresComMesmoEmail() {
        prestadorRepository.saveAndFlush(novoPrestador("duplicado@teste.com"));

        assertThatThrownBy(() -> prestadorRepository.saveAndFlush(novoPrestador("duplicado@teste.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Prestador novoPrestador(String email) {
        Prestador prestador = new Prestador();
        prestador.setNome("Prestador " + email);
        prestador.setEmail(email);
        prestador.setSenha("hash");
        prestador.setStatusKyc(StatusKyc.APROVADO);
        prestador.setSaldoDisponivel(BigDecimal.ZERO);
        return prestador;
    }

    private Localizacao novaLocalizacao(String cidade) {
        Localizacao localizacao = new Localizacao();
        localizacao.setCidade(cidade);
        return localizacao;
    }

    private ServicoOfertado novoServico(Prestador prestador, String categoria, StatusServico status) {
        ServicoOfertado servico = new ServicoOfertado();
        servico.setPrestador(prestador);
        servico.setCategoria(categoria);
        servico.setPreco(new BigDecimal("80.00"));
        servico.setStatus(status);
        return servico;
    }
}
