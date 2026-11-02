package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.PublicAccountRegistrationResponse;
import com.vanrides.vanrides.dto.PublicPassengerRegistrationRequest;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.GuardianPassengerMutationPolicy;
import com.vanrides.vanrides.service.PassageiroService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/passageiros")
public class PassageiroController {

    @Autowired
    private PassageiroService passageiroService;

    @Autowired
    private GuardianAccessService guardianAccessService;

    // ✅ Cadastro de passageiro
    @PostMapping
    public ResponseEntity<?> criarPassageiro(@RequestBody PublicPassengerRegistrationRequest passageiro) {
        // Verifica campos obrigatórios
        if (passageiro.senha() == null || passageiro.senha().isBlank()) {
            return ResponseEntity.badRequest().body("Senha é obrigatória.");
        }

        if (passageiro.endereco() == null || passageiro.endereco().isBlank()
            || passageiro.bairro() == null || passageiro.bairro().isBlank()
            || passageiro.cidade() == null || passageiro.cidade().isBlank()
            || passageiro.estado() == null || passageiro.estado().isBlank()
            || passageiro.cep() == null || passageiro.cep().isBlank()) {
            return ResponseEntity.badRequest().body("Endereço completo com bairro é obrigatório.");
        }

        // Salva e retorna
        Passageiro salvo = passageiroService.registrarPassageiroPublico(passageiro);
        return ResponseEntity.ok(toRegistrationResponse(salvo));
    }

    // ✅ Listagem de passageiros
    @GetMapping
    public ResponseEntity<List<Passageiro>> listarPassageiros(HttpServletRequest request) {
        Long companyId = getRequiredCompanyId(request);
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);

        if (role.isCompanyAdmin()) {
            return ResponseEntity.ok(passageiroService.listarTodosPassageiros(companyId));
        }
        if (role.isGuardian()) {
            return ResponseEntity.ok(passageiroService.listarPassageirosPorIds(
                    guardianAccessService.listLinkedPassageiroIds(userId, companyId),
                    companyId
            ));
        }
        if (role.isPassenger()) {
            return ResponseEntity.ok(
                    passageiroService.buscarPassageiroPorId(userId, companyId)
                            .map(List::of)
                            .orElse(List.of())
            );
        }

        throw forbiddenForRole();
    }

    // ✅ Buscar passageiro por ID
    @GetMapping("/{id}")
    public ResponseEntity<Passageiro> buscarPorId(@PathVariable Long id, HttpServletRequest request) {
        Long companyId = getRequiredCompanyId(request);
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);

        if (role.isGuardian()) {
            ensureGuardianOwnsTarget(userId, id, companyId);
        } else if (role.isPassenger()) {
            ensurePassengerOwnsTarget(userId, id);
        } else if (!role.isCompanyAdmin()) {
            throw forbiddenForRole();
        }

        Optional<Passageiro> passageiro = passageiroService.buscarPassageiroPorId(id, companyId);
        return passageiro.map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
    }

    // ✅ Atualizar passageiro
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizarPassageiro(@PathVariable Long id,
                                                 @RequestBody Passageiro passageiroAtualizado,
                                                 HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN, UserRole.GUARDIAN);
        Long companyId = getRequiredCompanyId(request);
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        GuardianPassengerMutationPolicy.requireCanMutatePassengerProfile(role);

        return passageiroService.atualizarPassageiroProtegido(id, companyId, passageiroAtualizado)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ✅ Deletar passageiro
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPorId(@PathVariable Long id, HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN, UserRole.GUARDIAN);
        Long companyId = getRequiredCompanyId(request);
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        GuardianPassengerMutationPolicy.requireCanMutatePassengerProfile(role);

        passageiroService.deletarPassageiro(id, companyId);
        return ResponseEntity.noContent().build();
    }

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de empresa ausente");
    }

    private void ensurePassengerOwnsTarget(Long userId, Long targetPassageiroId) {
        if (!userId.equals(targetPassageiroId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado na empresa atual.");
        }
    }

    private void ensureGuardianOwnsTarget(Long guardianId, Long targetPassageiroId, Long companyId) {
        if (!guardianAccessService.hasLinkedPassageiro(guardianId, targetPassageiroId, companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado na empresa atual.");
        }
    }

    private ResponseStatusException forbiddenForRole() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Papel sem permissão para esta operação");
    }

    private PublicAccountRegistrationResponse toRegistrationResponse(Passageiro passageiro) {
        return new PublicAccountRegistrationResponse(
                passageiro.getId(),
                passageiro.getNome(),
                passageiro.getEmail(),
                passageiro.getTelefone(),
                passageiro.isEmailVerified(),
                passageiro.getEmailVerificationLastSentAt()
        );
    }
}
