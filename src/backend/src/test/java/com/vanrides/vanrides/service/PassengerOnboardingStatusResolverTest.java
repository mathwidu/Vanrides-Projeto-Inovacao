package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.PassengerOnboardingStatusResponse;
import com.vanrides.vanrides.dto.PendingCompanyLinkRequestResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyLinkRequest;
import com.vanrides.vanrides.model.CompanyLinkRequestStatus;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyLinkRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerOnboardingStatusResolverTest {

    @Mock
    private CompanyLinkRequestRepository companyLinkRequestRepository;

    @Mock
    private PassengerOnboardingResponseMapper responseMapper;

    @Test
    void returnsLinkedStatusWhenPassengerAlreadyHasCompany() {
        PassengerOnboardingStatusResolver resolver = resolver();
        Company company = company(66L, "Escola Van");
        Motorista motorista = motorista(7L);
        Passageiro passageiro = passageiro(21L);
        passageiro.setCompany(company);
        passageiro.setMotorista(motorista);

        PassengerOnboardingStatusResponse response = resolver.resolve(passageiro);

        assertThat(response.companyLinkStatus()).isEqualTo("COMPANY_LINKED");
        assertThat(response.companyId()).isEqualTo(66L);
        assertThat(response.companyName()).isEqualTo("Escola Van");
        assertThat(response.motoristaId()).isEqualTo(7L);
        assertThat(response.pendingRequest()).isNull();
        verify(companyLinkRequestRepository, never())
                .findFirstByPassageiroIdAndStatusOrderByRequestedAtDesc(21L, CompanyLinkRequestStatus.PENDING);
    }

    @Test
    void returnsPendingApprovalWhenPassengerHasOpenRequest() {
        PassengerOnboardingStatusResolver resolver = resolver();
        Passageiro passageiro = passageiro(22L);
        CompanyLinkRequest request = new CompanyLinkRequest();
        PendingCompanyLinkRequestResponse pendingResponse = new PendingCompanyLinkRequestResponse(
                8L,
                66L,
                "Escola Van",
                "PENDING",
                OffsetDateTime.parse("2026-06-13T10:15:30Z")
        );

        when(companyLinkRequestRepository.findFirstByPassageiroIdAndStatusOrderByRequestedAtDesc(
                22L,
                CompanyLinkRequestStatus.PENDING
        )).thenReturn(Optional.of(request));
        when(responseMapper.toPendingRequest(request)).thenReturn(pendingResponse);

        PassengerOnboardingStatusResponse response = resolver.resolve(passageiro);

        assertThat(response.companyLinkStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.companyId()).isNull();
        assertThat(response.companyName()).isNull();
        assertThat(response.motoristaId()).isNull();
        assertThat(response.pendingRequest()).isSameAs(pendingResponse);
    }

    @Test
    void returnsNoCompanyWhenPassengerHasNoCompanyOrPendingRequest() {
        PassengerOnboardingStatusResolver resolver = resolver();
        Passageiro passageiro = passageiro(23L);
        when(companyLinkRequestRepository.findFirstByPassageiroIdAndStatusOrderByRequestedAtDesc(
                23L,
                CompanyLinkRequestStatus.PENDING
        )).thenReturn(Optional.empty());

        PassengerOnboardingStatusResponse response = resolver.resolve(passageiro);

        assertThat(response.companyLinkStatus()).isEqualTo("NO_COMPANY");
        assertThat(response.companyId()).isNull();
        assertThat(response.companyName()).isNull();
        assertThat(response.motoristaId()).isNull();
        assertThat(response.pendingRequest()).isNull();
    }

    @Test
    void returnsNoCompanyWhenPassengerIsNull() {
        PassengerOnboardingStatusResolver resolver = resolver();

        PassengerOnboardingStatusResponse response = resolver.resolve(null);

        assertThat(response.companyLinkStatus()).isEqualTo("NO_COMPANY");
        assertThat(response.companyId()).isNull();
        assertThat(response.companyName()).isNull();
        assertThat(response.motoristaId()).isNull();
        assertThat(response.pendingRequest()).isNull();
    }

    private PassengerOnboardingStatusResolver resolver() {
        return new PassengerOnboardingStatusResolver(companyLinkRequestRepository, responseMapper);
    }

    private Passageiro passageiro(Long id) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Aluno " + id);
        passageiro.setEmail("aluno" + id + "@vanrides.dev");
        return passageiro;
    }

    private Company company(Long id, String nome) {
        Company company = new Company(nome, "empresa-" + id);
        company.setId(id);
        return company;
    }

    private Motorista motorista(Long id) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome("Motorista " + id);
        motorista.setEmail("motorista" + id + "@vanrides.dev");
        return motorista;
    }
}
