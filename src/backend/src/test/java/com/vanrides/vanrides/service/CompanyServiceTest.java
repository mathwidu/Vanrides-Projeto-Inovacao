package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private CompanyService companyService;

    @Test
    void shouldCreateOnlyANewCompanyForPublicBootstrap() {
        when(companyRepository.findBySlug("escola-aurora")).thenReturn(Optional.empty());
        when(companyRepository.saveAndFlush(any(Company.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Company company = companyService.createNewForPublicBootstrap("  Escola Aurora  ");

        assertThat(company.getNome()).isEqualTo("Escola Aurora");
        assertThat(company.getSlug()).isEqualTo("escola-aurora");
    }

    @Test
    void shouldRejectExistingCompanyWithoutClaimingIt() {
        Company existing = new Company("Escola Aurora", "escola-aurora");
        when(companyRepository.findBySlug("escola-aurora")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> companyService.createNewForPublicBootstrap("Escola Aurora"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason())
                            .isEqualTo("Esta empresa já está cadastrada e requer ativação administrada.");
                });
        verify(companyRepository, never()).saveAndFlush(any(Company.class));
    }

    @Test
    void shouldTranslateConcurrentSlugCollisionToConflict() {
        when(companyRepository.findBySlug("escola-aurora")).thenReturn(Optional.empty());
        when(companyRepository.saveAndFlush(any(Company.class)))
                .thenThrow(new DataIntegrityViolationException("slug race"));

        assertThatThrownBy(() -> companyService.createNewForPublicBootstrap("Escola Aurora"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void shouldRejectMissingCompanyNameBeforeRepositoryAccess() {
        assertThatThrownBy(() -> companyService.createNewForPublicBootstrap(" "))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(companyRepository, never()).findBySlug(any());
    }
}
