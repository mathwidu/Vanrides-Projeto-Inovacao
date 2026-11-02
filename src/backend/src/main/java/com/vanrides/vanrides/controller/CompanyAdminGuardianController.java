package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminResponsavelCreateRequest;
import com.vanrides.vanrides.dto.AdminResponsavelDetailResponse;
import com.vanrides.vanrides.dto.AdminResponsavelSummaryResponse;
import com.vanrides.vanrides.dto.AdminResponsavelUpdateRequest;
import com.vanrides.vanrides.service.CompanyAdminGuardianService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/company-admins/responsaveis")
public class CompanyAdminGuardianController {

    @Autowired
    private CompanyAdminGuardianService companyAdminGuardianService;

    @GetMapping
    public ResponseEntity<List<AdminResponsavelSummaryResponse>> listarResponsaveis(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminGuardianService.listarResponsaveis(getRequiredCompanyId(request)));
    }

    @PostMapping
    public ResponseEntity<AdminResponsavelDetailResponse> criarResponsavel(@RequestBody AdminResponsavelCreateRequest request,
                                                                           HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        validateWriteRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyAdminGuardianService.criarResponsavel(getRequiredCompanyId(httpRequest), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminResponsavelDetailResponse> buscarDetalhe(@PathVariable Long id,
                                                                        HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return companyAdminGuardianService.buscarDetalhe(id, getRequiredCompanyId(request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminResponsavelDetailResponse> atualizarResponsavel(@PathVariable Long id,
                                                                               @RequestBody AdminResponsavelUpdateRequest request,
                                                                               HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        validateWriteRequest(request);
        return ResponseEntity.ok(companyAdminGuardianService.atualizarResponsavel(id, getRequiredCompanyId(httpRequest), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removerResponsavel(@PathVariable Long id, HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        companyAdminGuardianService.removerResponsavel(id, getRequiredCompanyId(request));
        return ResponseEntity.noContent().build();
    }

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de empresa ausente");
    }

    private void validateWriteRequest(AdminResponsavelCreateRequest request) {
        if (request == null
                || isBlank(request.getNome())
                || isBlank(request.getEmail())
                || isBlank(request.getTelefone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome, email e telefone são obrigatórios.");
        }
    }

    private void validateWriteRequest(AdminResponsavelUpdateRequest request) {
        if (request == null
                || isBlank(request.getNome())
                || isBlank(request.getEmail())
                || isBlank(request.getTelefone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome, email e telefone são obrigatórios.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
