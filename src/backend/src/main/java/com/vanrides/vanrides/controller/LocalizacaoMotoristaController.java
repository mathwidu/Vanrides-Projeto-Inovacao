package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.DriverLocationPublishRequest;
import com.vanrides.vanrides.dto.LocalizacaoMotoristaResponse;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.service.DriverLocationWritePolicy;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.LocalizacaoMotoristaService;
import com.vanrides.vanrides.service.MotoristaService;
import com.vanrides.vanrides.service.PassengerLocationReadPolicy;
import com.vanrides.vanrides.service.ViagemRealtimePublisherService;
import com.vanrides.vanrides.service.ViagemTrackingStateResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/localizacao")
public class LocalizacaoMotoristaController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocalizacaoMotoristaController.class);

    @Autowired
    private LocalizacaoMotoristaService localizacaoService;

    @Autowired
    private MotoristaService motoristaService;

    @Autowired
    private GuardianAccessService guardianAccessService;

    @Autowired
    private ViagemRealtimePublisherService viagemRealtimePublisherService;

    @Autowired
    private ViagemTrackingStateResolver viagemTrackingStateResolver;

    @Autowired
    private DriverLocationWritePolicy driverLocationWritePolicy;

    @Autowired
    private PassengerLocationReadPolicy passengerLocationReadPolicy;

    @PostMapping("/{idMotorista}")
    public ResponseEntity<?> registrarLocalizacao(
            @PathVariable Long idMotorista,
            HttpServletRequest request,
            @RequestBody DriverLocationPublishRequest locationRequest) {
        AuthorizationGuard.requireAnyRole(request, UserRole.DRIVER);
        Long authenticatedDriverId = AuthorizationGuard.getRequiredUserId(request);
        driverLocationWritePolicy.requireOwnDriver(
                authenticatedDriverId,
                idMotorista
        );
        Long companyId = getRequiredCompanyId(request);

        Motorista motorista = motoristaService.buscarMotoristaPorId(authenticatedDriverId, companyId).orElse(null);
        if (motorista == null) {
            return ResponseEntity.status(404).body("Motorista não encontrado na empresa atual.");
        }

        LocalizacaoMotorista localizacao = new LocalizacaoMotorista(
                locationRequest.latitude(),
                locationRequest.longitude(),
                LocalDateTime.now(),
                motorista
        );

        LocalizacaoMotorista salva = localizacaoService.salvar(localizacao);
        viagemRealtimePublisherService.publishTodayTripSnapshotForMotorista(authenticatedDriverId, companyId);
        log.debug("Localizacao salva via REST para motoristaId={}", authenticatedDriverId);
        return ResponseEntity.ok(salva);
    }

    @GetMapping("/{idMotorista}")
    public ResponseEntity<?> ultimaLocalizacao(@PathVariable Long idMotorista, HttpServletRequest request) {
        Long companyId = getRequiredCompanyId(request);
        ensureCanReadMotorista(idMotorista, request, companyId);
        if (motoristaService.buscarMotoristaPorId(idMotorista, companyId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        LocalizacaoMotorista ultima = localizacaoService.buscarUltimaPorMotorista(idMotorista, companyId);
        if (ultima == null) {
            log.debug("Nenhuma localizacao encontrada para motoristaId={}", idMotorista);
            return ResponseEntity.noContent().build();
        }

        log.debug("Ultima localizacao retornada para motoristaId={}", idMotorista);
        return ResponseEntity.ok(new LocalizacaoMotoristaResponse(
                ultima.getLatitude(),
                ultima.getLongitude(),
                ultima.getDataHora(),
                viagemTrackingStateResolver.resolveLocationFreshness(ultima)
        ));
    }

    @GetMapping("/{idMotorista}/historico")
    public ResponseEntity<List<LocalizacaoMotorista>> historico(@PathVariable Long idMotorista, HttpServletRequest request) {
        log.debug("Historico de localizacoes solicitado para motoristaId={}", idMotorista);
        AuthorizationGuard.requireAnyRole(request, UserRole.DRIVER, UserRole.COMPANY_ADMIN, UserRole.GUARDIAN);
        Long companyId = getRequiredCompanyId(request);
        ensureCanReadMotorista(idMotorista, request, companyId);
        if (motoristaService.buscarMotoristaPorId(idMotorista, companyId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(localizacaoService.buscarHistoricoPorMotorista(idMotorista, companyId));
    }

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Long value) {
            return value;
        }
        throw new IllegalStateException("userCompanyId ausente no request.");
    }

    private void ensureCanReadMotorista(Long motoristaId, HttpServletRequest request, Long companyId) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        Long userId = AuthorizationGuard.getRequiredUserId(request);

        boolean canRead = switch (role) {
            case DRIVER -> userId.equals(motoristaId);
            case PASSENGER -> {
                passengerLocationReadPolicy.requireCanReadMotorista(userId, motoristaId, companyId);
                yield true;
            }
            case GUARDIAN -> guardianAccessService.hasLinkedMotorista(userId, motoristaId, companyId);
            case COMPANY_ADMIN -> true;
        };

        if (!canRead) {
            throw motoristaNotFound();
        }
    }

    private org.springframework.web.server.ResponseStatusException motoristaNotFound() {
        return new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Motorista não encontrado na empresa atual."
        );
    }
}
