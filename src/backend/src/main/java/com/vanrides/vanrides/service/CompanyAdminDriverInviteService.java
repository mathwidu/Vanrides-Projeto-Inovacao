package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminDriverInviteCreateRequest;
import com.vanrides.vanrides.dto.AdminDriverInviteResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyAdmin;
import com.vanrides.vanrides.model.DriverCompanyInvite;
import com.vanrides.vanrides.model.DriverCompanyInviteStatus;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.DriverCompanyInviteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class CompanyAdminDriverInviteService {

    private final CompanyRepository companyRepository;
    private final CompanyAdminRepository companyAdminRepository;
    private final DriverCompanyInviteRepository inviteRepository;
    private final DriverInviteTokenService tokenService;
    private final DriverInviteRedemptionService redemptionService;
    private final DriverOnboardingResponseMapper responseMapper;

    @Value("${vanrides.app.driver-invite-url-template:vanrides://driver-invite/{token}}")
    private String driverInviteUrlTemplate;

    public CompanyAdminDriverInviteService(CompanyRepository companyRepository,
                                           CompanyAdminRepository companyAdminRepository,
                                           DriverCompanyInviteRepository inviteRepository,
                                           DriverInviteTokenService tokenService,
                                           DriverInviteRedemptionService redemptionService,
                                           DriverOnboardingResponseMapper responseMapper) {
        this.companyRepository = companyRepository;
        this.companyAdminRepository = companyAdminRepository;
        this.inviteRepository = inviteRepository;
        this.tokenService = tokenService;
        this.redemptionService = redemptionService;
        this.responseMapper = responseMapper;
    }

    public List<AdminDriverInviteResponse> listInvites(Long companyId) {
        redemptionService.expireStaleInvites();
        return inviteRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(invite -> responseMapper.toAdminInvite(invite, null, null))
                .toList();
    }

    @Transactional
    public AdminDriverInviteResponse createInvite(Long companyId,
                                                  Long adminId,
                                                  AdminDriverInviteCreateRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));
        CompanyAdmin admin = companyAdminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrador não encontrado."));

        String rawToken = tokenService.generateRawToken();
        DriverCompanyInvite invite = new DriverCompanyInvite();
        invite.setCompany(company);
        invite.setCreatedBy(admin);
        invite.setTokenHash(tokenService.hashToken(rawToken));
        invite.setExpiresAt(request != null ? request.expiresAt() : null);
        invite.setStatus(DriverCompanyInviteStatus.ACTIVE);

        DriverCompanyInvite saved = inviteRepository.save(invite);
        return responseMapper.toAdminInvite(saved, rawToken, buildShareUrl(rawToken));
    }

    @Transactional
    public AdminDriverInviteResponse cancelInvite(Long inviteId, Long companyId) {
        DriverCompanyInvite invite = inviteRepository.findByIdAndCompanyId(inviteId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite de motorista não encontrado."));
        if (invite.getStatus() == DriverCompanyInviteStatus.ACTIVE) {
            invite.setStatus(DriverCompanyInviteStatus.CANCELLED);
            invite.setCancelledAt(OffsetDateTime.now());
        }
        return responseMapper.toAdminInvite(invite, null, null);
    }

    private String buildShareUrl(String rawToken) {
        return driverInviteUrlTemplate.replace("{token}", rawToken);
    }
}
