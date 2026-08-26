package com.example.Estrela.repository;

import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.StatusSolicitacao;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FatoServicoRepository extends JpaRepository<FatoServico, Long> {

    List<FatoServico> findByLocalizacao_Cidade(String cidade);

    long countByStatus(StatusSolicitacao status);

    List<FatoServico> findByPrestador_IdPrestador(Long idPrestador);

    List<FatoServico> findByCliente_IdCliente(Long idCliente);
}
