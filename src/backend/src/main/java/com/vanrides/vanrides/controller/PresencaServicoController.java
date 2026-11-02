package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.PresencaServicoRequest;
import com.vanrides.vanrides.dto.PresencaServicoResponse;
import com.vanrides.vanrides.dto.PresencaServicoStatusUpdateRequest;
import com.vanrides.vanrides.model.PresencaServicoOrigem;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.PresencaServicoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/presencas-servico")
public class PresencaServicoController {

    @Autowired
    private PresencaServicoService presencaServicoService;

    @Autowired
    private GuardianAccessService guardianAccessService;

    @GetMapping("/passageiro/{passageiroId}")
    public ResponseEntity<List<PresencaServicoResponse>> listarPorPassageiro(
            @PathVariable Long passageiroId,
            @RequestParam("dataInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam("dataFim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            HttpServletRequest request) {
        Long companyId = getRequiredCompanyId(request);
        ensureCanAccessPassageiro(request, passageiroId, companyId);
        return ResponseEntity.ok(presencaServicoService.listarPresencasPorPassageiro(
                companyId,
                passageiroId,
                dataInicio,
                dataFim
        ));
    }

    @PostMapping
    public ResponseEntity<PresencaServicoResponse> criarPresenca(@RequestBody PresencaServicoRequest body,
                                                                 HttpServletRequest request) {
        Long companyId = getRequiredCompanyId(request);
        Long passageiroId = getRequiredPassageiroId(body);
        ensureCanAccessPassageiro(request, passageiroId, companyId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(presencaServicoService.criarPresenca(companyId, body, origemFromRole(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PresencaServicoResponse> atualizarStatus(@PathVariable Long id,
                                                                   @RequestBody PresencaServicoStatusUpdateRequest body,
                                                                   HttpServletRequest request) {
        Long companyId = getRequiredCompanyId(request);
        Long passageiroId = presencaServicoService.buscarPassageiroIdDaPresenca(companyId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Presença por serviço não encontrada na empresa atual."));
        ensureCanAccessPassageiro(request, passageiroId, companyId);
        return ResponseEntity.ok(presencaServicoService.atualizarStatus(
                companyId,
                id,
                body == null ? null : body.getStatus(),
                origemFromRole(request)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPresenca(@PathVariable Long id, HttpServletRequest request) {
        Long companyId = getRequiredCompanyId(request);
        Long passageiroId = presencaServicoService.buscarPassageiroIdDaPresenca(companyId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Presença por serviço não encontrada na empresa atual."));
        ensureCanAccessPassageiro(request, passageiroId, companyId);
        presencaServicoService.deletarPresenca(companyId, id);
        return ResponseEntity.noContent().build();
    }

    private Long getRequiredPassageiroId(PresencaServicoRequest body) {
        if (body == null || body.getPassageiroId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passageiro é obrigatório.");
        }
        return body.getPassageiroId();
    }

    private void ensureCanAccessPassageiro(HttpServletRequest request, Long passageiroId, Long companyId) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);

        if (role.isPassenger()) {
            if (!userId.equals(passageiroId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado na empresa atual.");
            }
            return;
        }
        if (role.isGuardian()) {
            if (!guardianAccessService.hasLinkedPassageiro(userId, passageiroId, companyId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado na empresa atual.");
            }
            return;
        }
        if (role.isCompanyAdmin()) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Papel sem permissão para esta operação");
    }

    private PresencaServicoOrigem origemFromRole(HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        if (role.isPassenger()) {
            return PresencaServicoOrigem.PASSENGER;
        }
        if (role.isGuardian()) {
            return PresencaServicoOrigem.GUARDIAN;
        }
        if (role.isCompanyAdmin()) {
            return PresencaServicoOrigem.ADMIN;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Papel sem permissão para esta operação");
    }

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de empresa ausente");
    }
}
