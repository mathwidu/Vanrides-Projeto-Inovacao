package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.Passageiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PassageiroRepository extends JpaRepository<Passageiro, Long> {

    // Podemos criar métodos personalizados aqui depois, como buscar por e-mail

    Passageiro findByEmail(String email);

    List<Passageiro> findAllByCompanyId(Long companyId);

    List<Passageiro> findAllByCompanyIdOrderByNomeAsc(Long companyId);

    List<Passageiro> findAllByIdInAndCompanyId(List<Long> ids, Long companyId);

    Optional<Passageiro> findByIdAndCompanyId(Long id, Long companyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM passageiro
            WHERE id = :id
              AND company_id = :companyId
            """, nativeQuery = true)
    int deleteByIdAndCompanyIdAtomically(
            @Param("id") Long id,
            @Param("companyId") Long companyId);
}
