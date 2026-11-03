package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.RotaBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RotaBaseRepository extends JpaRepository<RotaBase, Long> {

    @Query("""
            SELECT rb FROM RotaBase rb
            JOIN FETCH rb.motorista
            WHERE rb.company.id = :companyId
              AND rb.ativa = true
            ORDER BY rb.nome ASC
            """)
    List<RotaBase> findAllWithMotoristaByCompanyIdOrderByNomeAsc(@Param("companyId") Long companyId);

    @Query("""
            SELECT rb FROM RotaBase rb
            JOIN FETCH rb.motorista
            WHERE rb.id = :id
              AND rb.company.id = :companyId
            """)
    Optional<RotaBase> findByIdAndCompanyId(@Param("id") Long id,
                                            @Param("companyId") Long companyId);

    @Query("""
            SELECT rb FROM RotaBase rb
            JOIN FETCH rb.motorista
            WHERE rb.company.id = :companyId
              AND rb.motorista.id = :motoristaId
              AND rb.ativo = true
              AND rb.ativa = true
              AND rb.geracaoAutomaticaAtiva = true
            ORDER BY rb.id ASC
            """)
    List<RotaBase> findAllAutomaticasByMotoristaIdAndCompanyId(@Param("motoristaId") Long motoristaId,
                                                               @Param("companyId") Long companyId);

    @Query("""
            SELECT DISTINCT rb FROM RotaBase rb
            JOIN FETCH rb.motorista
            JOIN RotaBaseParada rbp ON rbp.rotaBase.id = rb.id
            WHERE rb.company.id = :companyId
              AND rbp.passageiro.id = :passageiroId
              AND rb.ativo = true
              AND rb.ativa = true
              AND rb.geracaoAutomaticaAtiva = true
            ORDER BY rb.id ASC
            """)
    List<RotaBase> findAllAutomaticasByPassageiroIdAndCompanyId(@Param("passageiroId") Long passageiroId,
                                                                @Param("companyId") Long companyId);

    long countByCompanyIdAndAtivaTrue(Long companyId);
}
