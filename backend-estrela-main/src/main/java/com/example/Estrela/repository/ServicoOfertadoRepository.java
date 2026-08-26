package com.example.Estrela.repository;

import com.example.Estrela.Entity.ServicoOfertado;
import com.example.Estrela.Entity.StatusServico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServicoOfertadoRepository extends JpaRepository<ServicoOfertado, Long> {

    List<ServicoOfertado> findByPrestador_IdPrestador(Long idPrestador);

    List<ServicoOfertado> findByStatusAndCategoriaIgnoreCase(StatusServico status, String categoria);

    List<ServicoOfertado> findByStatusAndCategoriaIgnoreCaseAndLocalizacao_CidadeIgnoreCase(
            StatusServico status, String categoria, String cidade);
}
