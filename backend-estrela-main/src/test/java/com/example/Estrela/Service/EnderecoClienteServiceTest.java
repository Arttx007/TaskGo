package com.example.Estrela.Service;

import com.example.Estrela.DTO.EnderecoClienteRequest;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Entity.EnderecoCliente;
import com.example.Estrela.exception.AcessoNegadoException;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.EnderecoClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Endereços de atendimento: invariante do padrão único e remoção lógica.
 *
 * <p>Usa um repositório em memória em vez de stubs por chamada porque as regras aqui são
 * sobre o <i>conjunto</i> de endereços de uma conta — quantos estão padrão, o que sobra
 * depois de remover — e verificar isso com {@code verify} por chamada testaria a
 * implementação em vez do comportamento.
 */
@ExtendWith(MockitoExtension.class)
class EnderecoClienteServiceTest {

    @Mock private EnderecoClienteRepository enderecoRepository;
    @Mock private ClienteService clienteService;

    private EnderecoClienteService service;
    private final List<EnderecoCliente> banco = new ArrayList<>();
    private final AtomicLong sequencia = new AtomicLong(1);
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setIdCliente(1L);

        service = new EnderecoClienteService(enderecoRepository, clienteService);

        lenient().when(clienteService.buscarPorId(1L)).thenReturn(cliente);

        lenient().when(enderecoRepository.save(any(EnderecoCliente.class))).thenAnswer(inv -> {
            EnderecoCliente e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(sequencia.getAndIncrement());
                banco.add(e);
            }
            return e;
        });
        lenient().when(enderecoRepository.findByCliente_IdCliente(anyLong())).thenAnswer(inv ->
                banco.stream().filter(e -> e.getCliente().getIdCliente().equals(inv.getArgument(0))).toList());
        lenient().when(enderecoRepository.findByCliente_IdClienteAndAtivoTrueOrderByIdAsc(anyLong()))
                .thenAnswer(inv -> banco.stream()
                        .filter(e -> e.getCliente().getIdCliente().equals(inv.getArgument(0)))
                        .filter(e -> Boolean.TRUE.equals(e.getAtivo()))
                        .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                        .toList());
        lenient().when(enderecoRepository.findById(anyLong())).thenAnswer(inv ->
                banco.stream().filter(e -> e.getId().equals(inv.getArgument(0))).findFirst());
    }

    @Test
    void primeiroEnderecoNascePadraoMesmoSemPedir() {
        EnderecoCliente criado = service.criar(1L, pedido("Casa", false));

        assertThat(criado.getPadrao()).isTrue();
    }

    @Test
    void marcarOutroComoPadraoDeixaExatamenteUmPadrao() {
        service.criar(1L, pedido("Casa", false));
        service.criar(1L, pedido("Trabalho", false));
        EnderecoCliente terceiro = service.criar(1L, pedido("Praia", true));

        assertThat(banco.stream().filter(e -> Boolean.TRUE.equals(e.getPadrao())).toList())
                .hasSize(1)
                .first()
                .extracting(EnderecoCliente::getId)
                .isEqualTo(terceiro.getId());
    }

    @Test
    void atualizarMarcandoPadraoTambemDesmarcaOAnterior() {
        EnderecoCliente casa = service.criar(1L, pedido("Casa", false));
        EnderecoCliente trabalho = service.criar(1L, pedido("Trabalho", false));

        service.atualizar(1L, trabalho.getId(), pedido("Trabalho", true));

        assertThat(casa.getPadrao()).isFalse();
        assertThat(trabalho.getPadrao()).isTrue();
    }

    @Test
    void remocaoELogicaEEnderecoSaiDaLista() {
        EnderecoCliente casa = service.criar(1L, pedido("Casa", false));
        service.criar(1L, pedido("Trabalho", false));

        service.remover(1L, casa.getId());

        assertThat(casa.getAtivo()).isFalse();
        assertThat(service.listar(1L)).extracting(EnderecoCliente::getApelido).containsExactly("Trabalho");
        assertThat(banco).hasSize(2);
    }

    @Test
    void enderecoRemovidoContinuaResolvendoParaOHistorico() {
        EnderecoCliente casa = service.criar(1L, pedido("Casa", false));
        service.remover(1L, casa.getId());

        // Uma solicitação antiga referencia este endereço pela FK; ele tem de continuar legível.
        assertThat(service.buscarEValidarDono(1L, casa.getId())).isNotNull();
    }

    @Test
    void removerOPadraoPromoveOMaisAntigoRestante() {
        EnderecoCliente casa = service.criar(1L, pedido("Casa", false));
        EnderecoCliente trabalho = service.criar(1L, pedido("Trabalho", false));

        service.remover(1L, casa.getId());

        assertThat(trabalho.getPadrao()).isTrue();
        assertThat(service.buscarPadrao(1L)).isNull(); // stub do findBy...PadraoTrue não é usado aqui
    }

    @Test
    void enderecoDeOutraContaResponde403() {
        Cliente outro = new Cliente();
        outro.setIdCliente(2L);
        when(clienteService.buscarPorId(2L)).thenReturn(outro);
        EnderecoCliente doOutro = service.criar(2L, pedido("Casa do outro", false));

        assertThatThrownBy(() -> service.buscarEValidarDono(1L, doOutro.getId()))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.remover(1L, doOutro.getId()))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.atualizar(1L, doOutro.getId(), pedido("Invadido", false)))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void enderecoInexistenteResponde404() {
        assertThatThrownBy(() -> service.buscarEValidarDono(1L, 999L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void contaSemEnderecoDevolveListaVazia() {
        assertThat(service.listar(1L)).isEmpty();
    }

    @Test
    void ufEGravadaEmMaiusculas() {
        EnderecoCliente criado = service.criar(1L, new EnderecoClienteRequest(
                "Casa", "50000-000", "Rua A", "10", null, "Centro", "Recife", "pe",
                null, null, false));

        assertThat(criado.getUf()).isEqualTo("PE");
    }

    private EnderecoClienteRequest pedido(String apelido, boolean padrao) {
        return new EnderecoClienteRequest(apelido, "50000-000", "Rua A", "10", null,
                "Centro", "Recife", "PE", null, null, padrao);
    }
}
