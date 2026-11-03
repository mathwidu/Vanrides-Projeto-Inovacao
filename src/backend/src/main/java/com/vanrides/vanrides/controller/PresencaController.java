package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.model.Presenca;
import jakarta.servlet.http.HttpServletRequest;
import com.vanrides.vanrides.service.PresencaService;
import com.vanrides.vanrides.service.GuardianAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/presencas")
public class PresencaController {

    @Autowired
    private PresencaService presencaService;

    @Autowired
    private GuardianAccessService guardianAccessService;

    @PostMapping
    public ResponseEntity<?> criarPresenca(@RequestBody Presenca presenca, HttpServletRequest request) {
        Long companyId = getRequiredCompanyId(request);
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);
        Long targetPassageiroId = getRequiredPassageiroId(presenca);

        if (role.isPassenger()) {
            ensurePassengerOwnsTarget(userId, targetPassageiroId);
        } else if (role.isGuardian()) {
            ensureGuardianOwnsPassageiro(userId, targetPassageiroId, companyId);
        } else if (!role.isCompanyAdmin()) {
            throw forbiddenForRole();
        }

        boolean existe = presencaService.jaExistePresenca(
                targetPassageiroId,
                presenca.getDataPresenca(),
                companyId
        );

        if (existe) {
            return ResponseEntity.status(409).body("Já existe presença registrada para este dia.");
        }

        try {
            Presenca salva = presencaService.salvarPresenca(presenca, companyId);
            return ResponseEntity.ok(salva);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Presenca>> listarPresencas(HttpServletRequest request) {
        Long companyId = getRequiredCompanyId(request);
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);

        if (role.isPassenger()) {
            return ResponseEntity.ok(presencaService.listarPresencasPorPassageiro(userId, companyId));
        }
        if (role.isGuardian()) {
            return ResponseEntity.ok(
                    presencaService.listarPresencasPorPassageiros(
                            guardianAccessService.listLinkedPassageiroIds(userId, companyId),
                            companyId
                    )
            );
        }
        if (role.isCompanyAdmin()) {
            return ResponseEntity.ok(presencaService.listarTodasPresencas(companyId));
        }

        throw forbiddenForRole();
    }

    @GetMapping("/passageiro/{passageiroId}")
    public ResponseEntity<List<Presenca>> listarPorPassageiro(@PathVariable Long passageiroId,
                                                              HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);
        Long companyId = getRequiredCompanyId(request);

        if (role.isPassenger()) {
            ensurePassengerOwnsTarget(userId, passageiroId);
        } else if (role.isGuardian()) {
            ensureGuardianOwnsPassageiro(userId, passageiroId, companyId);
        } else if (!role.isCompanyAdmin()) {
            throw forbiddenForRole();
        }

        return ResponseEntity.ok(presencaService.listarPresencasPorPassageiro(passageiroId, companyId));
    }

    @GetMapping("/data")
    public ResponseEntity<List<Presenca>> listarPorData(@RequestParam("data")
                                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataPresenca,
                                                         HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(presencaService.listarPresencasPorData(dataPresenca, getRequiredCompanyId(request)));
    }

    @GetMapping("/motorista/{motoristaId}")
    public ResponseEntity<List<Presenca>> listarPorMotoristaEData(
            @PathVariable Long motoristaId,
            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);

        if (role.isDriver()) {
            ensureDriverOwnsTarget(userId, motoristaId);
        } else if (!role.isCompanyAdmin()) {
            throw forbiddenForRole();
        }

        return ResponseEntity.ok(presencaService.listarPresencasPorMotoristaEData(motoristaId, data, getRequiredCompanyId(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPresenca(@PathVariable Long id, HttpServletRequest request) {
        Long companyId = getRequiredCompanyId(request);
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);
        Optional<Presenca> presenca = presencaService.buscarPresencaPorId(id, companyId);

        if (role.isPassenger()) {
            if (presenca.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            ensurePassengerOwnsTarget(userId, presenca.get().getPassageiro().getId());
        } else if (role.isGuardian()) {
            if (presenca.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            ensureGuardianOwnsPassageiro(userId, presenca.get().getPassageiro().getId(), companyId);
        } else if (!role.isCompanyAdmin()) {
            throw forbiddenForRole();
        }

        if (presenca.isPresent()) {
            presencaService.deletarPresenca(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de empresa ausente");
    }

    private Long getRequiredPassageiroId(Presenca presenca) {
        if (presenca.getPassageiro() == null || presenca.getPassageiro().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passageiro é obrigatório.");
        }
        return presenca.getPassageiro().getId();
    }

    private void ensurePassengerOwnsTarget(Long userId, Long targetPassageiroId) {
        if (!userId.equals(targetPassageiroId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado na empresa atual.");
        }
    }

    private void ensureDriverOwnsTarget(Long userId, Long targetMotoristaId) {
        if (!userId.equals(targetMotoristaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado na empresa atual.");
        }
    }

    private void ensureGuardianOwnsPassageiro(Long guardianId, Long passageiroId, Long companyId) {
        if (!guardianAccessService.hasLinkedPassageiro(guardianId, passageiroId, companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado na empresa atual.");
        }
    }

    private ResponseStatusException forbiddenForRole() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Papel sem permissão para esta operação");
    }
}
