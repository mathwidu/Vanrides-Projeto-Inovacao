package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminPassageiroMotoristaLinkResponse;
import com.vanrides.vanrides.dto.AdminCompanyLinkRequestDecisionRequest;
import com.vanrides.vanrides.dto.AdminCompanyLinkRequestDetailResponse;
import com.vanrides.vanrides.dto.AdminCompanyLinkRequestSummaryResponse;
import com.vanrides.vanrides.dto.AdminDriverInviteCreateRequest;
import com.vanrides.vanrides.dto.AdminDriverInviteResponse;
import com.vanrides.vanrides.dto.AdminDriverLinkRequestDecisionRequest;
import com.vanrides.vanrides.dto.AdminDriverLinkRequestResponse;
import com.vanrides.vanrides.dto.AdminInviteCodeCreateRequest;
import com.vanrides.vanrides.dto.AdminInviteCodeDetailResponse;
import com.vanrides.vanrides.dto.AdminInviteCodeSummaryResponse;
import com.vanrides.vanrides.dto.AdminResponsavelPassageiroLinkCreateRequest;
import com.vanrides.vanrides.dto.AdminResponsavelPassageiroLinkResponse;
import com.vanrides.vanrides.service.CompanyAdminDriverInviteService;
import com.vanrides.vanrides.service.CompanyAdminDriverLinkRequestService;
import com.vanrides.vanrides.service.CompanyAdminVinculoService;
import com.vanrides.vanrides.service.PassengerOnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/company-admins")
public class CompanyAdminVinculoController {

    @Autowired
    private CompanyAdminVinculoService companyAdminVinculoService;

    @Autowired
    private PassengerOnboardingService passengerOnboardingService;

    @Autowired
    private CompanyAdminDriverInviteService companyAdminDriverInviteService;

    @Autowired
    private CompanyAdminDriverLinkRequestService companyAdminDriverLinkRequestService;

