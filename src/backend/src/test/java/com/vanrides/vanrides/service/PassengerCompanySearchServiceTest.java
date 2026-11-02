package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerCompanySearchServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private PassengerCompanySearchService service;

    @Test
    void shouldNormalizeQueryAndMapVisibleCompanies() {
        when(companyRepository.searchVisibleCompanies(CompanyService.DEFAULT_COMPANY_SLUG, "azul"))
                .thenReturn(List.of(company(55L, "Empresa Azul", "empresa-azul")));

        var result = service.searchCompanies("  azul  ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(55L);
        assertThat(result.get(0).nome()).isEqualTo("Empresa Azul");
        assertThat(result.get(0).slug()).isEqualTo("empresa-azul");
    }

    @Test
    void shouldUseNullQueryWhenInputIsBlank() {
        when(companyRepository.searchVisibleCompanies(CompanyService.DEFAULT_COMPANY_SLUG, null))
                .thenReturn(List.of());

        var result = service.searchCompanies("   ");

        assertThat(result).isEmpty();
    }

    private Company company(Long id, String nome, String slug) {
        Company company = new Company(nome, slug);
        company.setId(id);
        return company;
    }
}
