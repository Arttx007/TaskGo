package com.example.Estrela.repository;

import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    Optional<Pagamento> findByFatoServico(FatoServico fatoServico);
}
