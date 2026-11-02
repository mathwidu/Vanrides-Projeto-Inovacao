package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.Presenca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PresencaRepository extends JpaRepository<Presenca, Long> {

    List<Presenca> findByPassageiroId(Long passageiroId);

    List<Presenca> findByDataPresenca(LocalDate dataPresenca);

    boolean existsByPassageiroIdAndDataPresenca(Long passageiroId, LocalDate dataPresenca);

    @Query("SELECT p FROM Presenca p WHERE p.passageiro.company.id = :companyId")
    List<Presenca> findAllByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT p FROM Presenca p WHERE p.passageiro.id = :passageiroId AND p.passageiro.company.id = :companyId")
    List<Presenca> findByPassageiroIdAndCompanyId(@Param("passageiroId") Long passageiroId,
                                                  @Param("companyId") Long companyId);

    @Query("""
            SELECT p FROM Presenca p
            WHERE p.passageiro.id IN :passageiroIds
              AND p.passageiro.company.id = :companyId
            """)
    List<Presenca> findByPassageiroIdInAndCompanyId(@Param("passageiroIds") List<Long> passageiroIds,
                                                    @Param("companyId") Long companyId);

    @Query("SELECT p FROM Presenca p WHERE p.dataPresenca = :dataPresenca AND p.passageiro.company.id = :companyId")
    List<Presenca> findByDataPresencaAndCompanyId(@Param("dataPresenca") LocalDate dataPresenca,
                                                  @Param("companyId") Long companyId);

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM Presenca p
            WHERE p.passageiro.id = :passageiroId
              AND p.dataPresenca = :dataPresenca
              AND p.passageiro.company.id = :companyId
            """)
    boolean existsByPassageiroIdAndDataPresencaAndCompanyId(@Param("passageiroId") Long passageiroId,
                                                            @Param("dataPresenca") LocalDate dataPresenca,
                                                            @Param("companyId") Long companyId);

    @Query("SELECT p FROM Presenca p WHERE p.id = :id AND p.passageiro.company.id = :companyId")
    Optional<Presenca> findByIdAndCompanyId(@Param("id") Long id,
                                            @Param("companyId") Long companyId);

    @Query("SELECT p FROM Presenca p WHERE p.dataPresenca = :data AND p.passageiro.motorista.id = :motoristaId")
    List<Presenca> findByMotoristaIdAndData(@Param("motoristaId") Long motoristaId,
                                             @Param("data") LocalDate data);

    @Query("""
            SELECT p FROM Presenca p
            WHERE p.dataPresenca = :data
              AND p.passageiro.motorista.id = :motoristaId
              AND p.passageiro.company.id = :companyId
            """)
    List<Presenca> findByMotoristaIdAndDataAndCompanyId(@Param("motoristaId") Long motoristaId,
                                                        @Param("data") LocalDate data,
                                                        @Param("companyId") Long companyId);

    @Query("""
            SELECT p FROM Presenca p
            WHERE p.passageiro.id IN :passageiroIds
              AND p.passageiro.company.id = :companyId
              AND p.dataPresenca = :dataPresenca
            """)
    List<Presenca> findByPassageiroIdInAndCompanyIdAndDataPresenca(@Param("passageiroIds") List<Long> passageiroIds,
                                                                   @Param("companyId") Long companyId,
                                                                   @Param("dataPresenca") LocalDate dataPresenca);

    long countByPassageiro_Company_IdAndDataPresenca(Long companyId, LocalDate dataPresenca);
}
