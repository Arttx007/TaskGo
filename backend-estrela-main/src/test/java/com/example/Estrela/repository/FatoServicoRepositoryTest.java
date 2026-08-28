package com.example.Estrela.repository;

import com.example.Estrela.Entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FatoServicoRepositoryTest {

    @Autowired
    private FatoServicoRepository fatoServicoRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @Autowired
    private TempoRepository tempoRepository;

    @Test
    void contaPorStatusEListaPorClienteEPrestador() {
        Cliente cliente = clienteRepository.save(novoCliente());
        Prestador prestador = prestadorRepository.save(novoPrestador());
        Tempo tempo = tempoRepository.save(novoTempo());

        fatoServicoRepository.save(novaSolicitacao(cliente, prestador, tempo, StatusSolicitacao.ACEITO));
        fatoServicoRepository.save(novaSolicitacao(cliente, prestador, tempo, StatusSolicitacao.CANCELADO));
        fatoServicoRepository.save(novaSolicitacao(cliente, prestador, tempo, StatusSolicitacao.CONCLUIDO));
        fatoServicoRepository.save(novaSolicitacao(cliente, prestador, tempo, StatusSolicitacao.CONCLUIDO));

        assertThat(fatoServicoRepository.countByStatus(StatusSolicitacao.CONCLUIDO)).isEqualTo(2);
        assertThat(fatoServicoRepository.countByStatus(StatusSolicitacao.CANCELADO)).isEqualTo(1);

        List<FatoServico> doCliente = fatoServicoRepository.findByCliente_IdCliente(cliente.getIdCliente());
        List<FatoServico> doPrestador = fatoServicoRepository.findByPrestador_IdPrestador(prestador.getIdPrestador());

        assertThat(doCliente).hasSize(4);
        assertThat(doPrestador).hasSize(4);
    }

    @Test
    void avaliacoesPublicasSelecionamApenasAvaliadoComComentario() {
        Cliente cliente = clienteRepository.save(novoCliente());
        Prestador prestador = prestadorRepository.save(novoPrestador());
        Tempo tempo = tempoRepository.save(novoTempo());

        fatoServicoRepository.save(avaliada(cliente, prestador, tempo, 5, "Excelente"));
        fatoServicoRepository.save(avaliada(cliente, prestador, tempo, 4, "   "));
        fatoServicoRepository.save(avaliada(cliente, prestador, tempo, 4, null));
        fatoServicoRepository.save(novaSolicitacao(cliente, prestador, tempo, StatusSolicitacao.CONCLUIDO));

        List<FatoServico> resultado = fatoServicoRepository.buscarAvaliacoesPublicas(
                StatusSolicitacao.AVALIADO, StatusKyc.APROVADO, PageRequest.of(0, 10));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getComentarioAvaliacao()).isEqualTo("Excelente");
    }

    @Test
    void avaliacoesPublicasOmitemPrestadorNaoAprovado() {
        Cliente cliente = clienteRepository.save(novoCliente());
        Tempo tempo = tempoRepository.save(novoTempo());

        Prestador pendente = novoPrestador();
        pendente.setEmail("pendente.repo@teste.com");
        pendente.setStatusKyc(StatusKyc.PENDENTE);
        pendente = prestadorRepository.save(pendente);

        fatoServicoRepository.save(avaliada(cliente, pendente, tempo, 5, "Nao deve aparecer"));

        List<FatoServico> resultado = fatoServicoRepository.buscarAvaliacoesPublicas(
                StatusSolicitacao.AVALIADO, StatusKyc.APROVADO, PageRequest.of(0, 10));

        assertThat(resultado).isEmpty();
    }

    @Test
    void avaliacoesPublicasVemDaMaisRecenteParaAMaisAntigaERespeitamOLimite() {
        Cliente cliente = clienteRepository.save(novoCliente());
        Prestador prestador = prestadorRepository.save(novoPrestador());

        Tempo antigo = tempoRepository.save(tempoEm(LocalDate.of(2026, 1, 10)));
        Tempo recente = tempoRepository.save(tempoEm(LocalDate.of(2026, 8, 20)));
        Tempo meio = tempoRepository.save(tempoEm(LocalDate.of(2026, 5, 5)));

        fatoServicoRepository.save(avaliada(cliente, prestador, antigo, 3, "Antigo"));
        fatoServicoRepository.save(avaliada(cliente, prestador, recente, 5, "Recente"));
        fatoServicoRepository.save(avaliada(cliente, prestador, meio, 4, "Meio"));

        List<FatoServico> todas = fatoServicoRepository.buscarAvaliacoesPublicas(
                StatusSolicitacao.AVALIADO, StatusKyc.APROVADO, PageRequest.of(0, 10));

        assertThat(todas).extracting(FatoServico::getComentarioAvaliacao)
                .containsExactly("Recente", "Meio", "Antigo");

        List<FatoServico> limitada = fatoServicoRepository.buscarAvaliacoesPublicas(
                StatusSolicitacao.AVALIADO, StatusKyc.APROVADO, PageRequest.of(0, 2));

        assertThat(limitada).extracting(FatoServico::getComentarioAvaliacao)
                .containsExactly("Recente", "Meio");
    }

    private FatoServico avaliada(Cliente cliente, Prestador prestador, Tempo tempo, int nota, String comentario) {
        FatoServico servico = novaSolicitacao(cliente, prestador, tempo, StatusSolicitacao.AVALIADO);
        servico.setAvaliacao(nota);
        servico.setComentarioAvaliacao(comentario);
        return servico;
    }

    private Tempo tempoEm(LocalDate data) {
        Tempo tempo = new Tempo();
        tempo.setData(data);
        tempo.setDia(data.getDayOfMonth());
        tempo.setMes(data.getMonthValue());
        tempo.setAno(data.getYear());
        tempo.setDia_semana("segunda-feira");
        return tempo;
    }

    private Cliente novoCliente() {
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente Teste");
        cliente.setEmail("cliente.repo@teste.com");
        cliente.setSenha("hash");
        return cliente;
    }

    private Prestador novoPrestador() {
        Prestador prestador = new Prestador();
        prestador.setNome("Prestador Teste");
        prestador.setEmail("prestador.repo@teste.com");
        prestador.setSenha("hash");
        prestador.setStatusKyc(StatusKyc.APROVADO);
        prestador.setSaldoDisponivel(BigDecimal.ZERO);
        return prestador;
    }

    private Tempo novoTempo() {
        Tempo tempo = new Tempo();
        tempo.setData(LocalDate.now());
        tempo.setDia(LocalDate.now().getDayOfMonth());
        tempo.setMes(LocalDate.now().getMonthValue());
        tempo.setAno(LocalDate.now().getYear());
        tempo.setDia_semana("segunda-feira");
        return tempo;
    }

    private FatoServico novaSolicitacao(Cliente cliente, Prestador prestador, Tempo tempo, StatusSolicitacao status) {
        FatoServico servico = new FatoServico();
        servico.setCliente(cliente);
        servico.setPrestador(prestador);
        servico.setTempo(tempo);
        servico.setValor(new BigDecimal("50.00"));
        servico.setStatus(status);
        return servico;
    }
}
