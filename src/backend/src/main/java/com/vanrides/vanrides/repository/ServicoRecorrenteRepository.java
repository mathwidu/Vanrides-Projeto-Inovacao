package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.ServicoRecorrente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServicoRecorrenteRepository extends JpaRepository<ServicoRecorrente, Long> {

    @Query("""
            SELECT sr FROM ServicoRecorrente sr
            LEFT JOIN FETCH sr.motoristaPadrao
            WHERE sr.company.id = :companyId
              AND sr.ativo = true
            ORDER BY sr.nome ASC
            """)
    List<ServicoRecorrente> findAllAtivosWithMotoristaByCompanyIdOrderByNomeAsc(@Param("companyId") Long companyId);

    @Query("""
            SELECT sr FROM ServicoRecorrente sr
            LEFT JOIN FETCH sr.motoristaPadrao
            WHERE sr.id IN :ids
              AND sr.company.id = :companyId
              AND sr.ativo = true
            """)
    List<ServicoRecorrente> findAllAtivosByIdInAndCompanyId(@Param("ids") List<Long> ids,
                                                            @Param("companyId") Long companyId);

    @Query("""
            SELECT sr FROM ServicoRecorrente sr
            LEFT JOIN FETCH sr.motoristaPadrao
            WHERE sr.id = :id
              AND sr.company.id = :companyId
            """)
    Optional<ServicoRecorrente> findByIdAndCompanyId(@Param("id") Long id,
                                                     @Param("companyId") Long companyId);

    @Query("""
            SELECT sr FROM ServicoRecorrente sr
            LEFT JOIN FETCH sr.motoristaPadrao
            WHERE sr.company.id = :companyId
              AND sr.motoristaPadrao.id = :motoristaId
              AND sr.ativo = true
              AND sr.geracaoAutomaticaAtiva = true
            ORDER BY sr.horarioPartida ASC NULLS LAST, sr.id ASC
            """)
    List<ServicoRecorrente> findAllAutomaticosByMotoristaPadraoIdAndCompanyId(@Param("motoristaId") Long motoristaId,
                                                                              @Param("companyId") Long companyId);

    @Query("""
            SELECT DISTINCT sr FROM ServicoRecorrente sr
            LEFT JOIN FETCH sr.motoristaPadrao
            JOIN ServicoPassageiro sp ON sp.servicoRecorrente.id = sr.id
            WHERE sr.company.id = :companyId
              AND sp.passageiro.id = :passageiroId
              AND sp.ativo = true
              AND sr.ativo = true
              AND sr.geracaoAutomaticaAtiva = true
            ORDER BY sr.horarioPartida ASC NULLS LAST, sr.id ASC
            """)
    List<ServicoRecorrente> findAllAutomaticosByPassageiroIdAndCompanyId(@Param("passageiroId") Long passageiroId,
                                                                         @Param("companyId") Long companyId);

    @Query("""
            SELECT sr FROM ServicoRecorrente sr
            LEFT JOIN FETCH sr.motoristaPadrao
            WHERE sr.company.id = :companyId
              AND sr.ativo = true
              AND sr.geracaoAutomaticaAtiva = true
            ORDER BY sr.horarioPartida ASC NULLS LAST, sr.id ASC
            """)
    List<ServicoRecorrente> findAllAutomaticosByCompanyId(@Param("companyId") Long companyId);

    Optional<ServicoRecorrente> findByRotaBaseLegadaIdAndCompanyId(Long rotaBaseId, Long companyId);

    long countByCompanyIdAndAtivoTrue(Long companyId);

    long countByCompanyIdAndAtivoTrueAndGeracaoAutomaticaAtivaTrue(Long companyId);

    long countByCompanyIdAndAtivoTrueAndMotoristaPadraoIsNull(Long companyId);
}
