package com.example.Estrela.repository;

import com.example.Estrela.Entity.Favorito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Acesso aos prestadores favoritados pelos clientes.
 */
@Repository
public interface FavoritoRepository extends JpaRepository<Favorito, Long> {

    /** Favoritos do cliente, do marcado mais recentemente para o mais antigo. */
    List<Favorito> findByCliente_IdClienteOrderByCriadoEmDesc(Long clienteId);

    /** O favorito daquele par, se existir — base da recusa de duplicado. */
    Optional<Favorito> findByCliente_IdClienteAndPrestador_IdPrestador(Long clienteId, Long prestadorId);

    boolean existsByCliente_IdClienteAndPrestador_IdPrestador(Long clienteId, Long prestadorId);
}
