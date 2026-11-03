package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.PassengerOnboardingStatusResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyInviteCode;
import com.vanrides.vanrides.model.CompanyInviteCodeStatus;
import com.vanrides.vanrides.model.CompanyInviteCodeUsage;
import com.vanrides.vanrides.model.CompanyLinkRequest;
import com.vanrides.vanrides.model.CompanyLinkRequestStatus;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyInviteCodeRepository;
import com.vanrides.vanrides.repository.CompanyInviteCodeUsageRepository;
import com.vanrides.vanrides.repository.CompanyLinkRequestRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerInviteRedemptionServiceTest {

    @Mock
    private PassageiroRepository passageiroRepository;

    @Mock
    private CompanyInviteCodeRepository companyInviteCodeRepository;

    @Mock
    private CompanyInviteCodeUsageRepository companyInviteCodeUsageRepository;

    @Mock
    private CompanyLinkRequestRepository companyLinkRequestRepository;

    @Mock
    private PassengerCompanyLinker passengerCompanyLinker;

    @Mock
    private PassengerOnboardingStatusResolver statusResolver;

    @Mock
    private CompanyInviteCodePolicy inviteCodePolicy;

    @InjectMocks
    private PassengerInviteRedemptionService service;

    @Test
    void shouldRedeemInviteAndCancelPendingRequests() {
        Passageiro passageiro = passageiro(21L);
        Company company = company(55L);
        CompanyInviteCode inviteCode = inviteCode(company);
        CompanyLinkRequest pendingRequest = new CompanyLinkRequest();
        pendingRequest.setStatus(CompanyLinkRequestStatus.PENDING);
        PassengerOnboardingStatusResponse status = new PassengerOnboardingStatusResponse(
                "COMPANY_LINKED",
                55L,
                "Empresa Azul",
                null,
                null
        );

        when(passageiroRepository.findById(21L)).thenReturn(Optional.of(passageiro));
        when(inviteCodePolicy.normalizeCode(" inv-2026-abcd ")).thenReturn("INV-2026-ABCD");
        when(companyInviteCodeRepository.findByCode("INV-2026-ABCD")).thenReturn(Optional.of(inviteCode));
        when(inviteCodePolicy.isUsable(inviteCode)).thenReturn(true);
        when(companyLinkRequestRepository.findAllByPassageiroIdAndStatus(21L, CompanyLinkRequestStatus.PENDING))
                .thenReturn(List.of(pendingRequest));
        when(statusResolver.resolve(passageiro)).thenReturn(status);

        PassengerOnboardingStatusResponse result = service.redeemInviteCode(21L, " inv-2026-abcd ");

        ArgumentCaptor<CompanyInviteCodeUsage> usageCaptor = ArgumentCaptor.forClass(CompanyInviteCodeUsage.class);
        org.mockito.Mockito.verify(passengerCompanyLinker).link(passageiro, company, null);
        org.mockito.Mockito.verify(companyInviteCodeUsageRepository).save(usageCaptor.capture());
        org.mockito.Mockito.verify(companyInviteCodeRepository).save(inviteCode);
        assertThat(result).isSameAs(status);
        assertThat(pendingRequest.getStatus()).isEqualTo(CompanyLinkRequestStatus.CANCELLED);
        assertThat(inviteCode.getUseCount()).isEqualTo(1);
        assertThat(inviteCode.getStatus()).isEqualTo(CompanyInviteCodeStatus.USED_UP);
        assertThat(usageCaptor.getValue().getInviteCode()).isSameAs(inviteCode);
        assertThat(usageCaptor.getValue().getPassageiro()).isSameAs(passageiro);
    }

    @Test
    void shouldRejectUnknownInviteCode() {
        Passageiro passageiro = passageiro(21L);

        when(passageiroRepository.findById(21L)).thenReturn(Optional.of(passageiro));
        when(inviteCodePolicy.normalizeCode("codigo-invalido")).thenReturn("CODIGO-INVALIDO");
        when(companyInviteCodeRepository.findByCode("CODIGO-INVALIDO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeemInviteCode(21L, "codigo-invalido"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRejectExpiredInviteCodeAtRedemptionBoundary() {
        Passageiro passageiro = passageiro(21L);
        CompanyInviteCode inviteCode = inviteCode(company(55L));
        inviteCode.setStatus(CompanyInviteCodeStatus.EXPIRED);

        when(passageiroRepository.findById(21L)).thenReturn(Optional.of(passageiro));
        when(inviteCodePolicy.normalizeCode("INV-2026-ABCD")).thenReturn("INV-2026-ABCD");
        when(companyInviteCodeRepository.findByCode("INV-2026-ABCD")).thenReturn(Optional.of(inviteCode));
        when(inviteCodePolicy.isUsable(inviteCode)).thenReturn(false);

        assertThatThrownBy(() -> service.redeemInviteCode(21L, "INV-2026-ABCD"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Passageiro passageiro(Long id) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Passageiro " + id);
        passageiro.setEmail("passageiro" + id + "@vanrides.dev");
        return passageiro;
    }

    private Company company(Long id) {
        Company company = new Company("Empresa Azul", "empresa-azul");
        company.setId(id);
        return company;
    }

    private CompanyInviteCode inviteCode(Company company) {
        CompanyInviteCode inviteCode = new CompanyInviteCode();
        inviteCode.setCompany(company);
        inviteCode.setCode("INV-2026-ABCD");
        inviteCode.setStatus(CompanyInviteCodeStatus.ACTIVE);
        inviteCode.setUseCount(0);
        inviteCode.setMaxUses(1);
        return inviteCode;
    }
}
