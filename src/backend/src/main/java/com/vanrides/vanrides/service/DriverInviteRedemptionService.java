package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.DriverInvitePreviewResponse;
import com.vanrides.vanrides.dto.DriverOnboardingStatusResponse;
import com.vanrides.vanrides.model.DriverCompanyInvite;
import com.vanrides.vanrides.model.DriverCompanyInviteStatus;
import com.vanrides.vanrides.model.DriverCompanyLinkRequestStatus;
import com.vanrides.vanrides.model.DriverCompanyMembership;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.DriverCompanyInviteRepository;
import com.vanrides.vanrides.repository.DriverCompanyLinkRequestRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@Service
public class DriverInviteRedemptionService {

    private static final String STATUS_EMAIL_VERIFICATION_REQUIRED = "EMAIL_VERIFICATION_REQUIRED";

    private final MotoristaRepository motoristaRepository;
    private final DriverCompanyInviteRepository inviteRepository;
    private final DriverCompanyLinkRequestRepository requestRepository;
    private final DriverInviteTokenService tokenService;
    private final DriverCompanyLinker driverCompanyLinker;
    private final DriverOnboardingStatusResolver statusResolver;
    private final DriverOnboardingResponseMapper responseMapper;

    public DriverInviteRedemptionService(MotoristaRepository motoristaRepository,
                                         DriverCompanyInviteRepository inviteRepository,
                                         DriverCompanyLinkRequestRepository requestRepository,
                                         DriverInviteTokenService tokenService,
                                         DriverCompanyLinker driverCompanyLinker,
                                         DriverOnboardingStatusResolver statusResolver,
                                         DriverOnboardingResponseMapper responseMapper) {
        this.motoristaRepository = motoristaRepository;
        this.inviteRepository = inviteRepository;
        this.requestRepository = requestRepository;
        this.tokenService = tokenService;
        this.driverCompanyLinker = driverCompanyLinker;
        this.statusResolver = statusResolver;
        this.responseMapper = responseMapper;
    }

    @Transactional
    public DriverInvitePreviewResponse preview(String rawToken) {
        expireStaleInvites();
        DriverCompanyInvite invite = requireInvite(rawToken);
        DriverCompanyInviteStatus status = responseMapper.effectiveInviteStatus(invite);
        if (status == DriverCompanyInviteStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.GONE, "Convite expirado.");
        }
        if (status != DriverCompanyInviteStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Convite indisponível.");
        }
        return new DriverInvitePreviewResponse(
                invite.getCompany().getId(),
                invite.getCompany().getNome(),
                "DRIVER",
                status.name(),
                invite.getExpiresAt()
        );
    }

    @Transactional
    public DriverOnboardingStatusResponse redeem(Long motoristaId, String rawToken) {
        Motorista motorista = requireMotorista(motoristaId);
        expireStaleInvites();
        DriverCompanyInvite invite = requireInvite(rawToken);
        assertInviteUsable(invite);

        if (motorista.getCompany() != null) {
            if (!motorista.getCompany().getId().equals(invite.getCompany().getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Motorista já está vinculado a outra empresa.");
            }
            return statusResolver.resolve(motorista);
        }

        if (!motorista.isEmailVerified()) {
            return new DriverOnboardingStatusResponse(
                    STATUS_EMAIL_VERIFICATION_REQUIRED,
                    invite.getCompany().getId(),
                    invite.getCompany().getNome(),
                    null,
                    null
            );
        }

        DriverCompanyMembership membership = driverCompanyLinker.link(motorista, invite.getCompany(), "INVITE_LINK", null);
        requestRepository.findAllByMotoristaIdAndStatus(motoristaId, DriverCompanyLinkRequestStatus.PENDING)
                .forEach(request -> {
                    request.setStatus(DriverCompanyLinkRequestStatus.CANCELLED);
                    request.setReviewedAt(OffsetDateTime.now());
                    request.setReviewNotes("Cancelada após vínculo por convite da empresa.");
                });

        invite.setStatus(DriverCompanyInviteStatus.USED);
        invite.setAcceptedBy(motorista);
        invite.setAcceptedAt(OffsetDateTime.now());
        inviteRepository.save(invite);

        return new DriverOnboardingStatusResponse(
                "COMPANY_LINKED",
                invite.getCompany().getId(),
                invite.getCompany().getNome(),
                membership.getId(),
                null
        );
    }

    @Transactional
    public void expireStaleInvites() {
        inviteRepository.findAllByStatusAndExpiresAtBefore(DriverCompanyInviteStatus.ACTIVE, OffsetDateTime.now())
                .forEach(invite -> invite.setStatus(DriverCompanyInviteStatus.EXPIRED));
    }

    private DriverCompanyInvite requireInvite(String rawToken) {
        return inviteRepository.findByTokenHash(tokenService.hashToken(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite expirado ou não encontrado."));
    }

    private void assertInviteUsable(DriverCompanyInvite invite) {
        DriverCompanyInviteStatus status = responseMapper.effectiveInviteStatus(invite);
        if (status == DriverCompanyInviteStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.GONE, "Convite expirado.");
        }
        if (status != DriverCompanyInviteStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Convite indisponível.");
        }
    }

    private Motorista requireMotorista(Long motoristaId) {
        return motoristaRepository.findById(motoristaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado."));
    }
}
