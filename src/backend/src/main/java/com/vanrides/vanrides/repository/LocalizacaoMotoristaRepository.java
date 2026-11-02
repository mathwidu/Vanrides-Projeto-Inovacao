package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.LocalizacaoMotorista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocalizacaoMotoristaRepository extends JpaRepository<LocalizacaoMotorista, Long> {

    List<LocalizacaoMotorista> findByMotoristaIdOrderByDataHoraDesc(Long motoristaId);

    @Query("""
            SELECT l
            FROM LocalizacaoMotorista l
            WHERE l.motorista.id = :motoristaId
              AND l.motorista.company.id = :companyId
            ORDER BY l.dataHora DESC
            """)
    List<LocalizacaoMotorista> findByMotoristaIdAndCompanyIdOrderByDataHoraDesc(@Param("motoristaId") Long motoristaId,
                                                                                 @Param("companyId") Long companyId);
}
