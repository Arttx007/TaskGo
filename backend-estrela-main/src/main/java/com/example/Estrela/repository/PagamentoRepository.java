package com.example.Estrela.repository;

import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    Optional<Pagamento> findByFatoServico(FatoServico fatoServico);

    /**
     * Pagamentos originados por um cliente, do mais recente para o mais antigo.
     *
     * <p>Usa {@code @Query} em vez de nome derivado porque o desempate percorre
     * {@code fatoServico.id_servico}, campo em snake_case que o derivador de nomes do
     * Spring Data não resolve. O desempate por id mantém a ordem estável entre pagamentos
     * feitos no mesmo instante.
     *
     * @param clienteId identificador do cliente autenticado
     * @return pagamentos do cliente, ordenados do mais recente para o mais antigo
     */
    @Query("""
            SELECT p FROM Pagamento p
            WHERE p.fatoServico.cliente.idCliente = :clienteId
            ORDER BY p.criadoEm DESC, p.id DESC
            """)
    List<Pagamento> listarPorCliente(@Param("clienteId") Long clienteId);
}
