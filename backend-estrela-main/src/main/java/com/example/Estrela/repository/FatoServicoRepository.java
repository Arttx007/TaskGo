package com.example.Estrela.repository;

import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.StatusKyc;
import com.example.Estrela.Entity.StatusSolicitacao;


import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FatoServicoRepository extends JpaRepository<FatoServico, Long> {

    List<FatoServico> findByLocalizacao_Cidade(String cidade);

    long countByStatus(StatusSolicitacao status);

    List<FatoServico> findByPrestador_IdPrestador(Long idPrestador);

    List<FatoServico> findByCliente_IdCliente(Long idCliente);

    /**
     * Avaliações exibíveis publicamente: solicitação avaliada, com nota e comentário preenchidos, de
     * prestador com verificação aprovada (RN04). Nota sem comentário não é depoimento e é descartada
     * aqui, para não render card vazio no site.
     *
     * <p>Ordena da mais recente para a mais antiga por {@code tempo.data}, com desempate por id para
     * que a ordem seja estável entre avaliações do mesmo dia. Como o caminho {@code f.tempo.data}
     * produz junção interna, solicitação sem registro temporal não aparece — a máquina de estados
     * sempre cria o {@code Tempo}, então na prática isso não descarta dado real.
     *
     * @param status    situação exigida (`AVALIADO`)
     * @param statusKyc situação exigida da verificação do prestador (`APROVADO`)
     * @param pageable  usado apenas para limitar a quantidade devolvida
     * @return avaliações da mais recente para a mais antiga
     */
    @Query("""
            SELECT f FROM FatoServico f
            WHERE f.status = :status
              AND f.avaliacao IS NOT NULL
              AND f.comentarioAvaliacao IS NOT NULL
              AND TRIM(f.comentarioAvaliacao) <> ''
              AND f.prestador.statusKyc = :statusKyc
            ORDER BY f.tempo.data DESC, f.id_servico DESC
            """)
    List<FatoServico> buscarAvaliacoesPublicas(@Param("status") StatusSolicitacao status,
                                                @Param("statusKyc") StatusKyc statusKyc,
                                                Pageable pageable);
}
