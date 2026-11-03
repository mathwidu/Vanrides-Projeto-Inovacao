package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.GuardianPasswordChangeRequest;
import com.vanrides.vanrides.service.CompanyAdminGuardianService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/responsaveis")
public class GuardianAccountController {

    @Autowired
    private CompanyAdminGuardianService companyAdminGuardianService;

    @PutMapping("/minha-senha")
    public ResponseEntity<Void> atualizarMinhaSenha(@RequestBody GuardianPasswordChangeRequest request,
                                                    HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.GUARDIAN);
        validatePasswordChangeRequest(request);
        companyAdminGuardianService.atualizarMinhaSenha(
                getRequiredUserId(httpRequest),
                getRequiredCompanyId(httpRequest),
                request
        );
        return ResponseEntity.noContent().build();
    }

    private void validatePasswordChangeRequest(GuardianPasswordChangeRequest request) {
        if (request == null || isBlank(request.getSenhaAtual()) || isBlank(request.getNovaSenha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha atual e nova senha são obrigatórias.");
        }
    }

    private Long getRequiredUserId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário autenticado ausente");
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
