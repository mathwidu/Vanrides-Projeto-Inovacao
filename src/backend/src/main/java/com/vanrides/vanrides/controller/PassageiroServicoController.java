package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.PassageiroServicoDisponivelResponse;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.PresencaServicoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/passageiros")
public class PassageiroServicoController {

    @Autowired
    private PresencaServicoService presencaServicoService;

    @Autowired
    private GuardianAccessService guardianAccessService;

    @GetMapping("/{passageiroId}/servicos-disponiveis")
    public ResponseEntity<List<PassageiroServicoDisponivelResponse>> listarServicosDisponiveis(
            @PathVariable Long passageiroId,
            @RequestParam("data") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            HttpServletRequest request) {
        Long companyId = getRequiredCompanyId(request);
        ensureCanAccessPassageiro(request, passageiroId, companyId);
        return ResponseEntity.ok(presencaServicoService.listarServicosDisponiveis(companyId, passageiroId, data));
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

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de empresa ausente");
    }
}
