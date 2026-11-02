package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.ViagemMotoristaResponse;
import com.vanrides.vanrides.dto.ViagemParadaStatusUpdateRequest;
import com.vanrides.vanrides.dto.ViagemPassageiroAtualResponse;
import com.vanrides.vanrides.dto.ViagemRotaUpdateRequest;
import com.vanrides.vanrides.dto.ViagemStatusUpdateRequest;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.OperationalDateProvider;
import com.vanrides.vanrides.service.ViagemOperacionalService;
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
@RequestMapping("/viagens")
public class ViagemController {

    @Autowired
    private ViagemOperacionalService viagemOperacionalService;

    @Autowired
    private GuardianAccessService guardianAccessService;

    @Autowired
    private OperationalDateProvider operationalDateProvider;

    @GetMapping("/motorista/{motoristaId}/hoje")
    public ResponseEntity<ViagemMotoristaResponse> buscarViagemDoMotorista(
            @PathVariable Long motoristaId,
            @RequestParam(value = "data", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);
        Long companyId = getRequiredCompanyId(request);

        if (role.isDriver() && !userId.equals(motoristaId)) {
            throw notFoundInTenant("Motorista");
        }
        if (!role.isDriver() && !role.isCompanyAdmin()) {
            throw forbiddenForRole();
        }

        return viagemOperacionalService.buscarViagemDoMotorista(motoristaId, defaultDate(data), companyId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/motorista/{motoristaId}/dia")
    public ResponseEntity<List<ViagemMotoristaResponse>> buscarAgendaDoMotorista(
            @PathVariable Long motoristaId,
            @RequestParam(value = "data", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);
        Long companyId = getRequiredCompanyId(request);

        if (role.isDriver() && !userId.equals(motoristaId)) {
            throw notFoundInTenant("Motorista");
        }
        if (!role.isDriver() && !role.isCompanyAdmin()) {
            throw forbiddenForRole();
        }

        return ResponseEntity.ok(
                viagemOperacionalService.buscarAgendaDoMotorista(motoristaId, defaultDate(data), companyId)
        );
    }

    @GetMapping("/passageiro/{passageiroId}/atual")
    public ResponseEntity<ViagemPassageiroAtualResponse> buscarViagemAtualDoPassageiro(
            @PathVariable Long passageiroId,
            @RequestParam(value = "data", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);
        Long companyId = getRequiredCompanyId(request);

        if (role.isPassenger() && !userId.equals(passageiroId)) {
            throw notFoundInTenant("Passageiro");
        }
        if (role.isGuardian() && !guardianAccessService.hasLinkedPassageiro(userId, passageiroId, companyId)) {
            throw notFoundInTenant("Passageiro");
        }
        if (!role.isPassenger() && !role.isGuardian() && !role.isCompanyAdmin()) {
            throw forbiddenForRole();
        }

        return viagemOperacionalService.buscarViagemAtualDoPassageiro(passageiroId, defaultDate(data), companyId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/passageiro/{passageiroId}/dia")
    public ResponseEntity<List<ViagemPassageiroAtualResponse>> buscarAgendaDoPassageiro(
            @PathVariable Long passageiroId,
            @RequestParam(value = "data", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);
        Long companyId = getRequiredCompanyId(request);

        if (role.isPassenger() && !userId.equals(passageiroId)) {
            throw notFoundInTenant("Passageiro");
        }
        if (role.isGuardian() && !guardianAccessService.hasLinkedPassageiro(userId, passageiroId, companyId)) {
            throw notFoundInTenant("Passageiro");
        }
        if (!role.isPassenger() && !role.isGuardian() && !role.isCompanyAdmin()) {
            throw forbiddenForRole();
        }

        return ResponseEntity.ok(
                viagemOperacionalService.buscarAgendaDoPassageiro(passageiroId, defaultDate(data), companyId)
        );
    }

    @PutMapping("/{viagemId}/status")
    public ResponseEntity<ViagemMotoristaResponse> atualizarStatusDaViagem(@PathVariable Long viagemId,
                                                                           @RequestBody ViagemStatusUpdateRequest request,
                                                                           HttpServletRequest httpRequest) {
        UserRole role = AuthorizationGuard.getRequiredRole(httpRequest);
        Long companyId = getRequiredCompanyId(httpRequest);
        Long userId = AuthorizationGuard.getRequiredUserId(httpRequest);

        if (request == null || request.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status da viagem é obrigatório.");
        }

        ensureTripCanBeOperatedByRole(viagemId, role, userId, companyId);
        return ResponseEntity.ok(viagemOperacionalService.atualizarStatusDaViagem(viagemId, companyId, request.getStatus()));
    }

    @PostMapping("/{viagemId}/iniciar")
    public ResponseEntity<ViagemMotoristaResponse> iniciarCorrida(@PathVariable Long viagemId,
                                                                  HttpServletRequest httpRequest) {
        UserRole role = AuthorizationGuard.getRequiredRole(httpRequest);
        Long companyId = getRequiredCompanyId(httpRequest);
        Long userId = AuthorizationGuard.getRequiredUserId(httpRequest);

        ensureTripCanBeOperatedByRole(viagemId, role, userId, companyId);
        return ResponseEntity.ok(viagemOperacionalService.iniciarCorrida(viagemId, companyId));
    }

    @PostMapping("/{viagemId}/encerrar")
    public ResponseEntity<ViagemMotoristaResponse> encerrarCorrida(@PathVariable Long viagemId,
                                                                   HttpServletRequest httpRequest) {
        UserRole role = AuthorizationGuard.getRequiredRole(httpRequest);
        Long companyId = getRequiredCompanyId(httpRequest);
        Long userId = AuthorizationGuard.getRequiredUserId(httpRequest);

        ensureTripCanBeOperatedByRole(viagemId, role, userId, companyId);
        return ResponseEntity.ok(viagemOperacionalService.encerrarCorrida(viagemId, companyId));
    }

    @PutMapping("/{viagemId}/paradas/{paradaId}/status")
    public ResponseEntity<ViagemMotoristaResponse> atualizarStatusDaParada(@PathVariable Long viagemId,
                                                                           @PathVariable Long paradaId,
                                                                           @RequestBody ViagemParadaStatusUpdateRequest request,
                                                                           HttpServletRequest httpRequest) {
        UserRole role = AuthorizationGuard.getRequiredRole(httpRequest);
        Long companyId = getRequiredCompanyId(httpRequest);
        Long userId = AuthorizationGuard.getRequiredUserId(httpRequest);

        if (request == null || request.getStatus() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status da parada é obrigatório.");
        }

        ensureTripCanBeOperatedByRole(viagemId, role, userId, companyId);
        return ResponseEntity.ok(viagemOperacionalService.atualizarStatusDaParada(viagemId, paradaId, companyId, request.getStatus()));
    }

    @PostMapping("/{viagemId}/paradas/{paradaId}/concluir")
    public ResponseEntity<ViagemMotoristaResponse> concluirParada(@PathVariable Long viagemId,
                                                                  @PathVariable Long paradaId,
                                                                  HttpServletRequest httpRequest) {
        UserRole role = AuthorizationGuard.getRequiredRole(httpRequest);
        Long companyId = getRequiredCompanyId(httpRequest);
        Long userId = AuthorizationGuard.getRequiredUserId(httpRequest);

        ensureTripCanBeOperatedByRole(viagemId, role, userId, companyId);
        return ResponseEntity.ok(viagemOperacionalService.concluirParada(viagemId, paradaId, companyId));
    }

    @PostMapping("/{viagemId}/paradas/{paradaId}/pular")
    public ResponseEntity<ViagemMotoristaResponse> pularParada(@PathVariable Long viagemId,
                                                               @PathVariable Long paradaId,
                                                               HttpServletRequest httpRequest) {
        UserRole role = AuthorizationGuard.getRequiredRole(httpRequest);
        Long companyId = getRequiredCompanyId(httpRequest);
        Long userId = AuthorizationGuard.getRequiredUserId(httpRequest);

        ensureTripCanBeOperatedByRole(viagemId, role, userId, companyId);
        return ResponseEntity.ok(viagemOperacionalService.pularParada(viagemId, paradaId, companyId));
    }

    @PostMapping("/{viagemId}/paradas/{paradaId}/no-show")
    public ResponseEntity<ViagemMotoristaResponse> marcarNoShowParada(@PathVariable Long viagemId,
                                                                      @PathVariable Long paradaId,
                                                                      HttpServletRequest httpRequest) {
        UserRole role = AuthorizationGuard.getRequiredRole(httpRequest);
        Long companyId = getRequiredCompanyId(httpRequest);
        Long userId = AuthorizationGuard.getRequiredUserId(httpRequest);

        ensureTripCanBeOperatedByRole(viagemId, role, userId, companyId);
        return ResponseEntity.ok(viagemOperacionalService.marcarNoShowParada(viagemId, paradaId, companyId));
    }

    @PutMapping("/{viagemId}/rota")
    public ResponseEntity<ViagemMotoristaResponse> atualizarRotaDaViagem(@PathVariable Long viagemId,
                                                                         @RequestBody ViagemRotaUpdateRequest request,
                                                                         HttpServletRequest httpRequest) {
        UserRole role = AuthorizationGuard.getRequiredRole(httpRequest);
        Long companyId = getRequiredCompanyId(httpRequest);
        Long userId = AuthorizationGuard.getRequiredUserId(httpRequest);

        if (request == null || request.getParadaIds() == null || request.getParadaIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista de paradas é obrigatória.");
        }

        ensureTripCanBeOperatedByRole(viagemId, role, userId, companyId);
        return ResponseEntity.ok(viagemOperacionalService.atualizarRotaDaViagem(viagemId, companyId, request.getParadaIds()));
    }

    private void ensureTripCanBeOperatedByRole(Long viagemId, UserRole role, Long userId, Long companyId) {
        if (role.isCompanyAdmin()) {
            return;
        }
        if (role.isDriver() && viagemOperacionalService.viagemPertenceAoMotorista(viagemId, userId, companyId)) {
            return;
        }
        if (role.isDriver()) {
            throw notFoundInTenant("Viagem");
        }
        throw forbiddenForRole();
    }

    private LocalDate defaultDate(LocalDate data) {
        return data != null ? data : operationalDateProvider.today();
    }

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de empresa ausente");
    }

    private ResponseStatusException forbiddenForRole() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Papel sem permissão para esta operação");
    }

    private ResponseStatusException notFoundInTenant(String resourceName) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resourceName + " não encontrado na empresa atual.");
    }
}
