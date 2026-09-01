package com.example.Estrela.Service;

import com.example.Estrela.DTO.FavoritoResponse;
import com.example.Estrela.Entity.*;
import com.example.Estrela.exception.EstadoInvalidoException;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.FavoritoRepository;
import com.example.Estrela.repository.PrestadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Favoritos do cliente: marcação, listagem com disponibilidade, e remoção.
 */
@ExtendWith(MockitoExtension.class)
class FavoritoServiceTest {

    @Mock private FavoritoRepository favoritoRepository;
    @Mock private PrestadorRepository prestadorRepository;
    @Mock private ClienteService clienteService;
    @Mock private ServicoOfertadoService servicoOfertadoService;

    @InjectMocks
    private FavoritoService favoritoService;

    private Cliente cliente;
    private Prestador prestador;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setIdCliente(1L);

        prestador = new Prestador();
        prestador.setIdPrestador(10L);
        prestador.setNome("Prestador Teste");
        prestador.setEspecialidade("Eletricista");
        prestador.setCidade("Recife");
        prestador.setNota_media(new BigDecimal("4.80"));
        prestador.setStatusKyc(StatusKyc.APROVADO);

        lenient().when(clienteService.buscarPorId(1L)).thenReturn(cliente);
        lenient().when(prestadorRepository.findById(10L)).thenReturn(Optional.of(prestador));
        lenient().when(favoritoRepository.save(any(Favorito.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(servicoOfertadoService.listarAtivosDoPrestador(10L))
                .thenReturn(List.of(new ServicoOfertado(), new ServicoOfertado()));
    }

    @Test
    void marcaPrestadorComoFavorito() {
        when(favoritoRepository.existsByCliente_IdClienteAndPrestador_IdPrestador(1L, 10L)).thenReturn(false);

        FavoritoResponse resposta = favoritoService.marcar(1L, 10L);

        assertThat(resposta.prestadorId()).isEqualTo(10L);
        assertThat(resposta.nome()).isEqualTo("Prestador Teste");
        assertThat(resposta.notaMedia()).isEqualByComparingTo("4.80");
        assertThat(resposta.servicosAtivos()).isEqualTo(2);
        assertThat(resposta.disponivel()).isTrue();
    }

    @Test
    void prestadorJaFavoritoERecusado() {
        when(favoritoRepository.existsByCliente_IdClienteAndPrestador_IdPrestador(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> favoritoService.marcar(1L, 10L))
                .isInstanceOf(EstadoInvalidoException.class);

        verify(favoritoRepository, never()).save(any(Favorito.class));
    }

    @Test
    void prestadorInexistenteE404() {
        when(prestadorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoritoService.marcar(1L, 99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void favoritoDePrestadorNaoAprovadoContinuaNaListaSinalizado() {
        prestador.setStatusKyc(StatusKyc.REJEITADO);
        when(favoritoRepository.findByCliente_IdClienteOrderByCriadoEmDesc(1L))
                .thenReturn(List.of(favorito(cliente, prestador)));

        List<FavoritoResponse> lista = favoritoService.listar(1L);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).disponivel()).isFalse();
        assertThat(lista.get(0).servicosAtivos()).isZero();
    }

    @Test
    void favoritoAprovadoSemServicoAtivoNaoEContratavel() {
        when(servicoOfertadoService.listarAtivosDoPrestador(10L)).thenReturn(List.of());
        when(favoritoRepository.findByCliente_IdClienteOrderByCriadoEmDesc(1L))
                .thenReturn(List.of(favorito(cliente, prestador)));

        FavoritoResponse resposta = favoritoService.listar(1L).get(0);

        assertThat(resposta.disponivel()).isFalse();
        assertThat(resposta.servicosAtivos()).isZero();
    }

    @Test
    void contaSemFavoritoDevolveListaVazia() {
        when(favoritoRepository.findByCliente_IdClienteOrderByCriadoEmDesc(1L)).thenReturn(List.of());

        assertThat(favoritoService.listar(1L)).isEmpty();
    }

    @Test
    void removeFavoritoExistente() {
        Favorito favorito = favorito(cliente, prestador);
        when(favoritoRepository.findByCliente_IdClienteAndPrestador_IdPrestador(1L, 10L))
                .thenReturn(Optional.of(favorito));

        favoritoService.remover(1L, 10L);

        verify(favoritoRepository).delete(favorito);
    }

    @Test
    void removerQuemNaoEFavoritoE404() {
        when(favoritoRepository.findByCliente_IdClienteAndPrestador_IdPrestador(anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoritoService.remover(1L, 10L))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(favoritoRepository, never()).delete(any(Favorito.class));
    }

    @Test
    void prestadorNaoAvaliadoVemComNotaNula() {
        prestador.setNota_media(null);
        when(favoritoRepository.findByCliente_IdClienteOrderByCriadoEmDesc(1L))
                .thenReturn(List.of(favorito(cliente, prestador)));

        assertThat(favoritoService.listar(1L).get(0).notaMedia()).isNull();
    }

    private Favorito favorito(Cliente cliente, Prestador prestador) {
        Favorito favorito = new Favorito();
        favorito.setId(1L);
        favorito.setCliente(cliente);
        favorito.setPrestador(prestador);
        favorito.setCriadoEm(LocalDateTime.now());
        return favorito;
    }
}
