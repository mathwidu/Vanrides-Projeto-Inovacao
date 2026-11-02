package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.CompanyAdminBootstrapRequest;
import com.vanrides.vanrides.dto.CompanyAdminResponse;
import com.vanrides.vanrides.exception.ApiStatusException;
import com.vanrides.vanrides.service.CompanyAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/company-admins")
public class CompanyAdminController {

    @Autowired
    private CompanyAdminService companyAdminService;

    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@RequestBody CompanyAdminBootstrapRequest request) {
        if (request == null
                || isBlank(request.getNome())
                || isBlank(request.getEmail())
                || isBlank(request.getTelefone())
                || isBlank(request.getSenha())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nome, email, telefone e senha são obrigatórios."));
        }

        if (request.getSenha().trim().length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "A senha do admin deve ter pelo menos 8 caracteres."));
        }

        if (isBlank(request.getCompanyName())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Nome da empresa é obrigatório para o primeiro cadastro."));
        }

        try {
            CompanyAdminResponse response = companyAdminService.bootstrapFirstAdmin(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ApiStatusException exception) {
            throw exception;
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode())
                    .body(Map.of("error", exception.getReason() != null ? exception.getReason() : exception.getMessage()));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
