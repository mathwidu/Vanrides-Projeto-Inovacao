package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.PublicAccountRegistrationResponse;
import com.vanrides.vanrides.dto.PublicDriverRegistrationRequest;
import com.vanrides.vanrides.dto.PublicMotoristaSummaryResponse;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.service.MotoristaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/motoristas")
public class MotoristaController {

    @Autowired
    private MotoristaService motoristaService;

    @PostMapping
    public ResponseEntity<?> criarMotorista(@RequestBody PublicDriverRegistrationRequest motorista) {
        // Verifica se a senha foi enviada
        if (motorista.senha() == null || motorista.senha().isBlank()) {
            return ResponseEntity.badRequest().body("Senha é obrigatória.");
        }

        // Criptografa a senha diretamente na service
        Motorista salvo = motoristaService.registrarMotoristaPublico(motorista);
        return ResponseEntity.ok(toRegistrationResponse(salvo));
    }

    @GetMapping
    public ResponseEntity<List<PublicMotoristaSummaryResponse>> listarMotoristas() {
        List<Motorista> motoristas = motoristaService.listarTodosMotoristas();
        return ResponseEntity.ok(motoristas.stream()
                .map(this::toPublicSummary)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Motorista> buscarPorId(@PathVariable Long id, HttpServletRequest request) {
        Optional<Motorista> motorista = motoristaService.buscarMotoristaPorId(id, getRequiredCompanyId(request));
        return motorista.map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPorId(@PathVariable Long id, HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        Optional<Motorista> motorista = motoristaService.buscarMotoristaPorId(id, getRequiredCompanyId(request));

        if (motorista.isPresent()) {
            motoristaService.deletarMotorista(id);
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

    private PublicMotoristaSummaryResponse toPublicSummary(Motorista motorista) {
        return new PublicMotoristaSummaryResponse(
                motorista.getId(),
                motorista.getNome()
        );
    }

    private PublicAccountRegistrationResponse toRegistrationResponse(Motorista motorista) {
        return new PublicAccountRegistrationResponse(
                motorista.getId(),
                motorista.getNome(),
                motorista.getEmail(),
                motorista.getTelefone(),
                motorista.isEmailVerified(),
                motorista.getEmailVerificationLastSentAt()
        );
    }
}
