package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.PresencaServico;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PresencaServicoRepository extends JpaRepository<PresencaServico, Long> {

    List<PresencaServico> findByPassageiroIdAndCompanyIdAndDataOperacaoBetweenOrderByDataOperacaoAsc(
            Long passageiroId,
            Long companyId,
            LocalDate dataInicio,
            LocalDate dataFim
    );

    List<PresencaServico> findByPassageiroIdAndCompanyIdAndDataOperacao(
            Long passageiroId,
            Long companyId,
            LocalDate dataOperacao
    );

    List<PresencaServico> findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
            List<Long> passageiroIds,
            Long companyId,
            Long servicoId,
            LocalDate dataOperacao
    );

    Optional<PresencaServico> findByServicoRecorrenteIdAndPassageiroIdAndCompanyIdAndDataOperacao(
            Long servicoId,
            Long passageiroId,
            Long companyId,
            LocalDate dataOperacao
    );

    @Query("""
            SELECT ps FROM PresencaServico ps
            JOIN FETCH ps.servicoRecorrente sr
            LEFT JOIN FETCH sr.motoristaPadrao
            JOIN FETCH ps.passageiro
            WHERE ps.id = :id
              AND ps.company.id = :companyId
            """)
    Optional<PresencaServico> findByIdAndCompanyIdWithDetails(@Param("id") Long id,
                                                              @Param("companyId") Long companyId);

    long countByCompanyIdAndDataOperacaoAndStatus(Long companyId,
                                                  LocalDate dataOperacao,
                                                  PresencaServicoStatus status);
}
