package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.DriverOnboardingStatusResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.DriverCompanyLinkRequest;
import com.vanrides.vanrides.model.DriverCompanyLinkRequestStatus;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.DriverCompanyLinkRequestRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
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
class DriverCompanyRequestServiceTest {

    @Mock
    private MotoristaRepository motoristaRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private DriverCompanyLinkRequestRepository requestRepository;

    @Mock
    private DriverOnboardingStatusResolver statusResolver;

    @InjectMocks
    private DriverCompanyRequestService service;

    @Test
    void shouldCreatePendingRequestForUnlinkedDriver() {
        Motorista motorista = motorista(21L, null);
        Company company = company(55L, "empresa-azul");
        DriverOnboardingStatusResponse status = new DriverOnboardingStatusResponse(
                "PENDING_APPROVAL",
                null,
                null,
                null,
                null
        );

        when(motoristaRepository.findById(21L)).thenReturn(Optional.of(motorista));
        when(companyRepository.findById(55L)).thenReturn(Optional.of(company));
        when(requestRepository.findFirstByMotoristaIdAndStatusOrderByRequestedAtDesc(21L, DriverCompanyLinkRequestStatus.PENDING))
                .thenReturn(Optional.empty());
        when(statusResolver.resolve(motorista)).thenReturn(status);

        DriverOnboardingStatusResponse result = service.createRequest(21L, 55L);

        ArgumentCaptor<DriverCompanyLinkRequest> captor = ArgumentCaptor.forClass(DriverCompanyLinkRequest.class);
        verify(requestRepository).save(captor.capture());
        assertThat(result).isSameAs(status);
        assertThat(captor.getValue().getMotorista()).isSameAs(motorista);
        assertThat(captor.getValue().getCompany()).isSameAs(company);
        assertThat(captor.getValue().getStatus()).isEqualTo(DriverCompanyLinkRequestStatus.PENDING);
    }

    @Test
    void shouldReturnCurrentStatusWhenDriverAlreadyHasCompany() {
        Company company = company(55L, "empresa-azul");
        Motorista motorista = motorista(21L, company);
        DriverOnboardingStatusResponse status = new DriverOnboardingStatusResponse(
                "COMPANY_LINKED",
                55L,
                "Empresa Azul",
                null,
                null
        );

        when(motoristaRepository.findById(21L)).thenReturn(Optional.of(motorista));
        when(statusResolver.resolve(motorista)).thenReturn(status);

        DriverOnboardingStatusResponse result = service.createRequest(21L, 55L);

        assertThat(result).isSameAs(status);
        verify(companyRepository, never()).findById(55L);
        verify(requestRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Motorista motorista(Long id, Company company) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome("Motorista " + id);
        motorista.setEmail("motorista" + id + "@vanrides.dev");
        motorista.setTelefone("51999999999");
        motorista.setCompany(company);
        return motorista;
    }

    private Company company(Long id, String slug) {
        Company company = new Company("Empresa Azul", slug);
        company.setId(id);
        return company;
    }
}
