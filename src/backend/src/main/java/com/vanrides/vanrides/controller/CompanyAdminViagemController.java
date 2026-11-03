package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminViagemBulkCreateRequest;
import com.vanrides.vanrides.dto.AdminViagemBulkCreateResponse;
import com.vanrides.vanrides.dto.AdminViagemCreateRequest;
import com.vanrides.vanrides.dto.AdminViagemDetailResponse;
import com.vanrides.vanrides.dto.AdminViagemManifestPreviewResponse;
import com.vanrides.vanrides.dto.AdminViagemMotoristaUpdateRequest;
import com.vanrides.vanrides.dto.AdminViagemSummaryResponse;
import com.vanrides.vanrides.service.CompanyAdminViagemService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/company-admins/viagens")
public class CompanyAdminViagemController {

    @Autowired
    private CompanyAdminViagemService companyAdminViagemService;

    @GetMapping
    public ResponseEntity<List<AdminViagemSummaryResponse>> listarViagens(
            @RequestParam(value = "data", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminViagemService.listarViagens(getRequiredCompanyId(request), data));
    }

    @PostMapping("/gerar-automaticas")
    public ResponseEntity<AdminViagemBulkCreateResponse> gerarViagensAutomaticas(
            @RequestBody AdminViagemBulkCreateRequest request,
            HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        if (request == null || request.getDataOperacao() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data da operação é obrigatória.");
        }
        return ResponseEntity.ok(companyAdminViagemService.gerarViagensAutomaticas(
                getRequiredCompanyId(httpRequest),
                request.getDataOperacao()
        ));
    }

    @GetMapping("/{viagemId}")
    public ResponseEntity<AdminViagemDetailResponse> buscarDetalhe(@PathVariable Long viagemId,
                                                                   HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminViagemService.buscarDetalhe(getRequiredCompanyId(request), viagemId));
    }

    @GetMapping("/{viagemId}/manifesto-preview")
    public ResponseEntity<AdminViagemManifestPreviewResponse> previewSincronizacaoManifesto(
            @PathVariable Long viagemId,
            HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminViagemService.previewSincronizacaoManifesto(
                getRequiredCompanyId(request),
                viagemId
        ));
    }

    @PostMapping("/{viagemId}/sincronizar-manifesto")
    public ResponseEntity<AdminViagemDetailResponse> sincronizarManifesto(@PathVariable Long viagemId,
                                                                          HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminViagemService.sincronizarManifesto(
                getRequiredCompanyId(request),
                viagemId
        ));
    }

    @PostMapping
    public ResponseEntity<AdminViagemSummaryResponse> criarViagem(@RequestBody AdminViagemCreateRequest request,
                                                                  HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        validateRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyAdminViagemService.criarViagem(getRequiredCompanyId(httpRequest), request));
    }

    @PutMapping("/{viagemId}/motorista")
    public ResponseEntity<AdminViagemDetailResponse> trocarMotorista(@PathVariable Long viagemId,
                                                                     @RequestBody AdminViagemMotoristaUpdateRequest request,
                                                                     HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        if (request == null || request.getMotoristaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "motoristaId é obrigatório.");
        }
        return ResponseEntity.ok(companyAdminViagemService.trocarMotorista(
                getRequiredCompanyId(httpRequest),
                viagemId,
                request.getMotoristaId()
        ));
    }

    @PostMapping("/{viagemId}/cancelar")
    public ResponseEntity<AdminViagemDetailResponse> cancelarViagem(@PathVariable Long viagemId,
                                                                    HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminViagemService.cancelarViagem(
                getRequiredCompanyId(request),
                viagemId
        ));
    }

    private void validateRequest(AdminViagemCreateRequest request) {
        if (request == null || request.getDataOperacao() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data da operação é obrigatória.");
        }
        boolean hasRotaBase = request.getRotaBaseId() != null;
        boolean hasServico = request.getServicoId() != null;
        if (hasRotaBase == hasServico) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe rotaBaseId ou servicoId, mas não ambos.");
        }
        if (request.getMotoristaId() != null && !hasServico) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "motoristaId só pode ser usado na criação por serviço.");
        }
    }

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de empresa ausente");
    }
}
