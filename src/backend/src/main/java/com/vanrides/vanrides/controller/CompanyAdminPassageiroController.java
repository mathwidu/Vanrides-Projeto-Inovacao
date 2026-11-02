package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminPassageiroCreateRequest;
import com.vanrides.vanrides.dto.AdminPassageiroDetailResponse;
import com.vanrides.vanrides.dto.AdminPassageiroMotoristaUpdateRequest;
import com.vanrides.vanrides.dto.AdminPassageiroServicoResponse;
import com.vanrides.vanrides.dto.AdminPassageiroServicosUpdateRequest;
import com.vanrides.vanrides.dto.AdminPassageiroSummaryResponse;
import com.vanrides.vanrides.dto.AdminPassageiroUpdateRequest;
import com.vanrides.vanrides.service.CompanyAdminPassageiroService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/company-admins/passageiros")
public class CompanyAdminPassageiroController {

    @Autowired
    private CompanyAdminPassageiroService companyAdminPassageiroService;

    @GetMapping
    public ResponseEntity<List<AdminPassageiroSummaryResponse>> listarPassageiros(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminPassageiroService.listarPassageiros(getRequiredCompanyId(request)));
    }

    @PostMapping
    public ResponseEntity<AdminPassageiroDetailResponse> criarPassageiro(@RequestBody AdminPassageiroCreateRequest request,
                                                                         HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        validateCreateRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyAdminPassageiroService.criarPassageiro(getRequiredCompanyId(httpRequest), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminPassageiroDetailResponse> buscarDetalhe(@PathVariable Long id,
                                                                       HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return companyAdminPassageiroService.buscarDetalhe(id, getRequiredCompanyId(request))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminPassageiroDetailResponse> atualizarPassageiro(@PathVariable Long id,
                                                                             @RequestBody AdminPassageiroUpdateRequest request,
                                                                             HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        validateUpdateRequest(request);
        return ResponseEntity.ok(companyAdminPassageiroService.atualizarPassageiro(id, getRequiredCompanyId(httpRequest), request));
    }

    @PutMapping("/{id}/motorista")
    public ResponseEntity<AdminPassageiroDetailResponse> reatribuirMotorista(@PathVariable Long id,
                                                                             @RequestBody AdminPassageiroMotoristaUpdateRequest request,
                                                                             HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        if (request == null || request.getMotoristaId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(companyAdminPassageiroService.reatribuirMotorista(id, getRequiredCompanyId(httpRequest), request.getMotoristaId()));
    }

    @GetMapping("/{id}/servicos")
    public ResponseEntity<List<AdminPassageiroServicoResponse>> listarServicosDoPassageiro(@PathVariable Long id,
                                                                                           HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminPassageiroService.listarServicosDoPassageiro(id, getRequiredCompanyId(request)));
    }

    @PutMapping("/{id}/servicos")
    public ResponseEntity<List<AdminPassageiroServicoResponse>> substituirServicosDoPassageiro(
            @PathVariable Long id,
            @RequestBody AdminPassageiroServicosUpdateRequest request,
            HttpServletRequest httpRequest) {
        AuthorizationGuard.requireAnyRole(httpRequest, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(companyAdminPassageiroService.substituirServicosDoPassageiro(id, getRequiredCompanyId(httpRequest), request));
    }

    private void validateUpdateRequest(AdminPassageiroUpdateRequest request) {
        if (request == null
                || isBlank(request.getNome())
                || isBlank(request.getEmail())
                || isBlank(request.getTelefone())
                || isBlank(request.getEndereco())
                || isBlank(request.getBairro())
                || isBlank(request.getCidade())
                || isBlank(request.getEstado())
                || isBlank(request.getCep())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome, email, telefone e endereço completo são obrigatórios.");
        }

        validateCoordinates(request.getLatitude(), request.getLongitude());
    }

    private void validateCreateRequest(AdminPassageiroCreateRequest request) {
        if (request == null
                || isBlank(request.getNome())
                || isBlank(request.getEmail())
                || isBlank(request.getTelefone())
                || isBlank(request.getEndereco())
                || isBlank(request.getBairro())
                || isBlank(request.getCidade())
                || isBlank(request.getEstado())
                || isBlank(request.getCep())
                || isBlank(request.getSenha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome, email, telefone, endereço completo e senha são obrigatórios.");
        }

        if (request.getSenha().trim().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha do passageiro deve ter pelo menos 8 caracteres.");
        }

        validateCoordinates(request.getLatitude(), request.getLongitude());
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        boolean hasLatitude = latitude != null;
        boolean hasLongitude = longitude != null;
        if (hasLatitude != hasLongitude) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude e longitude devem ser informadas juntas.");
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
