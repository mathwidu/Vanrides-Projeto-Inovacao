package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.GuardianPassageiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GuardianPassageiroRepository extends JpaRepository<GuardianPassageiro, Long> {

    boolean existsByGuardianIdAndPassageiroIdAndCompanyId(Long guardianId, Long passageiroId, Long companyId);

    List<GuardianPassageiro> findAllByGuardianIdAndCompanyId(Long guardianId, Long companyId);

    List<GuardianPassageiro> findAllByPassageiroIdAndCompanyId(Long passageiroId, Long companyId);

    long countByPassageiroIdAndCompanyId(Long passageiroId, Long companyId);

    long countByGuardianIdAndCompanyId(Long guardianId, Long companyId);

    Optional<GuardianPassageiro> findByGuardianIdAndPassageiroIdAndCompanyId(Long guardianId, Long passageiroId, Long companyId);

    @Query("""
            select gp from GuardianPassageiro gp
            join fetch gp.guardian g
            where gp.passageiro.id = :passageiroId
              and gp.company.id = :companyId
            order by g.nome asc
            """)
    List<GuardianPassageiro> findAllWithGuardianByPassageiroIdAndCompanyId(@Param("passageiroId") Long passageiroId,
                                                                           @Param("companyId") Long companyId);

    @Query("""
            select gp from GuardianPassageiro gp
            join fetch gp.passageiro p
            left join fetch p.motorista m
            where gp.guardian.id = :guardianId
              and gp.company.id = :companyId
            order by p.nome asc
            """)
    List<GuardianPassageiro> findAllWithPassageiroByGuardianIdAndCompanyId(@Param("guardianId") Long guardianId,
                                                                           @Param("companyId") Long companyId);

    @Query("""
            select gp from GuardianPassageiro gp
            join fetch gp.guardian g
            join fetch gp.passageiro p
            left join fetch p.motorista m
            where gp.company.id = :companyId
            order by g.nome asc, p.nome asc
            """)
    List<GuardianPassageiro> findAllWithGuardianAndPassageiroByCompanyId(@Param("companyId") Long companyId);
}
