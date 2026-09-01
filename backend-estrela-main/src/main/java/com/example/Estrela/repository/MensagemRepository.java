package com.example.Estrela.repository;

import com.example.Estrela.Entity.Mensagem;
import com.example.Estrela.Entity.TipoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Acesso às mensagens trocadas nas solicitações.
 *
 * <p>As consultas por solicitação usam {@code @Query} em vez de nome derivado porque a
 * chave de {@code FatoServico} é o campo {@code id_servico}, em snake_case, e o
 * derivador de nomes do Spring Data não resolve esse caminho — a mesma razão pela qual
 * {@code FatoServicoRepository.buscarAvaliacoesPublicas} também é explícita.
 */
@Repository
public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    /**
     * Conversa de uma solicitação, da mensagem mais antiga para a mais recente.
     *
     * @param solicitacaoId identificador da solicitação
     * @return mensagens em ordem cronológica, lista vazia quando ninguém escreveu ainda
     */
    @Query("""
            SELECT m FROM Mensagem m
            WHERE m.fatoServico.id_servico = :solicitacaoId
            ORDER BY m.criadoEm ASC, m.id ASC
            """)
    List<Mensagem> listarConversa(@Param("solicitacaoId") Long solicitacaoId);

    /**
     * Mensagens de uma solicitação ainda não lidas que foram escritas pela outra parte —
     * as que o papel informado precisa ler.
     *
     * @param solicitacaoId identificador da solicitação
     * @param leitor        papel de quem está lendo; suas próprias mensagens são excluídas
     * @return mensagens pendentes de leitura
     */
    @Query("""
            SELECT m FROM Mensagem m
            WHERE m.fatoServico.id_servico = :solicitacaoId
              AND m.remetenteTipo <> :leitor
              AND m.lidaEm IS NULL
            """)
    List<Mensagem> listarNaoLidasPara(@Param("solicitacaoId") Long solicitacaoId,
                                      @Param("leitor") TipoUsuario leitor);

    /**
     * Quantas mensagens o cliente ainda não leu, somando todas as solicitações dele.
     * Alimenta o aviso de atividade correspondente.
     *
     * @param clienteId identificador do cliente
     * @return quantidade de mensagens não lidas escritas pelos prestadores
     */
    @Query("""
            SELECT COUNT(m) FROM Mensagem m
            WHERE m.fatoServico.cliente.idCliente = :clienteId
              AND m.remetenteTipo <> com.example.Estrela.Entity.TipoUsuario.CLIENTE
              AND m.lidaEm IS NULL
            """)
    long contarNaoLidasDoCliente(@Param("clienteId") Long clienteId);
}
