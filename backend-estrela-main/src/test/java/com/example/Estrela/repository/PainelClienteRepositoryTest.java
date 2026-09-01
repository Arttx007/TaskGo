package com.example.Estrela.repository;

import com.example.Estrela.Entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre os métodos de consulta dos repositórios criados para o painel do cliente:
 * endereços, favoritos e mensagens.
 */
@DataJpaTest
class PainelClienteRepositoryTest {

    @Autowired
    private EnderecoClienteRepository enderecoClienteRepository;
    @Autowired
    private FavoritoRepository favoritoRepository;
    @Autowired
    private MensagemRepository mensagemRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private PrestadorRepository prestadorRepository;
    @Autowired
    private TempoRepository tempoRepository;
    @Autowired
    private FatoServicoRepository fatoServicoRepository;

    @Test
    void enderecosAtivosDoClienteExcluemRemovidosEDeOutraConta() {
        Cliente dono = clienteRepository.save(novoCliente("dono.end@teste.com"));
        Cliente outro = clienteRepository.save(novoCliente("outro.end@teste.com"));

        enderecoClienteRepository.save(novoEndereco(dono, "Casa", true, true));
        enderecoClienteRepository.save(novoEndereco(dono, "Trabalho", false, true));
        enderecoClienteRepository.save(novoEndereco(dono, "Antigo", false, false));
        enderecoClienteRepository.save(novoEndereco(outro, "Casa do outro", true, true));

        List<EnderecoCliente> ativos =
                enderecoClienteRepository.findByCliente_IdClienteAndAtivoTrueOrderByIdAsc(dono.getIdCliente());

        assertThat(ativos).extracting(EnderecoCliente::getApelido).containsExactly("Casa", "Trabalho");
        assertThat(enderecoClienteRepository.findByCliente_IdCliente(dono.getIdCliente())).hasSize(3);
    }

    @Test
    void enderecoPadraoDoClienteEUnicoEIgnoraRemovido() {
        Cliente cliente = clienteRepository.save(novoCliente("padrao@teste.com"));
        enderecoClienteRepository.save(novoEndereco(cliente, "Casa", true, true));
        enderecoClienteRepository.save(novoEndereco(cliente, "Trabalho", false, true));

        assertThat(enderecoClienteRepository
                .findByCliente_IdClienteAndPadraoTrueAndAtivoTrue(cliente.getIdCliente()))
                .isPresent()
                .get()
                .extracting(EnderecoCliente::getApelido)
                .isEqualTo("Casa");
    }

    @Test
    void enderecoPadraoRemovidoLogicamenteNaoEDevolvido() {
        Cliente cliente = clienteRepository.save(novoCliente("padrao.removido@teste.com"));
        enderecoClienteRepository.save(novoEndereco(cliente, "Casa", true, false));

        assertThat(enderecoClienteRepository
                .findByCliente_IdClienteAndPadraoTrueAndAtivoTrue(cliente.getIdCliente()))
                .isEmpty();
    }

    @Test
    void favoritosVemDoMaisRecenteParaOMaisAntigoESoDaPropriaConta() {
        Cliente dono = clienteRepository.save(novoCliente("dono.fav@teste.com"));
        Cliente outro = clienteRepository.save(novoCliente("outro.fav@teste.com"));
        Prestador antigo = prestadorRepository.save(novoPrestador("antigo@teste.com"));
        Prestador recente = prestadorRepository.save(novoPrestador("recente@teste.com"));

        favoritoRepository.save(novoFavorito(dono, antigo, LocalDateTime.now().minusDays(2)));
        favoritoRepository.save(novoFavorito(dono, recente, LocalDateTime.now()));
        favoritoRepository.save(novoFavorito(outro, antigo, LocalDateTime.now()));

        List<Favorito> doDono = favoritoRepository.findByCliente_IdClienteOrderByCriadoEmDesc(dono.getIdCliente());

        assertThat(doDono).hasSize(2);
        assertThat(doDono.get(0).getPrestador().getIdPrestador()).isEqualTo(recente.getIdPrestador());
    }

    @Test
    void favoritoEncontradoPeloParClientePrestador() {
        Cliente cliente = clienteRepository.save(novoCliente("par.fav@teste.com"));
        Prestador prestador = prestadorRepository.save(novoPrestador("par.prest@teste.com"));
        favoritoRepository.save(novoFavorito(cliente, prestador, LocalDateTime.now()));

        assertThat(favoritoRepository.existsByCliente_IdClienteAndPrestador_IdPrestador(
                cliente.getIdCliente(), prestador.getIdPrestador())).isTrue();
        assertThat(favoritoRepository.findByCliente_IdClienteAndPrestador_IdPrestador(
                cliente.getIdCliente(), prestador.getIdPrestador())).isPresent();
        assertThat(favoritoRepository.existsByCliente_IdClienteAndPrestador_IdPrestador(
                cliente.getIdCliente(), 9999L)).isFalse();
    }

    @Test
    void conversaVemEmOrdemCronologicaESoDaPropriaSolicitacao() {
        Cliente cliente = clienteRepository.save(novoCliente("chat@teste.com"));
        Prestador prestador = prestadorRepository.save(novoPrestador("chat.prest@teste.com"));
        Tempo tempo = tempoRepository.save(novoTempo());
        FatoServico solicitacao = fatoServicoRepository.save(novaSolicitacao(cliente, prestador, tempo));
        FatoServico outra = fatoServicoRepository.save(novaSolicitacao(cliente, prestador, tempo));

        mensagemRepository.save(novaMensagem(solicitacao, TipoUsuario.CLIENTE, cliente.getIdCliente(),
                "primeira", LocalDateTime.now().minusMinutes(10), null));
        mensagemRepository.save(novaMensagem(solicitacao, TipoUsuario.PRESTADOR, prestador.getIdPrestador(),
                "segunda", LocalDateTime.now().minusMinutes(5), null));
        mensagemRepository.save(novaMensagem(outra, TipoUsuario.CLIENTE, cliente.getIdCliente(),
                "de outra solicitacao", LocalDateTime.now(), null));

        List<Mensagem> conversa = mensagemRepository.listarConversa(solicitacao.getId_servico());

        assertThat(conversa).extracting(Mensagem::getConteudo).containsExactly("primeira", "segunda");
    }