    @GetMapping("/vinculos/passageiros-motoristas")
    public ResponseEntity<List<AdminPassageiroMotoristaLinkResponse>> listarVinculosPassageiroMotorista(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminVinculoService.listarVinculosPassageiroMotorista(getRequiredCompanyId(request)));
    }

    @DeleteMapping("/passageiros/{id}/motorista")
    public ResponseEntity<Void> removerVinculoPassageiroMotorista(@PathVariable Long id, HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        companyAdminVinculoService.removerVinculoPassageiroMotorista(id, getRequiredCompanyId(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/vinculos/responsaveis-passageiros")
    public ResponseEntity<List<AdminResponsavelPassageiroLinkResponse>> listarVinculosResponsavelPassageiro(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminVinculoService.listarVinculosResponsavelPassageiro(getRequiredCompanyId(request)));
    }

    @PostMapping("/responsaveis/{responsavelId}/passageiros")
    public ResponseEntity<AdminResponsavelPassageiroLinkResponse> criarVinculoResponsavelPassageiro(@PathVariable Long responsavelId,
                                                                                                     @RequestBody AdminResponsavelPassageiroLinkCreateRequest request,
                                                                                                     HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        if (request == null || request.getPassageiroId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "passageiroId é obrigatório.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyAdminVinculoService.criarVinculoResponsavelPassageiro(
                        responsavelId,
                        request.getPassageiroId(),
                        getRequiredCompanyId(httpRequest)
                ));
    }

    @DeleteMapping("/responsaveis/{responsavelId}/passageiros/{passageiroId}")
    public ResponseEntity<Void> removerVinculoResponsavelPassageiro(@PathVariable Long responsavelId,
                                                                    @PathVariable Long passageiroId,
                                                                    HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        companyAdminVinculoService.removerVinculoResponsavelPassageiro(responsavelId, passageiroId, getRequiredCompanyId(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/access-requests")
    public ResponseEntity<List<AdminCompanyLinkRequestSummaryResponse>> listAccessRequests(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(passengerOnboardingService.listPendingRequests(getRequiredCompanyId(request)));
    }

    @GetMapping("/access-requests/{id}")
    public ResponseEntity<AdminCompanyLinkRequestDetailResponse> getAccessRequest(@PathVariable Long id,
                                                                                  HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return passengerOnboardingService.getRequestDetail(id, getRequiredCompanyId(request))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada na empresa atual."));
    }

    @PostMapping("/access-requests/{id}/approve")
    public ResponseEntity<AdminCompanyLinkRequestDetailResponse> approveAccessRequest(@PathVariable Long id,
                                                                                      @RequestBody(required = false) AdminCompanyLinkRequestDecisionRequest body,
                                                                                      HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(
                passengerOnboardingService.approveRequest(
                        id,
                        getRequiredCompanyId(request),
                        AuthorizationGuard.getRequiredUserId(request),
                        body != null ? body.motoristaId() : null,
                        body != null ? body.reason() : null,
                        body != null ? body.servicoIds() : null
                )
        );
    }

    @PostMapping("/access-requests/{id}/reject")
    public ResponseEntity<AdminCompanyLinkRequestDetailResponse> rejectAccessRequest(@PathVariable Long id,
                                                                                     @RequestBody(required = false) AdminCompanyLinkRequestDecisionRequest body,
                                                                                     HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(
                passengerOnboardingService.rejectRequest(
                        id,
                        getRequiredCompanyId(request),
                        AuthorizationGuard.getRequiredUserId(request),
                        body != null ? body.reason() : null
                )
        );
    }

    @GetMapping("/invite-codes")
    public ResponseEntity<List<AdminInviteCodeSummaryResponse>> listInviteCodes(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(passengerOnboardingService.listInviteCodes(getRequiredCompanyId(request)));
    }

    @GetMapping("/invite-codes/{id}")
    public ResponseEntity<AdminInviteCodeDetailResponse> getInviteCode(@PathVariable Long id, HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return passengerOnboardingService.getInviteCodeDetail(id, getRequiredCompanyId(request))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite não encontrado na empresa atual."));
    }

    @PostMapping("/invite-codes")
    public ResponseEntity<AdminInviteCodeDetailResponse> createInviteCode(@RequestBody(required = false) AdminInviteCodeCreateRequest body,
                                                                          HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(passengerOnboardingService.createInviteCode(
                        getRequiredCompanyId(request),
                        AuthorizationGuard.getRequiredUserId(request),
                        body
                ));
    }

    @PostMapping("/invite-codes/{id}/cancel")
    public ResponseEntity<AdminInviteCodeDetailResponse> cancelInviteCode(@PathVariable Long id, HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(passengerOnboardingService.cancelInviteCode(id, getRequiredCompanyId(request)));
    }

    @PostMapping("/invite-codes/{id}/regenerate")
    public ResponseEntity<AdminInviteCodeDetailResponse> regenerateInviteCode(@PathVariable Long id, HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(passengerOnboardingService.regenerateInviteCode(
                id,
                getRequiredCompanyId(request),
                AuthorizationGuard.getRequiredUserId(request)
        ));
    }

    @GetMapping("/driver-invites")
    public ResponseEntity<List<AdminDriverInviteResponse>> listDriverInvites(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminDriverInviteService.listInvites(getRequiredCompanyId(request)));
    }

    @PostMapping("/driver-invites")
    public ResponseEntity<AdminDriverInviteResponse> createDriverInvite(@RequestBody(required = false) AdminDriverInviteCreateRequest body,
                                                                        HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyAdminDriverInviteService.createInvite(
                        getRequiredCompanyId(request),
                        AuthorizationGuard.getRequiredUserId(request),
                        body
                ));
    }

    @PostMapping("/driver-invites/{id}/cancel")
    public ResponseEntity<AdminDriverInviteResponse> cancelDriverInvite(@PathVariable Long id, HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminDriverInviteService.cancelInvite(id, getRequiredCompanyId(request)));
    }

    @GetMapping("/driver-link-requests")
    public ResponseEntity<List<AdminDriverLinkRequestResponse>> listDriverLinkRequests(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminDriverLinkRequestService.listPendingRequests(getRequiredCompanyId(request)));
    }

    @PostMapping("/driver-link-requests/{id}/approve")
    public ResponseEntity<AdminDriverLinkRequestResponse> approveDriverLinkRequest(@PathVariable Long id,
                                                                                  @RequestBody(required = false) AdminDriverLinkRequestDecisionRequest body,
                                                                                  HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminDriverLinkRequestService.approveRequest(
                id,
                getRequiredCompanyId(request),
                AuthorizationGuard.getRequiredUserId(request),
                body != null ? body.reason() : null
        ));
    }

    @PostMapping("/driver-link-requests/{id}/reject")
    public ResponseEntity<AdminDriverLinkRequestResponse> rejectDriverLinkRequest(@PathVariable Long id,
                                                                                 @RequestBody(required = false) AdminDriverLinkRequestDecisionRequest body,
                                                                                 HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminDriverLinkRequestService.rejectRequest(
                id,
                getRequiredCompanyId(request),
                AuthorizationGuard.getRequiredUserId(request),
                body != null ? body.reason() : null
        ));
    }

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de empresa ausente");
    }
}
