package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.ServicoPassageiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServicoPassageiroRepository extends JpaRepository<ServicoPassageiro, Long> {

    @Query("""
            SELECT sp FROM ServicoPassageiro sp
            JOIN FETCH sp.passageiro
            WHERE sp.servicoRecorrente.id = :servicoId
              AND sp.company.id = :companyId
              AND sp.ativo = true
            ORDER BY sp.ordemPadrao ASC NULLS LAST, sp.id ASC
            """)
    List<ServicoPassageiro> findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(@Param("servicoId") Long servicoId,
                                                                                           @Param("companyId") Long companyId);

    List<ServicoPassageiro> findAllByServicoRecorrenteIdAndCompanyId(Long servicoId, Long companyId);

    @Query("""
            SELECT sp FROM ServicoPassageiro sp
            JOIN FETCH sp.servicoRecorrente sr
            LEFT JOIN FETCH sr.motoristaPadrao
            WHERE sp.passageiro.id = :passageiroId
              AND sp.company.id = :companyId
              AND sp.ativo = true
              AND sr.ativo = true
            ORDER BY sr.horarioPartida ASC NULLS LAST, sp.ordemPadrao ASC NULLS LAST, sr.id ASC
            """)
    List<ServicoPassageiro> findAllAtivosWithServicoByPassageiroIdAndCompanyId(@Param("passageiroId") Long passageiroId,
                                                                               @Param("companyId") Long companyId);

    @Query("""
            SELECT sp FROM ServicoPassageiro sp
            JOIN FETCH sp.servicoRecorrente sr
            LEFT JOIN FETCH sr.motoristaPadrao
            WHERE sp.passageiro.id = :passageiroId
              AND sp.company.id = :companyId
            ORDER BY sr.nome ASC, sp.id ASC
            """)
    List<ServicoPassageiro> findAllWithServicoByPassageiroIdAndCompanyId(@Param("passageiroId") Long passageiroId,
                                                                         @Param("companyId") Long companyId);

    Optional<ServicoPassageiro> findByServicoRecorrenteIdAndPassageiroIdAndCompanyIdAndAtivoTrue(Long servicoId,
                                                                                                 Long passageiroId,
                                                                                                 Long companyId);

    Optional<ServicoPassageiro> findByServicoRecorrenteIdAndPassageiroIdAndCompanyId(Long servicoId,
                                                                                     Long passageiroId,
                                                                                     Long companyId);

    @Query("""
            SELECT sp.passageiro.id FROM ServicoPassageiro sp
            WHERE sp.servicoRecorrente.id = :servicoId
              AND sp.company.id = :companyId
              AND sp.ativo = true
            ORDER BY sp.ordemPadrao ASC NULLS LAST, sp.id ASC
            """)
    List<Long> findPassageiroIdsByServicoIdAndCompanyIdOrderByOrdem(@Param("servicoId") Long servicoId,
                                                                    @Param("companyId") Long companyId);

    boolean existsByServicoRecorrenteIdAndPassageiroIdAndCompanyIdAndAtivoTrue(Long servicoId,
                                                                               Long passageiroId,
                                                                               Long companyId);

    long countByServicoRecorrenteIdAndCompanyIdAndAtivoTrue(Long servicoId, Long companyId);
}
