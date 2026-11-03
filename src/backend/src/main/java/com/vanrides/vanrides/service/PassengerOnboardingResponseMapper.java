package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminCompanyLinkRequestDetailResponse;
import com.vanrides.vanrides.dto.AdminCompanyLinkRequestSummaryResponse;
import com.vanrides.vanrides.dto.AdminInviteCodeDetailResponse;
import com.vanrides.vanrides.dto.AdminInviteCodeSummaryResponse;
import com.vanrides.vanrides.dto.AdminInviteCodeUsageResponse;
import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.PendingCompanyLinkRequestResponse;
import com.vanrides.vanrides.model.CompanyInviteCode;
import com.vanrides.vanrides.model.CompanyInviteCodeStatus;
import com.vanrides.vanrides.model.CompanyInviteCodeUsage;
import com.vanrides.vanrides.model.CompanyLinkRequest;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyInviteCodeUsageRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Component
public class PassengerOnboardingResponseMapper {

    private final CompanyInviteCodeUsageRepository companyInviteCodeUsageRepository;

    public PassengerOnboardingResponseMapper(CompanyInviteCodeUsageRepository companyInviteCodeUsageRepository) {
        this.companyInviteCodeUsageRepository = companyInviteCodeUsageRepository;
    }

    PendingCompanyLinkRequestResponse toPendingRequest(CompanyLinkRequest request) {
        return new PendingCompanyLinkRequestResponse(
                request.getId(),
                request.getCompany().getId(),
                request.getCompany().getNome(),
                request.getStatus().name(),
                request.getRequestedAt()
        );
    }

    AdminCompanyLinkRequestSummaryResponse toAdminRequestSummary(CompanyLinkRequest request) {
        return new AdminCompanyLinkRequestSummaryResponse(
                request.getId(),
                request.getPassageiro().getId(),
                request.getPassageiro().getNome(),
                request.getPassageiro().getEmail(),
                request.getCompany().getNome(),
                request.getSource().name(),
                request.getRequestedAt(),
                request.getPassageiro().isEmailVerified()
        );
    }

    AdminCompanyLinkRequestDetailResponse toAdminRequestDetail(CompanyLinkRequest request) {
        Passageiro passageiro = request.getPassageiro();
        return new AdminCompanyLinkRequestDetailResponse(
                request.getId(),
                passageiro.getId(),
                passageiro.getNome(),
                passageiro.getEmail(),
                passageiro.getTelefone(),
                request.getCompany().getNome(),
                request.getSource().name(),
                request.getStatus().name(),
                request.getRequestedAt(),
                passageiro.isEmailVerified(),
                toMotoristaSummary(passageiro.getMotorista())
        );
    }

    AdminInviteCodeSummaryResponse toInviteSummary(CompanyInviteCode inviteCode) {
        return new AdminInviteCodeSummaryResponse(
                inviteCode.getId(),
                inviteCode.getCode(),
                effectiveInviteStatus(inviteCode).name(),
                inviteCode.getMaxUses(),
                inviteCode.getUseCount(),
                inviteCode.getExpiresAt(),
                inviteCode.getCreatedAt()
        );
    }

    AdminInviteCodeDetailResponse toInviteDetail(CompanyInviteCode inviteCode) {
        List<AdminInviteCodeUsageResponse> usages = companyInviteCodeUsageRepository.findAllByInviteCodeIdOrderByUsedAtDesc(inviteCode.getId())
                .stream()
                .sorted(Comparator.comparing(CompanyInviteCodeUsage::getUsedAt).reversed())
                .map(usage -> new AdminInviteCodeUsageResponse(
                        usage.getId(),
                        usage.getPassageiro().getId(),
                        usage.getPassageiro().getNome(),
                        usage.getPassageiro().getEmail(),
                        usage.getUsedAt(),
                        usage.getPassageiro().isEmailVerified() ? "VERIFIED" : "PENDING_EMAIL"
                ))
                .toList();
        return new AdminInviteCodeDetailResponse(
                inviteCode.getId(),
                inviteCode.getCode(),
                effectiveInviteStatus(inviteCode).name(),
                inviteCode.getMaxUses(),
                inviteCode.getUseCount(),
                inviteCode.getExpiresAt(),
                inviteCode.getCreatedAt(),
                inviteCode.getLastUsedAt(),
                usages
        );
    }

    private AdminMotoristaSummaryResponse toMotoristaSummary(Motorista motorista) {
        if (motorista == null) {
            return null;
        }
        return new AdminMotoristaSummaryResponse(
                motorista.getId(),
                motorista.getNome(),
                motorista.getEmail(),
                motorista.getTelefone(),
                motorista.isEmailVerified(),
                motorista.getDataNascimento(),
                motorista.getFotoPerfilUrl()
        );
    }

    private CompanyInviteCodeStatus effectiveInviteStatus(CompanyInviteCode inviteCode) {
        if (inviteCode.getStatus() == CompanyInviteCodeStatus.CANCELLED) {
            return CompanyInviteCodeStatus.CANCELLED;
        }
        if (inviteCode.getExpiresAt() != null && inviteCode.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return CompanyInviteCodeStatus.EXPIRED;
        }
        if (inviteCode.getMaxUses() != null && inviteCode.getUseCount() >= inviteCode.getMaxUses()) {
            return CompanyInviteCodeStatus.USED_UP;
        }
        return CompanyInviteCodeStatus.ACTIVE;
    }
}
