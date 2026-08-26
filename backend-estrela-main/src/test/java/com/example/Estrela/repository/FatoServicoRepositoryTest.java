package com.example.Estrela.repository;

import com.example.Estrela.Entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

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
