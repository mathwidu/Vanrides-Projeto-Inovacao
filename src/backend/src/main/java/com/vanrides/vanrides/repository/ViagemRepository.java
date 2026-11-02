package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ViagemRepository extends JpaRepository<Viagem, Long> {

    @Query("""
            SELECT v FROM Viagem v
            LEFT JOIN FETCH v.rotaBase
            LEFT JOIN FETCH v.servicoRecorrente
            JOIN FETCH v.motorista
            WHERE v.id = :id
              AND v.company.id = :companyId
            """)
    Optional<Viagem> findByIdAndCompanyId(@Param("id") Long id,
                                          @Param("companyId") Long companyId);

    @Query("""
            SELECT v FROM Viagem v
            LEFT JOIN FETCH v.rotaBase
            LEFT JOIN FETCH v.servicoRecorrente
            JOIN FETCH v.motorista
            WHERE v.company.id = :companyId
            ORDER BY v.dataOperacao DESC, v.id DESC
            """)
    List<Viagem> findAllByCompanyIdOrderByDataOperacaoDescIdDesc(@Param("companyId") Long companyId);

    @Query("""
            SELECT v FROM Viagem v
            LEFT JOIN FETCH v.rotaBase
            LEFT JOIN FETCH v.servicoRecorrente
            JOIN FETCH v.motorista
            WHERE v.company.id = :companyId
              AND v.dataOperacao = :dataOperacao
            ORDER BY v.id DESC
            """)
    List<Viagem> findAllByCompanyIdAndDataOperacaoOrderByIdDesc(@Param("companyId") Long companyId,
                                                                @Param("dataOperacao") LocalDate dataOperacao);

    @Query("""
            SELECT v FROM Viagem v
            LEFT JOIN FETCH v.rotaBase
            LEFT JOIN FETCH v.servicoRecorrente
            JOIN FETCH v.motorista
            WHERE v.motorista.id = :motoristaId
              AND v.dataOperacao = :dataOperacao
              AND v.company.id = :companyId
            ORDER BY v.horarioPartida ASC NULLS LAST, v.id ASC
            """)
    List<Viagem> findByMotoristaIdAndDataOperacaoAndCompanyId(@Param("motoristaId") Long motoristaId,
                                                              @Param("dataOperacao") LocalDate dataOperacao,
                                                              @Param("companyId") Long companyId);

    @Query("""
            SELECT v FROM Viagem v
            LEFT JOIN FETCH v.rotaBase
            LEFT JOIN FETCH v.servicoRecorrente
            JOIN FETCH v.motorista
            JOIN ViagemParada vp ON vp.viagem.id = v.id
            WHERE vp.passageiro.id = :passageiroId
              AND v.dataOperacao = :dataOperacao
              AND v.company.id = :companyId
            ORDER BY v.horarioPartida ASC NULLS LAST, v.id ASC
            """)
    List<Viagem> findByPassageiroIdAndDataOperacaoAndCompanyId(@Param("passageiroId") Long passageiroId,
                                                               @Param("dataOperacao") LocalDate dataOperacao,
                                                               @Param("companyId") Long companyId);

    @Query("""
            SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
            FROM Viagem v
            WHERE v.rotaBase.id = :rotaBaseId
              AND v.dataOperacao = :dataOperacao
              AND v.company.id = :companyId
            """)
    boolean existsByRotaBaseIdAndDataOperacaoAndCompanyId(@Param("rotaBaseId") Long rotaBaseId,
                                                          @Param("dataOperacao") LocalDate dataOperacao,
                                                          @Param("companyId") Long companyId);

    @Query("""
            SELECT v FROM Viagem v
            LEFT JOIN FETCH v.rotaBase
            LEFT JOIN FETCH v.servicoRecorrente
            JOIN FETCH v.motorista
            WHERE v.rotaBase.id = :rotaBaseId
              AND v.dataOperacao = :dataOperacao
              AND v.company.id = :companyId
            """)
    Optional<Viagem> findByRotaBaseIdAndDataOperacaoAndCompanyId(@Param("rotaBaseId") Long rotaBaseId,
                                                                 @Param("dataOperacao") LocalDate dataOperacao,
                                                                 @Param("companyId") Long companyId);

    @Query("""
            SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
            FROM Viagem v
            WHERE v.servicoRecorrente.id = :servicoId
              AND v.dataOperacao = :dataOperacao
              AND v.company.id = :companyId
            """)
    boolean existsByServicoRecorrenteIdAndDataOperacaoAndCompanyId(@Param("servicoId") Long servicoId,
                                                                   @Param("dataOperacao") LocalDate dataOperacao,
                                                                   @Param("companyId") Long companyId);

    @Query("""
            SELECT v FROM Viagem v
            LEFT JOIN FETCH v.rotaBase
            LEFT JOIN FETCH v.servicoRecorrente
            JOIN FETCH v.motorista
            WHERE v.servicoRecorrente.id = :servicoId
              AND v.dataOperacao = :dataOperacao
              AND v.company.id = :companyId
            """)
    Optional<Viagem> findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(@Param("servicoId") Long servicoId,
                                                                          @Param("dataOperacao") LocalDate dataOperacao,
                                                                          @Param("companyId") Long companyId);

    @Query("""
            SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
            FROM Viagem v
            WHERE v.id = :viagemId
              AND v.motorista.id = :motoristaId
              AND v.company.id = :companyId
            """)
    boolean existsByIdAndMotoristaIdAndCompanyId(@Param("viagemId") Long viagemId,
                                                 @Param("motoristaId") Long motoristaId,
                                                 @Param("companyId") Long companyId);

    @Query("""
            SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
            FROM Viagem v
            WHERE v.rotaBase.id = :rotaBaseId
              AND v.status = :status
              AND v.company.id = :companyId
            """)
    boolean existsByRotaBaseIdAndStatusAndCompanyId(@Param("rotaBaseId") Long rotaBaseId,
                                                    @Param("status") ViagemStatus status,
                                                    @Param("companyId") Long companyId);

    @Query("""
            SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
            FROM Viagem v
            WHERE v.servicoRecorrente.id = :servicoId
              AND v.status = :status
              AND v.company.id = :companyId
            """)
    boolean existsByServicoRecorrenteIdAndStatusAndCompanyId(@Param("servicoId") Long servicoId,
                                                             @Param("status") ViagemStatus status,
                                                             @Param("companyId") Long companyId);

    @Query("""
            SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
            FROM Viagem v
            WHERE v.motorista.id = :motoristaId
              AND v.status = :status
              AND v.company.id = :companyId
            """)
    boolean existsByMotoristaIdAndStatusAndCompanyId(@Param("motoristaId") Long motoristaId,
                                                     @Param("status") ViagemStatus status,
                                                     @Param("companyId") Long companyId);

    @Query("""
            SELECT CASE WHEN COUNT(vp) > 0 THEN true ELSE false END
            FROM ViagemParada vp
            JOIN vp.passageiro p
            JOIN vp.viagem v
            JOIN v.motorista m
            WHERE m.id = :motoristaId
              AND v.company.id = :companyId
              AND v.dataOperacao = :dataOperacao
              AND v.status IN :allowedStatuses
              AND p.company.id = :companyId
              AND m.company.id = :companyId
              AND vp.passageiro.id IN (
                  SELECT gp.passageiro.id
                  FROM GuardianPassageiro gp
                  WHERE gp.guardian.id = :guardianId
                    AND gp.company.id = :companyId
                    AND gp.guardian.company.id = :companyId
                    AND gp.passageiro.company.id = :companyId
              )
            """)
    boolean existsByGuardianLinkedPassengerAndMotoristaOnDate(@Param("guardianId") Long guardianId,
                                                              @Param("motoristaId") Long motoristaId,
                                                              @Param("companyId") Long companyId,
                                                              @Param("dataOperacao") LocalDate dataOperacao,
                                                              @Param("allowedStatuses") Collection<ViagemStatus> allowedStatuses);

    @Query("""
            SELECT CASE WHEN COUNT(vp) > 0 THEN true ELSE false END
            FROM ViagemParada vp
            JOIN vp.passageiro p
            JOIN vp.viagem v
            JOIN v.motorista m
            WHERE p.id = :passageiroId
              AND p.company.id = :companyId
              AND v.company.id = :companyId
              AND m.id = :motoristaId
              AND m.company.id = :companyId
              AND v.dataOperacao = :dataOperacao
              AND v.status IN :allowedStatuses
            """)
    boolean existsByPassengerAndMotoristaOnDate(@Param("passageiroId") Long passageiroId,
                                                @Param("motoristaId") Long motoristaId,
                                                @Param("companyId") Long companyId,
                                                @Param("dataOperacao") LocalDate dataOperacao,
                                                @Param("allowedStatuses") Collection<ViagemStatus> allowedStatuses);

    long countByCompanyIdAndDataOperacaoAndStatus(Long companyId, LocalDate dataOperacao, ViagemStatus status);
}
