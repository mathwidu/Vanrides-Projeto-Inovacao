package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminRotaBaseCreateRequest;
import com.vanrides.vanrides.dto.AdminRotaBaseSummaryResponse;
import com.vanrides.vanrides.service.CompanyAdminRotaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/company-admins/rotas-base")
public class CompanyAdminRotaController {

    @Autowired
    private CompanyAdminRotaService companyAdminRotaService;

    @GetMapping
    public ResponseEntity<List<AdminRotaBaseSummaryResponse>> listarRotasBase(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminRotaService.listarRotasBase(getRequiredCompanyId(request)));
    }

    @PostMapping
    public ResponseEntity<AdminRotaBaseSummaryResponse> criarRotaBase(@RequestBody AdminRotaBaseCreateRequest request,
                                                                      HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        validateRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyAdminRotaService.criarRotaBase(getRequiredCompanyId(httpRequest), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminRotaBaseSummaryResponse> editarRotaBase(@PathVariable Long id,
                                                                       @RequestBody AdminRotaBaseCreateRequest request,
                                                                       HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        validateRequest(request);
        return ResponseEntity.ok(companyAdminRotaService.editarRotaBase(getRequiredCompanyId(httpRequest), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativarRotaBase(@PathVariable Long id, HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        companyAdminRotaService.inativarRotaBase(getRequiredCompanyId(httpRequest), id);
        return ResponseEntity.noContent().build();
    }

    private void validateRequest(AdminRotaBaseCreateRequest request) {
        if (request == null
                || request.getMotoristaId() == null
                || request.getNome() == null
                || request.getNome().isBlank()
                || request.getPassageiroIds() == null
                || request.getPassageiroIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome, motorista e passageiros são obrigatórios.");
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
