package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminDriverLinkRequestResponse;
import com.vanrides.vanrides.model.CompanyAdmin;
import com.vanrides.vanrides.model.DriverCompanyLinkRequest;
import com.vanrides.vanrides.model.DriverCompanyLinkRequestStatus;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.DriverCompanyLinkRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class CompanyAdminDriverLinkRequestService {

    private final DriverCompanyLinkRequestRepository requestRepository;
    private final CompanyAdminRepository companyAdminRepository;
    private final DriverCompanyLinker driverCompanyLinker;
    private final DriverOnboardingResponseMapper responseMapper;

    public CompanyAdminDriverLinkRequestService(DriverCompanyLinkRequestRepository requestRepository,
                                                CompanyAdminRepository companyAdminRepository,
                                                DriverCompanyLinker driverCompanyLinker,
                                                DriverOnboardingResponseMapper responseMapper) {
        this.requestRepository = requestRepository;
        this.companyAdminRepository = companyAdminRepository;
        this.driverCompanyLinker = driverCompanyLinker;
        this.responseMapper = responseMapper;
    }

    public List<AdminDriverLinkRequestResponse> listPendingRequests(Long companyId) {
        return requestRepository.findAllByCompanyIdAndStatusOrderByRequestedAtAsc(companyId, DriverCompanyLinkRequestStatus.PENDING)
                .stream()
                .map(responseMapper::toAdminRequest)
                .toList();
    }

    @Transactional
    public AdminDriverLinkRequestResponse approveRequest(Long requestId, Long companyId, Long adminId, String reason) {
        DriverCompanyLinkRequest request = requireRequest(requestId, companyId);
        if (request.getStatus() != DriverCompanyLinkRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solicitação já foi processada.");
        }
        CompanyAdmin admin = requireAdmin(adminId);

        driverCompanyLinker.link(request.getMotorista(), request.getCompany(), "SEARCH_REQUEST", reason);
        request.setStatus(DriverCompanyLinkRequestStatus.APPROVED);
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedBy(admin);
        request.setReviewNotes(reason);

        requestRepository.findAllByMotoristaIdAndStatus(request.getMotorista().getId(), DriverCompanyLinkRequestStatus.PENDING)
                .stream()
                .filter(other -> !other.getId().equals(request.getId()))
                .forEach(other -> {
                    other.setStatus(DriverCompanyLinkRequestStatus.CANCELLED);
                    other.setReviewedAt(OffsetDateTime.now());
                    other.setReviewedBy(admin);
                    other.setReviewNotes("Cancelada porque outra empresa aprovou o vínculo.");
                });

        return responseMapper.toAdminRequest(request);
    }

    @Transactional
    public AdminDriverLinkRequestResponse rejectRequest(Long requestId, Long companyId, Long adminId, String reason) {
        DriverCompanyLinkRequest request = requireRequest(requestId, companyId);
        if (request.getStatus() != DriverCompanyLinkRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solicitação já foi processada.");
        }
        CompanyAdmin admin = requireAdmin(adminId);
        request.setStatus(DriverCompanyLinkRequestStatus.REJECTED);
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedBy(admin);
        request.setReviewNotes(reason);
        return responseMapper.toAdminRequest(request);
    }

    private DriverCompanyLinkRequest requireRequest(Long requestId, Long companyId) {
        return requestRepository.findByIdAndCompanyId(requestId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação de motorista não encontrada."));
    }

    private CompanyAdmin requireAdmin(Long adminId) {
        return companyAdminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrador não encontrado."));
    }
}
