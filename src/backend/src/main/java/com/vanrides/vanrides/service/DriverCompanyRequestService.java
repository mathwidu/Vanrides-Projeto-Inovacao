package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.DriverOnboardingStatusResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.DriverCompanyLinkRequest;
import com.vanrides.vanrides.model.DriverCompanyLinkRequestStatus;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.DriverCompanyLinkRequestRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class DriverCompanyRequestService {

    private final MotoristaRepository motoristaRepository;
    private final CompanyRepository companyRepository;
    private final DriverCompanyLinkRequestRepository requestRepository;
    private final DriverOnboardingStatusResolver statusResolver;

    public DriverCompanyRequestService(MotoristaRepository motoristaRepository,
                                       CompanyRepository companyRepository,
                                       DriverCompanyLinkRequestRepository requestRepository,
                                       DriverOnboardingStatusResolver statusResolver) {
        this.motoristaRepository = motoristaRepository;
        this.companyRepository = companyRepository;
        this.requestRepository = requestRepository;
        this.statusResolver = statusResolver;
    }

    @Transactional
    public DriverOnboardingStatusResponse createRequest(Long motoristaId, Long companyId) {
        Motorista motorista = requireMotorista(motoristaId);
        if (motorista.getCompany() != null) {
            return statusResolver.resolve(motorista);
        }

        Company company = companyRepository.findById(companyId)
                .filter(found -> !CompanyService.DEFAULT_COMPANY_SLUG.equals(found.getSlug()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));

        Optional<DriverCompanyLinkRequest> existingPending = requestRepository
                .findFirstByMotoristaIdAndStatusOrderByRequestedAtDesc(motoristaId, DriverCompanyLinkRequestStatus.PENDING);
        if (existingPending.isPresent()) {
            DriverCompanyLinkRequest request = existingPending.get();
            if (!request.getCompany().getId().equals(companyId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma solicitação pendente para este motorista.");
            }
            return statusResolver.resolve(motorista);
        }

        DriverCompanyLinkRequest request = new DriverCompanyLinkRequest();
        request.setMotorista(motorista);
        request.setCompany(company);
        request.setStatus(DriverCompanyLinkRequestStatus.PENDING);
        requestRepository.save(request);
        return statusResolver.resolve(motorista);
    }

    private Motorista requireMotorista(Long motoristaId) {
        return motoristaRepository.findById(motoristaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado."));
    }
}
