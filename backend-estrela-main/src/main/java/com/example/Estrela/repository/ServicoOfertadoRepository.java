package com.example.Estrela.repository;

import com.example.Estrela.DTO.CategoriaDisponivelResponse;
import com.example.Estrela.Entity.ServicoOfertado;
import com.example.Estrela.Entity.StatusKyc;
import com.example.Estrela.Entity.StatusServico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServicoOfertadoRepository extends JpaRepository<ServicoOfertado, Long> {

    List<ServicoOfertado> findByPrestador_IdPrestador(Long idPrestador);

    /** Serviços de um prestador em determinada situação, para o catálogo público dele. */
    List<ServicoOfertado> findByPrestador_IdPrestadorAndStatus(Long idPrestador, StatusServico status);

    List<ServicoOfertado> findByStatusAndCategoriaIgnoreCase(StatusServico status, String categoria);

    List<ServicoOfertado> findByStatusAndCategoriaIgnoreCaseAndLocalizacao_CidadeIgnoreCase(
            StatusServico status, String categoria, String cidade);

    /**
     * Agrega as categorias que têm ao menos um serviço disponível ao público, com a contagem de cada.
     *
     * <p>É o único {@code @Query} do projeto, e de propósito: agrupar por categoria é contagem, e a
     * convenção do backend manda agregar no repository em vez de carregar listas para contar em
     * memória. O argumento de portabilidade que justifica o Haversine em memória não se aplica aqui
     * — {@code GROUP BY} é ANSI e roda igual em PostgreSQL e H2.
     *
     * @param status    situação exigida do serviço (`ATIVO` para o catálogo público)
     * @param statusKyc situação exigida da verificação do prestador (`APROVADO`, RN04)
     * @return categorias ordenadas da mais ofertada para a menos, com desempate alfabético
     */
    @Query("""
            SELECT new com.example.Estrela.DTO.CategoriaDisponivelResponse(s.categoria, COUNT(s))
            FROM ServicoOfertado s
            WHERE s.status = :status AND s.prestador.statusKyc = :statusKyc
            GROUP BY s.categoria
            ORDER BY COUNT(s) DESC, s.categoria ASC
            """)
    List<CategoriaDisponivelResponse> agregarCategoriasDisponiveis(@Param("status") StatusServico status,
                                                                    @Param("statusKyc") StatusKyc statusKyc);
}
