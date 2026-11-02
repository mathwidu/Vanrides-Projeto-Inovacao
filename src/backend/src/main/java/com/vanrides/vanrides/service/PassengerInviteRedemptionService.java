package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.PassengerOnboardingStatusResponse;
import com.vanrides.vanrides.model.CompanyInviteCode;
import com.vanrides.vanrides.model.CompanyInviteCodeStatus;
import com.vanrides.vanrides.model.CompanyInviteCodeUsage;
import com.vanrides.vanrides.model.CompanyLinkRequestStatus;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyInviteCodeRepository;
import com.vanrides.vanrides.repository.CompanyInviteCodeUsageRepository;
import com.vanrides.vanrides.repository.CompanyLinkRequestRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@Service
public class PassengerInviteRedemptionService {

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private CompanyInviteCodeRepository companyInviteCodeRepository;

    @Autowired
    private CompanyInviteCodeUsageRepository companyInviteCodeUsageRepository;

    @Autowired
    private CompanyLinkRequestRepository companyLinkRequestRepository;

    @Autowired
    private PassengerCompanyLinker passengerCompanyLinker;

    @Autowired
    private PassengerOnboardingStatusResolver statusResolver;

    @Autowired
    private CompanyInviteCodePolicy inviteCodePolicy;

    @Transactional
    public PassengerOnboardingStatusResponse redeemInviteCode(Long passageiroId, String rawCode) {
        Passageiro passageiro = requirePassageiro(passageiroId);
        if (passageiro.getCompany() != null) {
            return statusResolver.resolve(passageiro);
        }

        expireStaleInviteCodes();
        String normalizedCode = inviteCodePolicy.normalizeCode(rawCode);
        CompanyInviteCode inviteCode = companyInviteCodeRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Código expirado ou não encontrado."));

        if (!inviteCodePolicy.isUsable(inviteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código expirado ou não encontrado.");
        }

        passengerCompanyLinker.link(passageiro, inviteCode.getCompany(), null);
        companyLinkRequestRepository.findAllByPassageiroIdAndStatus(passageiroId, CompanyLinkRequestStatus.PENDING)
                .forEach(request -> {
                    request.setStatus(CompanyLinkRequestStatus.CANCELLED);
                    request.setReviewedAt(OffsetDateTime.now());
                    request.setReviewNotes("Cancelada após resgate de convite.");
                });

        inviteCode.setUseCount(inviteCode.getUseCount() + 1);
        inviteCode.setLastUsedAt(OffsetDateTime.now());
        if (inviteCode.getMaxUses() != null && inviteCode.getUseCount() >= inviteCode.getMaxUses()) {
            inviteCode.setStatus(CompanyInviteCodeStatus.USED_UP);
        }

        CompanyInviteCodeUsage usage = new CompanyInviteCodeUsage();
        usage.setInviteCode(inviteCode);
        usage.setPassageiro(passageiro);
        companyInviteCodeUsageRepository.save(usage);
        companyInviteCodeRepository.save(inviteCode);

        return statusResolver.resolve(passageiro);
    }

    private Passageiro requirePassageiro(Long passageiroId) {
        return passageiroRepository.findById(passageiroId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado."));
    }

    private void expireStaleInviteCodes() {
        companyInviteCodeRepository.findAllByStatusAndExpiresAtBefore(CompanyInviteCodeStatus.ACTIVE, OffsetDateTime.now())
                .forEach(inviteCode -> inviteCode.setStatus(CompanyInviteCodeStatus.EXPIRED));
    }
}
