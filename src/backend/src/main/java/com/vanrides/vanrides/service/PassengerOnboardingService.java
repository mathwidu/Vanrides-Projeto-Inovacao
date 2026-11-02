package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminCompanyLinkRequestDetailResponse;
import com.vanrides.vanrides.dto.AdminCompanyLinkRequestSummaryResponse;
import com.vanrides.vanrides.dto.AdminInviteCodeCreateRequest;
import com.vanrides.vanrides.dto.AdminInviteCodeDetailResponse;
import com.vanrides.vanrides.dto.AdminInviteCodeSummaryResponse;
import com.vanrides.vanrides.dto.PassengerCompanySearchResponse;
import com.vanrides.vanrides.dto.PassengerOnboardingStatusResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyAdmin;
import com.vanrides.vanrides.model.CompanyInviteCode;
import com.vanrides.vanrides.model.CompanyInviteCodeStatus;
import com.vanrides.vanrides.model.CompanyLinkRequest;
import com.vanrides.vanrides.model.CompanyLinkRequestStatus;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.CompanyInviteCodeRepository;
import com.vanrides.vanrides.repository.CompanyLinkRequestRepository;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PassengerOnboardingService {

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyLinkRequestRepository companyLinkRequestRepository;

    @Autowired
    private CompanyInviteCodeRepository companyInviteCodeRepository;

    @Autowired
    private CompanyAdminRepository companyAdminRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private PassengerOnboardingResponseMapper responseMapper;

    @Autowired
    private PassengerOnboardingStatusResolver statusResolver;

    @Autowired
    private PassengerCompanyLinker passengerCompanyLinker;

    @Autowired
    private CompanyInviteCodePolicy inviteCodePolicy;

    @Autowired
    private CompanyInviteCodeGenerator inviteCodeGenerator;

    @Autowired
    private PassengerCompanySearchService companySearchService;

    @Autowired
    private PassengerCompanyRequestService companyRequestService;

    @Autowired
    private PassengerInviteRedemptionService inviteRedemptionService;

    @Autowired
    private PassengerServiceEligibilityAssignmentService serviceEligibilityAssignmentService;

    public PassengerOnboardingStatusResponse resolveStatusForLogin(Passageiro passageiro) {
        return statusResolver.resolve(passageiro);
    }

    public PassengerOnboardingStatusResponse getStatus(Long passageiroId) {
        Passageiro passageiro = requirePassageiro(passageiroId);
        return resolveStatusForLogin(passageiro);
    }

    public List<PassengerCompanySearchResponse> searchCompanies(String query) {
        return companySearchService.searchCompanies(query);
    }

    public PassengerOnboardingStatusResponse createRequest(Long passageiroId, Long companyId) {
        return companyRequestService.createRequest(passageiroId, companyId);
    }

    public PassengerOnboardingStatusResponse redeemInviteCode(Long passageiroId, String rawCode) {
        return inviteRedemptionService.redeemInviteCode(passageiroId, rawCode);
    }

    @Transactional
    public Passageiro requireOperationalPassenger(Long passageiroId) {
        Passageiro passageiro = requirePassageiro(passageiroId);
        if (passageiro.getCompany() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Passageiro ainda não possui empresa vinculada.");
        }
        return passageiro;
    }

    public List<AdminCompanyLinkRequestSummaryResponse> listPendingRequests(Long companyId) {
        return companyLinkRequestRepository.findAllByCompanyIdAndStatusOrderByRequestedAtAsc(companyId, CompanyLinkRequestStatus.PENDING)
                .stream()
                .map(responseMapper::toAdminRequestSummary)
                .toList();
    }

    public Optional<AdminCompanyLinkRequestDetailResponse> getRequestDetail(Long requestId, Long companyId) {
        return companyLinkRequestRepository.findByIdAndCompanyId(requestId, companyId)
                .map(responseMapper::toAdminRequestDetail);
    }

    @Transactional
    public AdminCompanyLinkRequestDetailResponse approveRequest(Long requestId,
                                                                Long companyId,
                                                                Long adminId,
                                                                Long motoristaId,
                                                                String reason,
                                                                List<Long> servicoIds) {
        CompanyLinkRequest request = requireRequest(requestId, companyId);
        if (request.getStatus() != CompanyLinkRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solicitação já foi processada.");
        }

        CompanyAdmin companyAdmin = companyAdminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrador não encontrado."));
        Motorista motorista = motoristaId == null
                ? null
                : motoristaRepository.findByIdAndCompanyId(motoristaId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado na empresa atual."));

        passengerCompanyLinker.link(request.getPassageiro(), request.getCompany(), motorista);
        serviceEligibilityAssignmentService.assignServiceEligibilities(request.getPassageiro(), request.getCompany(), companyId, servicoIds);
        request.setStatus(CompanyLinkRequestStatus.APPROVED);
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedBy(companyAdmin);
        request.setReviewNotes(reason);

        companyLinkRequestRepository.findAllByPassageiroIdAndStatus(request.getPassageiro().getId(), CompanyLinkRequestStatus.PENDING)
                .stream()
                .filter(other -> !other.getId().equals(request.getId()))
                .forEach(other -> {
                    other.setStatus(CompanyLinkRequestStatus.CANCELLED);
                    other.setReviewedAt(OffsetDateTime.now());
                    other.setReviewedBy(companyAdmin);
                    other.setReviewNotes("Cancelada porque outra empresa aprovou o vínculo.");
                });

        return responseMapper.toAdminRequestDetail(request);
    }

    @Transactional
    public AdminCompanyLinkRequestDetailResponse rejectRequest(Long requestId,
                                                               Long companyId,
                                                               Long adminId,
                                                               String reason) {
        CompanyLinkRequest request = requireRequest(requestId, companyId);
        if (request.getStatus() != CompanyLinkRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solicitação já foi processada.");
        }

        CompanyAdmin companyAdmin = companyAdminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrador não encontrado."));
        request.setStatus(CompanyLinkRequestStatus.REJECTED);
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedBy(companyAdmin);
        request.setReviewNotes(reason);
        return responseMapper.toAdminRequestDetail(request);
    }

    public List<AdminInviteCodeSummaryResponse> listInviteCodes(Long companyId) {
        expireStaleInviteCodes();
        return companyInviteCodeRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(responseMapper::toInviteSummary)
                .toList();
    }

    public Optional<AdminInviteCodeDetailResponse> getInviteCodeDetail(Long inviteCodeId, Long companyId) {
        expireStaleInviteCodes();
        return companyInviteCodeRepository.findByIdAndCompanyId(inviteCodeId, companyId)
                .map(responseMapper::toInviteDetail);
    }

    @Transactional
    public AdminInviteCodeDetailResponse createInviteCode(Long companyId,
                                                          Long adminId,
                                                          AdminInviteCodeCreateRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));
        CompanyAdmin admin = companyAdminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrador não encontrado."));

        CompanyInviteCode inviteCode = new CompanyInviteCode();
        inviteCode.setCompany(company);
        inviteCode.setCreatedBy(admin);
        inviteCode.setCode(inviteCodeGenerator.generateUniqueCode());
        inviteCode.setExpiresAt(request != null ? request.expiresAt() : null);
        inviteCode.setMaxUses(inviteCodePolicy.normalizeMaxUses(request != null ? request.maxUses() : null));
        inviteCode.setStatus(CompanyInviteCodeStatus.ACTIVE);
        inviteCode.setUseCount(0);
        return responseMapper.toInviteDetail(companyInviteCodeRepository.save(inviteCode));
    }

    @Transactional
    public AdminInviteCodeDetailResponse cancelInviteCode(Long inviteCodeId, Long companyId) {
        CompanyInviteCode inviteCode = requireInviteCode(inviteCodeId, companyId);
        inviteCode.setStatus(CompanyInviteCodeStatus.CANCELLED);
        inviteCode.setCancelledAt(OffsetDateTime.now());
        return responseMapper.toInviteDetail(inviteCode);
    }

    @Transactional
    public AdminInviteCodeDetailResponse regenerateInviteCode(Long inviteCodeId, Long companyId, Long adminId) {
        CompanyInviteCode current = requireInviteCode(inviteCodeId, companyId);
        current.setStatus(CompanyInviteCodeStatus.CANCELLED);
        current.setCancelledAt(OffsetDateTime.now());

        AdminInviteCodeCreateRequest request = new AdminInviteCodeCreateRequest(current.getExpiresAt(), current.getMaxUses());
        return createInviteCode(companyId, adminId, request);
    }

    private CompanyLinkRequest requireRequest(Long requestId, Long companyId) {
        return companyLinkRequestRepository.findByIdAndCompanyId(requestId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada na empresa atual."));
    }

    private CompanyInviteCode requireInviteCode(Long inviteCodeId, Long companyId) {
        return companyInviteCodeRepository.findByIdAndCompanyId(inviteCodeId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite não encontrado na empresa atual."));
    }

    private Passageiro requirePassageiro(Long passageiroId) {
        return passageiroRepository.findById(passageiroId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado."));
    }

    @Transactional
    public void expireStaleInviteCodes() {
        companyInviteCodeRepository.findAllByStatusAndExpiresAtBefore(CompanyInviteCodeStatus.ACTIVE, OffsetDateTime.now())
                .forEach(inviteCode -> inviteCode.setStatus(CompanyInviteCodeStatus.EXPIRED));
    }

    @Transactional
    public void resendVerificationForLinkedPassenger(Passageiro passageiro) {
        emailVerificationService.markPendingAndRevokeSessions(UserRole.PASSENGER, passageiro.getId());
        emailVerificationService.sendVerificationIfAllowed(UserRole.PASSENGER, passageiro.getId(), passageiro.getEmail());
    }

    @Autowired
    private EmailVerificationService emailVerificationService;
}
