package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.ViagemParada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ViagemParadaRepository extends JpaRepository<ViagemParada, Long> {

    @Query("""
            SELECT vp FROM ViagemParada vp
            JOIN FETCH vp.passageiro
            WHERE vp.viagem.id = :viagemId
            ORDER BY vp.ordem ASC
            """)
    List<ViagemParada> findAllWithPassageiroByViagemIdOrderByOrdemAsc(@Param("viagemId") Long viagemId);

    @Query("""
            SELECT vp FROM ViagemParada vp
            JOIN FETCH vp.passageiro
            WHERE vp.id = :id
              AND vp.viagem.id = :viagemId
              AND vp.viagem.company.id = :companyId
            """)
    Optional<ViagemParada> findByIdAndViagemIdAndCompanyId(@Param("id") Long id,
                                                           @Param("viagemId") Long viagemId,
                                                           @Param("companyId") Long companyId);

    long countByViagemId(Long viagemId);
}
