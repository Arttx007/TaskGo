package com.example.Estrela.Service;

import com.example.Estrela.DTO.FavoritoResponse;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Entity.Favorito;
import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.StatusKyc;
import com.example.Estrela.exception.EstadoInvalidoException;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.FavoritoRepository;
import com.example.Estrela.repository.PrestadorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prestadores que o cliente guardou para voltar a contratar.
 *
 * <p>Favoritar não é contratar: uma solicitação é sempre aberta contra um serviço ofertado,
 * então a listagem informa quantos serviços ativos o prestador tem, e o caminho de
 * contratação passa pelo catálogo dele.
 */
@Service
public class FavoritoService {

    private final FavoritoRepository favoritoRepository;
    private final PrestadorRepository prestadorRepository;
    private final ClienteService clienteService;
    private final ServicoOfertadoService servicoOfertadoService;

    public FavoritoService(FavoritoRepository favoritoRepository,
                           PrestadorRepository prestadorRepository,
                           ClienteService clienteService,
                           ServicoOfertadoService servicoOfertadoService) {
        this.favoritoRepository = favoritoRepository;
        this.prestadorRepository = prestadorRepository;
        this.clienteService = clienteService;
        this.servicoOfertadoService = servicoOfertadoService;
    }

    /**
     * Favoritos do cliente, do marcado mais recentemente para o mais antigo.
     *
     * @param clienteId cliente autenticado
     * @return favoritos do cliente, lista vazia quando ele não marcou ninguém
     */
    public List<FavoritoResponse> listar(Long clienteId) {
        return favoritoRepository.findByCliente_IdClienteOrderByCriadoEmDesc(clienteId).stream()
                .map(this::paraResposta)
                .toList();
    }

    /**
     * Marca um prestador como favorito do cliente.
     *
     * @param clienteId   cliente autenticado
     * @param prestadorId prestador a favoritar
     * @return o favorito criado
     * @throws RecursoNaoEncontradoException se o cliente ou o prestador não existir (HTTP 404)
     * @throws EstadoInvalidoException       se aquele prestador já for favorito (HTTP 409)
     */
    @Transactional
    public FavoritoResponse marcar(Long clienteId, Long prestadorId) {
        Cliente cliente = clienteService.buscarPorId(clienteId);
        Prestador prestador = prestadorRepository.findById(prestadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Prestador não encontrado"));

        if (favoritoRepository.existsByCliente_IdClienteAndPrestador_IdPrestador(clienteId, prestadorId)) {
            throw new EstadoInvalidoException("Este prestador já está nos seus favoritos");
        }

        Favorito favorito = new Favorito();
        favorito.setCliente(cliente);
        favorito.setPrestador(prestador);
        favorito.setCriadoEm(LocalDateTime.now());

        return paraResposta(favoritoRepository.save(favorito));
    }

    /**
     * Remove um favorito do cliente. Não afeta o prestador nem solicitações já abertas com ele.
     *
     * @param clienteId   cliente autenticado
     * @param prestadorId prestador a desfavoritar
     * @throws RecursoNaoEncontradoException se aquele prestador não estiver entre os favoritos (HTTP 404)
     */
    @Transactional
    public void remover(Long clienteId, Long prestadorId) {
        Favorito favorito = favoritoRepository
                .findByCliente_IdClienteAndPrestador_IdPrestador(clienteId, prestadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Este prestador não está nos seus favoritos"));

        favoritoRepository.delete(favorito);
    }

    private FavoritoResponse paraResposta(Favorito favorito) {
        Prestador prestador = favorito.getPrestador();
        boolean aprovado = prestador.getStatusKyc() == StatusKyc.APROVADO;
        int ativos = aprovado
                ? servicoOfertadoService.listarAtivosDoPrestador(prestador.getIdPrestador()).size()
                : 0;

        return new FavoritoResponse(
                prestador.getIdPrestador(),
                prestador.getNome(),
                prestador.getEspecialidade(),
                prestador.getCidade(),
                prestador.getNota_media(),
                ativos,
                aprovado && ativos > 0,
                favorito.getCriadoEm());
    }
}
