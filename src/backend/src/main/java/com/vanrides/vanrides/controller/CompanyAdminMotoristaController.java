package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminMotoristaCreateRequest;
import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminMotoristaUpdateRequest;
import com.vanrides.vanrides.service.CompanyAdminMotoristaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/company-admins/motoristas")
public class CompanyAdminMotoristaController {

    @Autowired
    private CompanyAdminMotoristaService companyAdminMotoristaService;

    @GetMapping
    public ResponseEntity<List<AdminMotoristaSummaryResponse>> listarMotoristas(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminMotoristaService.listarMotoristas(getRequiredCompanyId(request)));
    }

    @PostMapping
    public ResponseEntity<AdminMotoristaSummaryResponse> criarMotorista(@RequestBody AdminMotoristaCreateRequest request,
                                                                        HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        validateRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyAdminMotoristaService.criarMotorista(getRequiredCompanyId(httpRequest), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminMotoristaSummaryResponse> editarMotorista(@PathVariable Long id,
                                                                         @RequestBody AdminMotoristaUpdateRequest request,
                                                                         HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        validateUpdateRequest(request);
        return ResponseEntity.ok(companyAdminMotoristaService.editarMotorista(getRequiredCompanyId(httpRequest), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarMotorista(@PathVariable Long id, HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        companyAdminMotoristaService.deletarMotorista(getRequiredCompanyId(httpRequest), id);
        return ResponseEntity.noContent().build();
    }

    private void validateRequest(AdminMotoristaCreateRequest request) {
        if (request == null
                || isBlank(request.getNome())
                || isBlank(request.getEmail())
                || isBlank(request.getTelefone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome, email e telefone são obrigatórios.");
        }

        if (!isBlank(request.getSenha()) && request.getSenha().trim().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha do motorista deve ter pelo menos 8 caracteres.");
        }
    }

    private void validateUpdateRequest(AdminMotoristaUpdateRequest request) {
        if (request == null
                || isBlank(request.getNome())
                || isBlank(request.getEmail())
                || isBlank(request.getTelefone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome, email e telefone são obrigatórios.");
        }
    }

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de empresa ausente");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
