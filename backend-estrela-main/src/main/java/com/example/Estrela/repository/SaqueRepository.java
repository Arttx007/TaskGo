package com.example.Estrela.repository;

import com.example.Estrela.Entity.Saque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaqueRepository extends JpaRepository<Saque, Long> {

    List<Saque> findByPrestador_IdPrestador(Long idPrestador);
}
