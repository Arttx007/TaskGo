package com.example.Estrela.repository;

import com.example.Estrela.Entity.EnderecoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Acesso aos endereços de atendimento dos clientes.
 */
@Repository
public interface EnderecoClienteRepository extends JpaRepository<EnderecoCliente, Long> {

    /** Endereços visíveis na lista do cliente, do mais antigo para o mais recente. */
    List<EnderecoCliente> findByCliente_IdClienteAndAtivoTrueOrderByIdAsc(Long clienteId);

    /** Todos os endereços do cliente, inclusive os removidos logicamente. */
    List<EnderecoCliente> findByCliente_IdCliente(Long clienteId);

    /** Endereço padrão vigente do cliente, usado quando ele não escolhe outro. */
    Optional<EnderecoCliente> findByCliente_IdClienteAndPadraoTrueAndAtivoTrue(Long clienteId);
}
