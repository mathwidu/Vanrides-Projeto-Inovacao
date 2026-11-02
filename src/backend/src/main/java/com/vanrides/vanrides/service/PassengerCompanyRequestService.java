package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.PassengerOnboardingStatusResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyLinkRequest;
import com.vanrides.vanrides.model.CompanyLinkRequestSource;
import com.vanrides.vanrides.model.CompanyLinkRequestStatus;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyLinkRequestRepository;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class PassengerCompanyRequestService {

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyLinkRequestRepository companyLinkRequestRepository;

    @Autowired
    private PassengerOnboardingStatusResolver statusResolver;

    @Transactional
    public PassengerOnboardingStatusResponse createRequest(Long passageiroId, Long companyId) {
        Passageiro passageiro = requirePassageiro(passageiroId);
        if (passageiro.getCompany() != null) {
            return statusResolver.resolve(passageiro);
        }

        Company company = companyRepository.findById(companyId)
                .filter(found -> !CompanyService.DEFAULT_COMPANY_SLUG.equals(found.getSlug()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));

        Optional<CompanyLinkRequest> existingPending = companyLinkRequestRepository
                .findFirstByPassageiroIdAndStatusOrderByRequestedAtDesc(passageiroId, CompanyLinkRequestStatus.PENDING);
        if (existingPending.isPresent()) {
            CompanyLinkRequest request = existingPending.get();
            if (!request.getCompany().getId().equals(companyId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma solicitação pendente para este passageiro.");
            }
            return statusResolver.resolve(passageiro);
        }

        CompanyLinkRequest request = new CompanyLinkRequest();
        request.setPassageiro(passageiro);
        request.setCompany(company);
        request.setSource(CompanyLinkRequestSource.SEARCH_REQUEST);
        request.setStatus(CompanyLinkRequestStatus.PENDING);
        companyLinkRequestRepository.save(request);
        return statusResolver.resolve(passageiro);
    }

    private Passageiro requirePassageiro(Long passageiroId) {
        return passageiroRepository.findById(passageiroId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado."));
    }
}
