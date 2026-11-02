package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.DriverCompanySearchResponse;
import com.vanrides.vanrides.dto.DriverInvitePreviewResponse;
import com.vanrides.vanrides.dto.DriverOnboardingStatusResponse;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.MotoristaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DriverOnboardingService {

    private final MotoristaRepository motoristaRepository;
    private final DriverOnboardingStatusResolver statusResolver;
    private final DriverCompanySearchService companySearchService;
    private final DriverCompanyRequestService companyRequestService;
    private final DriverInviteRedemptionService inviteRedemptionService;

    public DriverOnboardingService(MotoristaRepository motoristaRepository,
                                   DriverOnboardingStatusResolver statusResolver,
                                   DriverCompanySearchService companySearchService,
                                   DriverCompanyRequestService companyRequestService,
                                   DriverInviteRedemptionService inviteRedemptionService) {
        this.motoristaRepository = motoristaRepository;
        this.statusResolver = statusResolver;
        this.companySearchService = companySearchService;
        this.companyRequestService = companyRequestService;
        this.inviteRedemptionService = inviteRedemptionService;
    }

    public DriverOnboardingStatusResponse resolveStatusForLogin(Motorista motorista) {
        return statusResolver.resolve(motorista);
    }

    public DriverOnboardingStatusResponse getStatus(Long motoristaId) {
        return statusResolver.resolve(requireMotorista(motoristaId));
    }

    public List<DriverCompanySearchResponse> searchCompanies(String query) {
        return companySearchService.searchCompanies(query);
    }

    public DriverOnboardingStatusResponse createRequest(Long motoristaId, Long companyId) {
        return companyRequestService.createRequest(motoristaId, companyId);
    }

    public DriverInvitePreviewResponse previewInvite(String token) {
        return inviteRedemptionService.preview(token);
    }

    public DriverOnboardingStatusResponse redeemInvite(Long motoristaId, String token) {
        return inviteRedemptionService.redeem(motoristaId, token);
    }

    @Transactional
    public Motorista requireOperationalDriver(Long motoristaId) {
        Motorista motorista = requireMotorista(motoristaId);
        if (motorista.getCompany() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Motorista ainda não possui empresa vinculada.");
        }
        return motorista;
    }

    private Motorista requireMotorista(Long motoristaId) {
        return motoristaRepository.findById(motoristaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado."));
    }
}
