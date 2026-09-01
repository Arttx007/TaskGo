package com.example.Estrela.Service;

import com.example.Estrela.DTO.MensagemRequest;
import com.example.Estrela.DTO.MensagemResponse;
import com.example.Estrela.Entity.*;
import com.example.Estrela.exception.AcessoNegadoException;
import com.example.Estrela.exception.EstadoInvalidoException;
import com.example.Estrela.repository.MensagemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Conversa de uma solicitação: restrição às duas partes, envio e marcação de leitura.
 */
@ExtendWith(MockitoExtension.class)
class MensagemServiceTest {

    @Mock private MensagemRepository mensagemRepository;
    @Mock private FatoServicoService fatoServicoService;

    @InjectMocks
    private MensagemService mensagemService;

    private FatoServico solicitacao;
    private Cliente cliente;
    private Prestador prestador;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setIdCliente(1L);
        cliente.setNome("Maria");

        prestador = new Prestador();
        prestador.setIdPrestador(10L);
        prestador.setNome("Carlos");

        solicitacao = new FatoServico();
        solicitacao.setId_servico(99L);
        solicitacao.setCliente(cliente);
        solicitacao.setPrestador(prestador);
        solicitacao.setStatus(StatusSolicitacao.ACEITO);

        lenient().when(mensagemRepository.save(any(Mensagem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void clienteDonoEnviaMensagemQueNasceNaoLida() {
        when(fatoServicoService.buscarEValidarParte(99L, 1L, TipoUsuario.CLIENTE)).thenReturn(solicitacao);

        MensagemResponse resposta = mensagemService.enviar(99L, 1L, TipoUsuario.CLIENTE,
                new MensagemRequest("Estou em casa"));

        assertThat(resposta.conteudo()).isEqualTo("Estou em casa");
        assertThat(resposta.remetenteTipo()).isEqualTo(TipoUsuario.CLIENTE);
        assertThat(resposta.remetenteNome()).isEqualTo("Maria");
        assertThat(resposta.lida()).isFalse();
    }

    @Test
    void mensagemDoPrestadorEAtribuidaAEle() {
        when(fatoServicoService.buscarEValidarParte(99L, 10L, TipoUsuario.PRESTADOR)).thenReturn(solicitacao);

        MensagemResponse resposta = mensagemService.enviar(99L, 10L, TipoUsuario.PRESTADOR,
                new MensagemRequest("Chego em 10 minutos"));

        assertThat(resposta.remetenteTipo()).isEqualTo(TipoUsuario.PRESTADOR);
        assertThat(resposta.remetenteNome()).isEqualTo("Carlos");
    }

    @Test
    void conteudoEArmazenadoSemEspacoNasBordas() {
        when(fatoServicoService.buscarEValidarParte(99L, 1L, TipoUsuario.CLIENTE)).thenReturn(solicitacao);

        assertThat(mensagemService.enviar(99L, 1L, TipoUsuario.CLIENTE,
                new MensagemRequest("  texto  ")).conteudo()).isEqualTo("texto");
    }

    @Test
    void quemNaoParticipaRecebe403() {
        when(fatoServicoService.buscarEValidarParte(99L, 2L, TipoUsuario.CLIENTE))
                .thenThrow(new AcessoNegadoException("Você não participa desta solicitação"));

        assertThatThrownBy(() -> mensagemService.listar(99L, 2L, TipoUsuario.CLIENTE))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> mensagemService.enviar(99L, 2L, TipoUsuario.CLIENTE,
                new MensagemRequest("oi")))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> mensagemService.marcarLidas(99L, 2L, TipoUsuario.CLIENTE))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void conversaVemEmOrdemComOEstadoDeLeitura() {
        when(fatoServicoService.buscarEValidarParte(99L, 1L, TipoUsuario.CLIENTE)).thenReturn(solicitacao);
        when(mensagemRepository.listarConversa(99L)).thenReturn(List.of(
                mensagem(1L, TipoUsuario.CLIENTE, "primeira", LocalDateTime.now()),
                mensagem(2L, TipoUsuario.PRESTADOR, "segunda", LocalDateTime.now())));

        List<MensagemResponse> conversa = mensagemService.listar(99L, 1L, TipoUsuario.CLIENTE);

        assertThat(conversa).extracting(MensagemResponse::conteudo).containsExactly("primeira", "segunda");
        assertThat(conversa.get(1).lida()).isTrue();
    }

    @Test
    void conversaSemMensagemVemVazia() {
        when(fatoServicoService.buscarEValidarParte(99L, 1L, TipoUsuario.CLIENTE)).thenReturn(solicitacao);
        when(mensagemRepository.listarConversa(99L)).thenReturn(List.of());

        assertThat(mensagemService.listar(99L, 1L, TipoUsuario.CLIENTE)).isEmpty();
    }

    @Test
    void solicitacaoEncerradaNaoAceitaMensagemNova() {
        when(fatoServicoService.buscarEValidarParte(anyLong(), anyLong(), any())).thenReturn(solicitacao);

        for (StatusSolicitacao encerrado : List.of(StatusSolicitacao.RECUSADO,
                StatusSolicitacao.CANCELADO, StatusSolicitacao.AVALIADO)) {
            solicitacao.setStatus(encerrado);
            assertThatThrownBy(() -> mensagemService.enviar(99L, 1L, TipoUsuario.CLIENTE,
                    new MensagemRequest("oi")))
                    .isInstanceOf(EstadoInvalidoException.class);
        }
        verify(mensagemRepository, never()).save(any(Mensagem.class));
    }

    @Test
    void conversaDeSolicitacaoAvaliadaContinuaLegivel() {
        solicitacao.setStatus(StatusSolicitacao.AVALIADO);
        when(fatoServicoService.buscarEValidarParte(99L, 1L, TipoUsuario.CLIENTE)).thenReturn(solicitacao);
        when(mensagemRepository.listarConversa(99L)).thenReturn(List.of(
                mensagem(1L, TipoUsuario.CLIENTE, "combinado", LocalDateTime.now())));

        assertThat(mensagemService.listar(99L, 1L, TipoUsuario.CLIENTE)).hasSize(1);
    }

    @Test
    void marcarLidasAlcancaApenasAsDaOutraParte() {
        when(fatoServicoService.buscarEValidarParte(99L, 1L, TipoUsuario.CLIENTE)).thenReturn(solicitacao);
        Mensagem doPrestador = mensagem(1L, TipoUsuario.PRESTADOR, "do prestador", null);
        when(mensagemRepository.listarNaoLidasPara(99L, TipoUsuario.CLIENTE))
                .thenReturn(List.of(doPrestador));

        mensagemService.marcarLidas(99L, 1L, TipoUsuario.CLIENTE);

        assertThat(doPrestador.getLidaEm()).isNotNull();
        verify(mensagemRepository).listarNaoLidasPara(99L, TipoUsuario.CLIENTE);
    }

    @Test
    void marcarLidasSemNadaPendenteNaoEstoura() {
        when(fatoServicoService.buscarEValidarParte(99L, 1L, TipoUsuario.CLIENTE)).thenReturn(solicitacao);
        when(mensagemRepository.listarNaoLidasPara(99L, TipoUsuario.CLIENTE)).thenReturn(List.of());

        mensagemService.marcarLidas(99L, 1L, TipoUsuario.CLIENTE);

        verify(mensagemRepository, never()).save(any(Mensagem.class));
    }

    private Mensagem mensagem(Long id, TipoUsuario tipo, String conteudo, LocalDateTime lidaEm) {
        Mensagem m = new Mensagem();
        m.setId(id);
        m.setFatoServico(solicitacao);
        m.setRemetenteTipo(tipo);
        m.setRemetenteId(tipo == TipoUsuario.CLIENTE ? 1L : 10L);
        m.setConteudo(conteudo);
        m.setCriadoEm(LocalDateTime.now());
        m.setLidaEm(lidaEm);
        return m;
    }
}
