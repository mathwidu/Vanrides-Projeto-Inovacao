package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminDriverInviteResponse;
import com.vanrides.vanrides.dto.AdminDriverLinkRequestResponse;
import com.vanrides.vanrides.dto.PendingDriverCompanyLinkRequestResponse;
import com.vanrides.vanrides.model.DriverCompanyInvite;
import com.vanrides.vanrides.model.DriverCompanyInviteStatus;
import com.vanrides.vanrides.model.DriverCompanyLinkRequest;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class DriverOnboardingResponseMapper {

    PendingDriverCompanyLinkRequestResponse toPendingRequest(DriverCompanyLinkRequest request) {
        return new PendingDriverCompanyLinkRequestResponse(
                request.getId(),
                request.getCompany().getId(),
                request.getCompany().getNome(),
                request.getStatus().name(),
                request.getRequestedAt()
        );
    }

    AdminDriverLinkRequestResponse toAdminRequest(DriverCompanyLinkRequest request) {
        return new AdminDriverLinkRequestResponse(
                request.getId(),
                request.getMotorista().getId(),
                request.getMotorista().getNome(),
                request.getMotorista().getEmail(),
                request.getMotorista().getTelefone(),
                request.getCompany().getId(),
                request.getCompany().getNome(),
                request.getStatus().name(),
                request.getRequestedAt(),
                request.getReviewedAt(),
                request.getMotorista().isEmailVerified(),
                request.getReviewNotes()
        );
    }

    AdminDriverInviteResponse toAdminInvite(DriverCompanyInvite invite, String rawToken, String shareUrl) {
        return new AdminDriverInviteResponse(
                invite.getId(),
                invite.getCompany().getId(),
                invite.getCompany().getNome(),
                rawToken,
                shareUrl,
                effectiveInviteStatus(invite).name(),
                invite.getExpiresAt(),
                invite.getCreatedAt(),
                invite.getAcceptedAt(),
                invite.getAcceptedBy() != null ? invite.getAcceptedBy().getId() : null,
                invite.getAcceptedBy() != null ? invite.getAcceptedBy().getNome() : null
        );
    }

    DriverCompanyInviteStatus effectiveInviteStatus(DriverCompanyInvite invite) {
        if (invite.getStatus() == DriverCompanyInviteStatus.CANCELLED
                || invite.getStatus() == DriverCompanyInviteStatus.USED) {
            return invite.getStatus();
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return DriverCompanyInviteStatus.EXPIRED;
        }
        return invite.getStatus();
    }
}
