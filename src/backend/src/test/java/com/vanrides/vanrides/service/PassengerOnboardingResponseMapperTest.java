package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminCompanyLinkRequestDetailResponse;
import com.vanrides.vanrides.dto.AdminInviteCodeDetailResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyInviteCode;
import com.vanrides.vanrides.model.CompanyInviteCodeStatus;
import com.vanrides.vanrides.model.CompanyInviteCodeUsage;
import com.vanrides.vanrides.model.CompanyLinkRequest;
import com.vanrides.vanrides.model.CompanyLinkRequestSource;
import com.vanrides.vanrides.model.CompanyLinkRequestStatus;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyInviteCodeUsageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PassengerOnboardingResponseMapperTest {

    private final CompanyInviteCodeUsageRepository usageRepository = mock(CompanyInviteCodeUsageRepository.class);
    private final PassengerOnboardingResponseMapper mapper = new PassengerOnboardingResponseMapper(usageRepository);

    @Test
    void mapsAdminRequestDetailWithCurrentDriver() {
        Company company = company(66L, "Escola Van");
        Motorista motorista = motorista(44L, "Motorista Atual", "driver@vanrides.dev", "51999990044");
        Passageiro passageiro = passageiro(21L, "Aluno Um", "aluno@vanrides.dev", true);
        passageiro.setTelefone("51999990021");
        passageiro.setMotorista(motorista);

        CompanyLinkRequest request = new CompanyLinkRequest();
        ReflectionTestUtils.setField(request, "id", 90L);
        ReflectionTestUtils.setField(request, "requestedAt", OffsetDateTime.parse("2026-06-06T10:15:30Z"));
        request.setCompany(company);
        request.setPassageiro(passageiro);
        request.setSource(CompanyLinkRequestSource.SEARCH_REQUEST);
        request.setStatus(CompanyLinkRequestStatus.PENDING);

        AdminCompanyLinkRequestDetailResponse response = mapper.toAdminRequestDetail(request);

        assertThat(response.id()).isEqualTo(90L);
        assertThat(response.passageiroId()).isEqualTo(21L);
        assertThat(response.passageiroNome()).isEqualTo("Aluno Um");
        assertThat(response.passageiroTelefone()).isEqualTo("51999990021");
        assertThat(response.companyName()).isEqualTo("Escola Van");
        assertThat(response.source()).isEqualTo("SEARCH_REQUEST");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.emailVerified()).isTrue();
        assertThat(response.motoristaAtual().getId()).isEqualTo(44L);
        assertThat(response.motoristaAtual().getNome()).isEqualTo("Motorista Atual");
    }

    @Test
    void mapsInviteDetailWithEffectiveStatusAndUsages() {
        CompanyInviteCode inviteCode = new CompanyInviteCode();
        ReflectionTestUtils.setField(inviteCode, "id", 77L);
        ReflectionTestUtils.setField(inviteCode, "createdAt", OffsetDateTime.parse("2026-06-06T09:00:00Z"));
        inviteCode.setCode("INV-2026-ABCD");
        inviteCode.setStatus(CompanyInviteCodeStatus.ACTIVE);
        inviteCode.setMaxUses(2);
        inviteCode.setUseCount(1);
        inviteCode.setExpiresAt(OffsetDateTime.parse("2099-01-01T00:00:00Z"));
        inviteCode.setLastUsedAt(OffsetDateTime.parse("2026-06-06T10:05:00Z"));

        CompanyInviteCodeUsage olderUsage = inviteUsage(1L, inviteCode,
                passageiro(31L, "Aluno Antigo", "antigo@vanrides.dev", false),
                "2026-06-06T10:00:00Z");
        CompanyInviteCodeUsage newerUsage = inviteUsage(2L, inviteCode,
                passageiro(32L, "Aluno Novo", "novo@vanrides.dev", true),
                "2026-06-06T10:05:00Z");
        when(usageRepository.findAllByInviteCodeIdOrderByUsedAtDesc(77L))
                .thenReturn(List.of(olderUsage, newerUsage));

        AdminInviteCodeDetailResponse response = mapper.toInviteDetail(inviteCode);

        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.code()).isEqualTo("INV-2026-ABCD");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.maxUses()).isEqualTo(2);
        assertThat(response.useCount()).isEqualTo(1);
        assertThat(response.usages()).hasSize(2);
        assertThat(response.usages().get(0).passageiroId()).isEqualTo(32L);
        assertThat(response.usages().get(0).accountStatus()).isEqualTo("VERIFIED");
        assertThat(response.usages().get(1).passageiroId()).isEqualTo(31L);
        assertThat(response.usages().get(1).accountStatus()).isEqualTo("PENDING_EMAIL");
    }

    private Company company(Long id, String nome) {
        Company company = new Company(nome, "empresa-" + id);
        company.setId(id);
        return company;
    }

    private Motorista motorista(Long id, String nome, String email, String telefone) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome(nome);
        motorista.setEmail(email);
        motorista.setTelefone(telefone);
        return motorista;
    }

    private Passageiro passageiro(Long id, String nome, String email, boolean emailVerified) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome(nome);
        passageiro.setEmail(email);
        passageiro.setEmailVerified(emailVerified);
        return passageiro;
    }

    private CompanyInviteCodeUsage inviteUsage(Long id,
                                               CompanyInviteCode inviteCode,
                                               Passageiro passageiro,
                                               String usedAt) {
        CompanyInviteCodeUsage usage = new CompanyInviteCodeUsage();
        ReflectionTestUtils.setField(usage, "id", id);
        usage.setInviteCode(inviteCode);
        usage.setPassageiro(passageiro);
        usage.setUsedAt(OffsetDateTime.parse(usedAt));
        return usage;
    }
}
