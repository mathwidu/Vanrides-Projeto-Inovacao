package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.PassengerOnboardingStatusResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyLinkRequest;
import com.vanrides.vanrides.model.CompanyLinkRequestSource;
import com.vanrides.vanrides.model.CompanyLinkRequestStatus;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyLinkRequestRepository;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerCompanyRequestServiceTest {

    @Mock
    private PassageiroRepository passageiroRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanyLinkRequestRepository companyLinkRequestRepository;

    @Mock
    private PassengerOnboardingStatusResolver statusResolver;

    @InjectMocks
    private PassengerCompanyRequestService service;

    @Test
    void shouldCreatePendingRequestForUnlinkedPassenger() {
        Passageiro passageiro = passageiro(21L, null);
        Company company = company(55L, "empresa-azul");
        PassengerOnboardingStatusResponse status = new PassengerOnboardingStatusResponse(
                "PENDING_APPROVAL",
                null,
                null,
                null,
                null
        );

        when(passageiroRepository.findById(21L)).thenReturn(Optional.of(passageiro));
        when(companyRepository.findById(55L)).thenReturn(Optional.of(company));
        when(companyLinkRequestRepository.findFirstByPassageiroIdAndStatusOrderByRequestedAtDesc(
                21L,
                CompanyLinkRequestStatus.PENDING
        )).thenReturn(Optional.empty());
        when(statusResolver.resolve(passageiro)).thenReturn(status);

        PassengerOnboardingStatusResponse result = service.createRequest(21L, 55L);

        ArgumentCaptor<CompanyLinkRequest> captor = ArgumentCaptor.forClass(CompanyLinkRequest.class);
        verify(companyLinkRequestRepository).save(captor.capture());
        assertThat(result).isSameAs(status);
        assertThat(captor.getValue().getPassageiro()).isSameAs(passageiro);
        assertThat(captor.getValue().getCompany()).isSameAs(company);
        assertThat(captor.getValue().getSource()).isEqualTo(CompanyLinkRequestSource.SEARCH_REQUEST);
        assertThat(captor.getValue().getStatus()).isEqualTo(CompanyLinkRequestStatus.PENDING);
    }

    @Test
    void shouldReturnCurrentStatusWhenPassengerAlreadyHasCompany() {
        Company company = company(55L, "empresa-azul");
        Passageiro passageiro = passageiro(21L, company);
        PassengerOnboardingStatusResponse status = new PassengerOnboardingStatusResponse(
                "COMPANY_LINKED",
                55L,
                "Empresa Azul",
                null,
                null
        );

        when(passageiroRepository.findById(21L)).thenReturn(Optional.of(passageiro));
        when(statusResolver.resolve(passageiro)).thenReturn(status);

        PassengerOnboardingStatusResponse result = service.createRequest(21L, 55L);

        assertThat(result).isSameAs(status);
        verify(companyRepository, never()).findById(55L);
        verify(companyLinkRequestRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Passageiro passageiro(Long id, Company company) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Passageiro " + id);
        passageiro.setEmail("passageiro" + id + "@vanrides.dev");
        passageiro.setCompany(company);
        return passageiro;
    }

    private Company company(Long id, String slug) {
        Company company = new Company("Empresa Azul", slug);
        company.setId(id);
        return company;
    }
}
