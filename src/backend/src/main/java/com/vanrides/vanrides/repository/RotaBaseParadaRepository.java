package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.RotaBaseParada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RotaBaseParadaRepository extends JpaRepository<RotaBaseParada, Long> {

    @Query("""
            SELECT rbp FROM RotaBaseParada rbp
            JOIN FETCH rbp.passageiro
            WHERE rbp.rotaBase.id = :rotaBaseId
            ORDER BY rbp.ordem ASC
            """)
    List<RotaBaseParada> findAllWithPassageiroByRotaBaseIdOrderByOrdemAsc(@Param("rotaBaseId") Long rotaBaseId);

    @Query("SELECT rbp.passageiro.id FROM RotaBaseParada rbp WHERE rbp.rotaBase.id = :rotaBaseId ORDER BY rbp.ordem ASC")
    List<Long> findPassageiroIdsByRotaBaseIdOrderByOrdemAsc(@Param("rotaBaseId") Long rotaBaseId);

    long countByRotaBaseId(Long rotaBaseId);
}
