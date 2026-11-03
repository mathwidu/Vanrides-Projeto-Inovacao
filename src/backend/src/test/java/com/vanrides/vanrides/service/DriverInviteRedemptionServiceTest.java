package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.DriverInvitePreviewResponse;
import com.vanrides.vanrides.dto.DriverOnboardingStatusResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.DriverCompanyInvite;
import com.vanrides.vanrides.model.DriverCompanyInviteStatus;
import com.vanrides.vanrides.model.DriverCompanyMembership;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.DriverCompanyInviteRepository;
import com.vanrides.vanrides.repository.DriverCompanyLinkRequestRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverInviteRedemptionServiceTest {

    @Mock
    private MotoristaRepository motoristaRepository;

    @Mock
    private DriverCompanyInviteRepository inviteRepository;

    @Mock
    private DriverCompanyLinkRequestRepository requestRepository;

    @Mock
    private DriverInviteTokenService tokenService;

    @Mock
    private DriverCompanyLinker driverCompanyLinker;

    @Mock
    private DriverOnboardingStatusResolver statusResolver;

    @Mock
    private DriverOnboardingResponseMapper responseMapper;

    @InjectMocks
    private DriverInviteRedemptionService service;

    @Test
    void shouldPreviewValidInviteToken() {
        DriverCompanyInvite invite = invite(company(55L));
        when(tokenService.hashToken("token-raw")).thenReturn("token-hash");
        when(inviteRepository.findByTokenHash("token-hash")).thenReturn(Optional.of(invite));
        when(responseMapper.effectiveInviteStatus(invite)).thenReturn(DriverCompanyInviteStatus.ACTIVE);

        DriverInvitePreviewResponse response = service.preview("token-raw");

        assertThat(response.companyId()).isEqualTo(55L);
        assertThat(response.companyName()).isEqualTo("Empresa Azul");
        assertThat(response.role()).isEqualTo("DRIVER");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldLinkVerifiedDriverWhenRedeemingInvite() {
        Company company = company(55L);
        DriverCompanyInvite invite = invite(company);
        Motorista motorista = motorista(21L, true, null);
        DriverCompanyMembership membership = new DriverCompanyMembership();
        membership.setMotorista(motorista);
        membership.setCompany(company);

        when(motoristaRepository.findById(21L)).thenReturn(Optional.of(motorista));
        when(tokenService.hashToken("token-raw")).thenReturn("token-hash");
        when(inviteRepository.findByTokenHash("token-hash")).thenReturn(Optional.of(invite));
        when(responseMapper.effectiveInviteStatus(invite)).thenReturn(DriverCompanyInviteStatus.ACTIVE);
        when(driverCompanyLinker.link(motorista, company, "INVITE_LINK", null)).thenReturn(membership);
        when(requestRepository.findAllByMotoristaIdAndStatus(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        DriverOnboardingStatusResponse response = service.redeem(21L, "token-raw");

        assertThat(response.companyLinkStatus()).isEqualTo("COMPANY_LINKED");
        assertThat(response.companyId()).isEqualTo(55L);
        assertThat(invite.getStatus()).isEqualTo(DriverCompanyInviteStatus.USED);
        assertThat(invite.getAcceptedBy()).isSameAs(motorista);
        assertThat(invite.getAcceptedAt()).isNotNull();
        verify(inviteRepository).save(invite);
    }

    @Test
    void shouldAskForEmailVerificationBeforeLinkingUnverifiedDriver() {
        Company company = company(55L);
        DriverCompanyInvite invite = invite(company);
        Motorista motorista = motorista(21L, false, null);

        when(motoristaRepository.findById(21L)).thenReturn(Optional.of(motorista));
        when(tokenService.hashToken("token-raw")).thenReturn("token-hash");
        when(inviteRepository.findByTokenHash("token-hash")).thenReturn(Optional.of(invite));
        when(responseMapper.effectiveInviteStatus(invite)).thenReturn(DriverCompanyInviteStatus.ACTIVE);

        DriverOnboardingStatusResponse response = service.redeem(21L, "token-raw");

        assertThat(response.companyLinkStatus()).isEqualTo("EMAIL_VERIFICATION_REQUIRED");
        assertThat(response.companyId()).isEqualTo(55L);
        verify(driverCompanyLinker, never()).link(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(inviteRepository, never()).save(invite);
    }

    @Test
    void shouldReturnGoneForExpiredInvite() {
        DriverCompanyInvite invite = invite(company(55L));
        when(tokenService.hashToken("token-raw")).thenReturn("token-hash");
        when(inviteRepository.findByTokenHash("token-hash")).thenReturn(Optional.of(invite));
        when(responseMapper.effectiveInviteStatus(invite)).thenReturn(DriverCompanyInviteStatus.EXPIRED);

        assertThatThrownBy(() -> service.preview("token-raw"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("410 GONE");
    }

    private DriverCompanyInvite invite(Company company) {
        DriverCompanyInvite invite = new DriverCompanyInvite();
        invite.setCompany(company);
        invite.setTokenHash("token-hash");
        invite.setStatus(DriverCompanyInviteStatus.ACTIVE);
        invite.setExpiresAt(OffsetDateTime.now().plusDays(3));
        return invite;
    }

    private Motorista motorista(Long id, boolean emailVerified, Company company) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome("Motorista " + id);
        motorista.setEmail("motorista" + id + "@vanrides.dev");
        motorista.setTelefone("51999999999");
        motorista.setEmailVerified(emailVerified);
        motorista.setCompany(company);
        return motorista;
    }

    private Company company(Long id) {
        Company company = new Company("Empresa Azul", "empresa-azul");
        company.setId(id);
        return company;
    }
}
