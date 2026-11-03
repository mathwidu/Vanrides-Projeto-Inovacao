package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.EtaConfidence;
import com.vanrides.vanrides.dto.LocationFreshness;
import com.vanrides.vanrides.dto.RouteRecomputeReason;
import com.vanrides.vanrides.dto.RouteSnapshotStatus;
import com.vanrides.vanrides.dto.TrackingStateResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ViagemTrackingStateResolver {

    private static final Duration LIVE_MAX_AGE = Duration.ofSeconds(30);
    private static final Duration DELAYED_MAX_AGE = Duration.ofSeconds(90);
    private static final Duration STALE_MAX_AGE = Duration.ofMinutes(5);

    private final Clock clock;

    public ViagemTrackingStateResolver() {
        this(Clock.systemDefaultZone());
    }

    ViagemTrackingStateResolver(Clock clock) {
        this.clock = clock;
    }

    public TrackingStateResponse resolve(Viagem viagem,
                                         List<ViagemParada> paradas,
                                         ViagemRotaGeograficaResponse rotaGeografica,
                                         LocalizacaoMotorista latestLocation,
                                         Long passengerId) {
        List<ViagemParada> orderedStops = paradas == null
                ? List.of()
                : paradas.stream()
                        .sorted(Comparator.comparing(ViagemParada::getOrdem, Comparator.nullsLast(Integer::compareTo)))
                        .toList();
        ViagemParada nextStop = orderedStops.stream()
                .filter(this::isPending)
                .findFirst()
                .orElse(null);

        RouteSnapshotStatus routeSnapshotStatus = resolveRouteSnapshotStatus(rotaGeografica);
        LocationFreshness locationFreshness = resolveTripLocationFreshness(viagem, latestLocation);
        EtaConfidence etaConfidence = resolveEtaConfidence(
                routeSnapshotStatus,
                locationFreshness,
                viagem,
                orderedStops
        );

        return new TrackingStateResponse(
                routeSnapshotStatus,
                locationFreshness,
                etaConfidence,
                (int) orderedStops.stream().filter(this::isPending).count(),
                (int) orderedStops.stream().filter(stop -> !isPending(stop)).count(),
                resolveStopsBeforePassenger(orderedStops, passengerId, rotaGeografica),
                nextStop == null ? null : nextStop.getId(),
                nextStop == null ? null : nextStop.getOrdem(),
                latestLocation == null ? null : latestLocation.getDataHora(),
                routeSnapshotStatus == RouteSnapshotStatus.MATERIALIZED
                        ? RouteRecomputeReason.TRIP_STARTED
                        : RouteRecomputeReason.NONE,
                routeSnapshotStatus == RouteSnapshotStatus.MATERIALIZED
                        ? rotaGeografica.getCalculadaEm()
                        : null
        );
    }

    public LocationFreshness resolveLocationFreshness(LocalizacaoMotorista location) {
        if (location == null
                || location.getLatitude() == null
                || location.getLongitude() == null
                || location.getDataHora() == null) {
            return LocationFreshness.UNAVAILABLE;
        }

        Duration age = Duration.between(location.getDataHora(), LocalDateTime.now(clock));
        if (age.isNegative() || age.compareTo(LIVE_MAX_AGE) <= 0) {
            return LocationFreshness.LIVE;
        }
        if (age.compareTo(DELAYED_MAX_AGE) <= 0) {
            return LocationFreshness.DELAYED;
        }
        if (age.compareTo(STALE_MAX_AGE) <= 0) {
            return LocationFreshness.STALE;
        }
        return LocationFreshness.UNAVAILABLE;
    }

    private LocationFreshness resolveTripLocationFreshness(Viagem viagem, LocalizacaoMotorista latestLocation) {
        if (viagem == null || viagem.getStatus() != ViagemStatus.IN_PROGRESS) {
            return LocationFreshness.PAUSED;
        }
        return resolveLocationFreshness(latestLocation);
    }

    private RouteSnapshotStatus resolveRouteSnapshotStatus(ViagemRotaGeograficaResponse rotaGeografica) {
        if (rotaGeografica == null) {
            return RouteSnapshotStatus.UNAVAILABLE;
        }
        if (rotaGeografica.isSnapshotOperacional()) {
            return RouteSnapshotStatus.MATERIALIZED;
        }
        if (rotaGeografica.getPolyline() != null
                || rotaGeografica.getNavegacao() != null
                || rotaGeografica.getNavegacaoRestante() != null
                || (rotaGeografica.getWaypoints() != null && !rotaGeografica.getWaypoints().isEmpty())
                || (rotaGeografica.getLegs() != null && !rotaGeografica.getLegs().isEmpty())) {
            return RouteSnapshotStatus.PLANNED;
        }
        return RouteSnapshotStatus.UNAVAILABLE;
    }

    private EtaConfidence resolveEtaConfidence(RouteSnapshotStatus routeSnapshotStatus,
                                               LocationFreshness locationFreshness,
                                               Viagem viagem,
                                               List<ViagemParada> paradas) {
        boolean hasRoute = routeSnapshotStatus != RouteSnapshotStatus.UNAVAILABLE;
        boolean hasEtaBasis = hasEtaBasis(viagem, paradas);

        if (locationFreshness == LocationFreshness.LIVE
                && routeSnapshotStatus == RouteSnapshotStatus.MATERIALIZED) {
            return EtaConfidence.HIGH;
        }
        if ((locationFreshness == LocationFreshness.LIVE || locationFreshness == LocationFreshness.DELAYED)
                && (hasRoute || hasEtaBasis)) {
            return EtaConfidence.MEDIUM;
        }
        if ((locationFreshness == LocationFreshness.STALE || routeSnapshotStatus == RouteSnapshotStatus.PLANNED)
                && (hasRoute || hasEtaBasis)) {
            return EtaConfidence.LOW;
        }
        return EtaConfidence.UNAVAILABLE;
    }

    private boolean hasEtaBasis(Viagem viagem, List<ViagemParada> paradas) {
        if (viagem != null && viagem.getEtaAtualizadoEm() != null) {
            return true;
        }
        return paradas.stream().anyMatch(parada -> parada.getEtaMinutos() != null);
    }

    private Integer resolveStopsBeforePassenger(List<ViagemParada> paradas,
                                                Long passengerId,
                                                ViagemRotaGeograficaResponse rotaGeografica) {
        if (passengerId == null) {
            return rotaGeografica == null ? null : rotaGeografica.getParadasAntesDoPassageiro();
        }

        ViagemParada passengerStop = paradas.stream()
                .filter(parada -> parada.getPassageiro() != null)
                .filter(parada -> passengerId.equals(parada.getPassageiro().getId()))
                .findFirst()
                .orElse(null);
        if (passengerStop == null || passengerStop.getOrdem() == null) {
            return null;
        }

        return (int) paradas.stream()
                .filter(this::isPending)
                .filter(parada -> parada.getOrdem() != null)
                .filter(parada -> parada.getOrdem() < passengerStop.getOrdem())
                .count();
    }

    private boolean isPending(ViagemParada parada) {
        return parada.getStatus() == ViagemParadaStatus.PENDING;
    }
}
