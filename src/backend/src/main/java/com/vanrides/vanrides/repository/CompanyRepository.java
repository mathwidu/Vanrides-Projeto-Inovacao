package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findBySlug(String slug);

    @Query("""
            select c
            from Company c
            where c.slug <> :excludedSlug
              and (
                    :query is null
                    or trim(:query) = ''
                    or lower(c.nome) like lower(concat('%', :query, '%'))
                    or lower(c.slug) like lower(concat('%', :query, '%'))
                  )
            order by c.nome asc
            """)
    List<Company> searchVisibleCompanies(@Param("excludedSlug") String excludedSlug, @Param("query") String query);
}