    @Test
    void conversaSemMensagemVemVazia() {
        Cliente cliente = clienteRepository.save(novoCliente("chat.vazio@teste.com"));
        Prestador prestador = prestadorRepository.save(novoPrestador("chat.vazio.p@teste.com"));
        Tempo tempo = tempoRepository.save(novoTempo());
        FatoServico solicitacao = fatoServicoRepository.save(novaSolicitacao(cliente, prestador, tempo));

        assertThat(mensagemRepository.listarConversa(solicitacao.getId_servico())).isEmpty();
    }

    @Test
    void naoLidasParaOClienteExcluemAsProprias() {
        Cliente cliente = clienteRepository.save(novoCliente("naolidas@teste.com"));
        Prestador prestador = prestadorRepository.save(novoPrestador("naolidas.p@teste.com"));
        Tempo tempo = tempoRepository.save(novoTempo());
        FatoServico solicitacao = fatoServicoRepository.save(novaSolicitacao(cliente, prestador, tempo));

        mensagemRepository.save(novaMensagem(solicitacao, TipoUsuario.PRESTADOR, prestador.getIdPrestador(),
                "do prestador nao lida", LocalDateTime.now(), null));
        mensagemRepository.save(novaMensagem(solicitacao, TipoUsuario.PRESTADOR, prestador.getIdPrestador(),
                "do prestador ja lida", LocalDateTime.now(), LocalDateTime.now()));
        mensagemRepository.save(novaMensagem(solicitacao, TipoUsuario.CLIENTE, cliente.getIdCliente(),
                "do proprio cliente", LocalDateTime.now(), null));

        List<Mensagem> pendentes = mensagemRepository.listarNaoLidasPara(
                solicitacao.getId_servico(), TipoUsuario.CLIENTE);

        assertThat(pendentes).extracting(Mensagem::getConteudo).containsExactly("do prestador nao lida");
        assertThat(mensagemRepository.contarNaoLidasDoCliente(cliente.getIdCliente())).isEqualTo(1);
    }

    @Test
    void contagemDeNaoLidasNaoAlcancaOutroCliente() {
        Cliente dono = clienteRepository.save(novoCliente("dono.nl@teste.com"));
        Cliente outro = clienteRepository.save(novoCliente("outro.nl@teste.com"));
        Prestador prestador = prestadorRepository.save(novoPrestador("nl.p@teste.com"));
        Tempo tempo = tempoRepository.save(novoTempo());
        FatoServico doOutro = fatoServicoRepository.save(novaSolicitacao(outro, prestador, tempo));

        mensagemRepository.save(novaMensagem(doOutro, TipoUsuario.PRESTADOR, prestador.getIdPrestador(),
                "para o outro", LocalDateTime.now(), null));

        assertThat(mensagemRepository.contarNaoLidasDoCliente(dono.getIdCliente())).isZero();
        assertThat(mensagemRepository.contarNaoLidasDoCliente(outro.getIdCliente())).isEqualTo(1);
    }

    private Cliente novoCliente(String email) {
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente Teste");
        cliente.setEmail(email);
        cliente.setSenha("hash");
        return cliente;
    }

    private Prestador novoPrestador(String email) {
        Prestador prestador = new Prestador();
        prestador.setNome("Prestador Teste");
        prestador.setEmail(email);
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

    private FatoServico novaSolicitacao(Cliente cliente, Prestador prestador, Tempo tempo) {
        FatoServico servico = new FatoServico();
        servico.setCliente(cliente);
        servico.setPrestador(prestador);
        servico.setTempo(tempo);
        servico.setValor(new BigDecimal("120.00"));
        servico.setStatus(StatusSolicitacao.ACEITO);
        return servico;
    }

    private EnderecoCliente novoEndereco(Cliente cliente, String apelido, boolean padrao, boolean ativo) {
        EnderecoCliente endereco = new EnderecoCliente();
        endereco.setCliente(cliente);
        endereco.setApelido(apelido);
        endereco.setCep("50000-000");
        endereco.setRua("Rua Teste");
        endereco.setNumero("100");
        endereco.setBairro("Centro");
        endereco.setCidade("Recife");
        endereco.setUf("PE");
        endereco.setPadrao(padrao);
        endereco.setAtivo(ativo);
        return endereco;
    }

    private Favorito novoFavorito(Cliente cliente, Prestador prestador, LocalDateTime criadoEm) {
        Favorito favorito = new Favorito();
        favorito.setCliente(cliente);
        favorito.setPrestador(prestador);
        favorito.setCriadoEm(criadoEm);
        return favorito;
    }

    private Mensagem novaMensagem(FatoServico solicitacao, TipoUsuario tipo, Long remetenteId,
                                  String conteudo, LocalDateTime criadoEm, LocalDateTime lidaEm) {
        Mensagem mensagem = new Mensagem();
        mensagem.setFatoServico(solicitacao);
        mensagem.setRemetenteTipo(tipo);
        mensagem.setRemetenteId(remetenteId);
        mensagem.setConteudo(conteudo);
        mensagem.setCriadoEm(criadoEm);
        mensagem.setLidaEm(lidaEm);
        return mensagem;
    }
}
