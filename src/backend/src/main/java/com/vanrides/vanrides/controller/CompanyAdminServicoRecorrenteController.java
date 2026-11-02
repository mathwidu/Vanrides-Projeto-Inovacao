package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminServicoPassageiroResponse;
import com.vanrides.vanrides.dto.AdminServicoPassageirosUpdateRequest;
import com.vanrides.vanrides.dto.AdminServicoRecorrenteRequest;
import com.vanrides.vanrides.dto.AdminServicoRecorrenteResponse;
import com.vanrides.vanrides.service.CompanyAdminServicoRecorrenteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/company-admins/servicos-recorrentes")
public class CompanyAdminServicoRecorrenteController {

    @Autowired
    private CompanyAdminServicoRecorrenteService companyAdminServicoRecorrenteService;

    @GetMapping
    public ResponseEntity<List<AdminServicoRecorrenteResponse>> listarServicos(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminServicoRecorrenteService.listarServicos(getRequiredCompanyId(request)));
    }

    @PostMapping
    public ResponseEntity<AdminServicoRecorrenteResponse> criarServico(@RequestBody AdminServicoRecorrenteRequest request,
                                                                       HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyAdminServicoRecorrenteService.criarServico(getRequiredCompanyId(httpRequest), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminServicoRecorrenteResponse> detalharServico(@PathVariable Long id,
                                                                          HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminServicoRecorrenteService.detalharServico(getRequiredCompanyId(request), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminServicoRecorrenteResponse> editarServico(@PathVariable Long id,
                                                                        @RequestBody AdminServicoRecorrenteRequest request,
                                                                        HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminServicoRecorrenteService.editarServico(getRequiredCompanyId(httpRequest), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativarServico(@PathVariable Long id, HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        companyAdminServicoRecorrenteService.inativarServico(getRequiredCompanyId(request), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/passageiros")
    public ResponseEntity<List<AdminServicoPassageiroResponse>> listarPassageiros(@PathVariable Long id,
                                                                                  HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminServicoRecorrenteService.listarPassageiros(getRequiredCompanyId(request), id));
    }

    @PutMapping("/{id}/passageiros")
    public ResponseEntity<List<AdminServicoPassageiroResponse>> substituirPassageiros(
            @PathVariable Long id,
            @RequestBody AdminServicoPassageirosUpdateRequest request,
            HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminServicoRecorrenteService.substituirPassageiros(
                getRequiredCompanyId(httpRequest),
                id,
                request
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
