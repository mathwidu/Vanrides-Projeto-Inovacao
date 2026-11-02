package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.ViagemNavegacaoResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.dto.ViagemRotaLegResponse;
import com.vanrides.vanrides.dto.ViagemRotaWaypointResponse;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ViagemRotaGeograficaService {
    static final String PROVIDER_STATIC_GEOMETRY = "STATIC_GEOMETRY_V1";
    private final LocalizacaoMotoristaService localizacaoMotoristaService;
    private final GoogleDirectionsRoutingService googleDirectionsRoutingService;
    private final MapsPlatformTripRouteFacade mapsPlatformTripRouteFacade;
    public ViagemRotaGeograficaService(LocalizacaoMotoristaService localizacaoMotoristaService,
                                       GoogleDirectionsRoutingService googleDirectionsRoutingService,
                                       MapsPlatformTripRouteFacade mapsPlatformTripRouteFacade) {
        this.localizacaoMotoristaService = localizacaoMotoristaService;
        this.googleDirectionsRoutingService = googleDirectionsRoutingService;
        this.mapsPlatformTripRouteFacade = mapsPlatformTripRouteFacade;
    }
    public ViagemRotaGeograficaResponse buildForTrip(Viagem viagem,
                                                     List<ViagemParada> paradas,
                                                     Long passengerId) {
        return buildForTrip(viagem, paradas, passengerId, true);
    }
    public ViagemRotaGeograficaResponse calcularEAplicarSnapshotOperacional(Viagem viagem,
                                                                            List<ViagemParada> paradas) {
        ViagemRotaGeograficaResponse response = buildForTrip(viagem, paradas, null, false);
        LocalDateTime calculadaEm = LocalDateTime.now();
        viagem.setRotaGeograficaProviderSnapshot(response.getProvider());
        viagem.setRotaGeograficaPolylineSnapshot(response.getPolyline());
        viagem.setRotaGeograficaNavegacaoUrlSnapshot(response.getNavegacao() == null ? null : response.getNavegacao().getUrl());
        viagem.setRotaGeograficaCalculadaEm(calculadaEm);
        return ViagemRouteAuditBuilder.addAudit(
                viagem,
                paradas,
                new ViagemRotaGeograficaResponse(
                        response.getProvider(),
                        response.getPolyline(),
                        response.getNavegacao(),
                        response.getWaypoints(),
                        response.getLegs(),
                        response.getFilaTotalParadas(),
                        response.getPosicaoPassageiroNaFila(),
                        response.getParadasAntesDoPassageiro(),
                        response.getNavegacaoRestante(),
                        calculadaEm,
                        true
                )
        );
    }

    private ViagemRotaGeograficaResponse buildForTrip(Viagem viagem,
                                                      List<ViagemParada> paradas,
                                                      Long passengerId,
                                                      boolean allowOperationalSnapshot) {
        LocalizacaoMotorista localizacaoAtual = localizacaoMotoristaService.buscarUltimaPorMotorista(
                viagem.getMotorista().getId(),
                viagem.getCompany().getId()
        );
        List<ViagemRotaStopPoint> stopPoints = ViagemRotaStopPointResolver.resolve(paradas, passengerId);
        if (stopPoints.isEmpty()) {
            return ViagemRouteAuditBuilder.addAudit(
                    viagem,
                    paradas,
                    new ViagemRotaGeograficaResponse(
                            PROVIDER_STATIC_GEOMETRY,
                            null,
                            null,
                            List.of(),
                            List.of(),
                            0,
                            null,
                            null
                    )
            );
        }
        RoutePoint origin = resolveOrigin(localizacaoAtual, stopPoints);
        RoutePoint destination = resolveDestination(viagem, stopPoints);
        List<RoutePoint> orderedPath = resolveOrderedPath(origin, destination, stopPoints);
        List<ViagemRotaWaypointResponse> waypoints = buildWaypoints(viagem, localizacaoAtual, stopPoints);
        ViagemNavegacaoResponse navegacaoRestante = buildRemainingNavigation(viagem, localizacaoAtual, stopPoints);
        Integer passengerPosition = stopPoints.stream()
                .filter(ViagemRotaStopPoint::paradaDoPassageiro)
                .map(ViagemRotaStopPoint::ordem)
                .findFirst()
                .orElse(null);
        if (allowOperationalSnapshot && hasOperationalRouteSnapshot(viagem)) {
            ViagemNavegacaoResponse navegacao = buildSnapshotNavigation(viagem, origin, destination, orderedPath);
            return ViagemRouteAuditBuilder.addAudit(
                    viagem,
                    paradas,
                    new ViagemRotaGeograficaResponse(
                            viagem.getRotaGeograficaProviderSnapshot(),
                            viagem.getRotaGeograficaPolylineSnapshot(),
                            navegacao,
                            waypoints,
                            buildLegs(orderedPath),
                            stopPoints.size(),
                            passengerPosition,
                            passengerPosition == null ? null : Math.max(passengerPosition - 1, 0),
                            navegacaoRestante,
                            viagem.getRotaGeograficaCalculadaEm(),
                            true
                )
            );
        }
        Optional<MapsPlatformPlannedRoute> mapsPlatformRoute = mapsPlatformTripRouteFacade.planRoute(viagem, localizacaoAtual, paradas);
        if (mapsPlatformRoute.isPresent()) {
            return ViagemRouteAuditBuilder.addAudit(
                    viagem,
                    paradas,
                    mapsPlatformRoute.get().toRotaGeograficaResponse(
                            buildNavigation(origin, destination, orderedPath),
                            waypoints,
                            stopPoints.size(),
                            passengerPosition,
                            navegacaoRestante
                    )
            );
        }
        Optional<GoogleDirectionsRoutingService.ComputedRoute> computedRoute = googleDirectionsRoutingService
                .compute(toRoutingPoints(orderedPath));
        List<ViagemRotaLegResponse> legs = computedRoute.isPresent()
                ? buildProviderLegs(computedRoute.get())
                : buildLegs(orderedPath);
        String polyline = computedRoute.isPresent()
                ? computedRoute.get().polyline()
                : orderedPath.size() < 2 ? null : PolylineEncoder.encode(orderedPath.stream()
                        .map(point -> List.of(point.latitude(), point.longitude()))
                        .toList());
        String provider = computedRoute.isPresent()
                ? computedRoute.get().provider()
                : PROVIDER_STATIC_GEOMETRY;
        return ViagemRouteAuditBuilder.addAudit(
                viagem,
                paradas,
                new ViagemRotaGeograficaResponse(
                        provider,
                        polyline,
                        buildNavigation(origin, destination, orderedPath),
                        waypoints,
                        legs,
                        stopPoints.size(),
                        passengerPosition,
                        passengerPosition == null ? null : Math.max(passengerPosition - 1, 0),
                        navegacaoRestante,
                        null,
                        false
                )
        );
    }

    private boolean hasOperationalRouteSnapshot(Viagem viagem) {
        return viagem.getRotaGeograficaCalculadaEm() != null
                && viagem.getRotaGeograficaProviderSnapshot() != null
                && !viagem.getRotaGeograficaProviderSnapshot().isBlank();
    }
    private ViagemNavegacaoResponse buildSnapshotNavigation(Viagem viagem,
                                                            RoutePoint origin,
                                                            RoutePoint destination,
                                                            List<RoutePoint> orderedPath) {
        ViagemNavegacaoResponse navegacao = buildNavigation(origin, destination, orderedPath);
        if (navegacao == null || viagem.getRotaGeograficaNavegacaoUrlSnapshot() == null
                || viagem.getRotaGeograficaNavegacaoUrlSnapshot().isBlank()) {
            return navegacao;
        }
        return new ViagemNavegacaoResponse(
                navegacao.getProvider(),
                viagem.getRotaGeograficaNavegacaoUrlSnapshot(),
                navegacao.getOrigem(),
                navegacao.getDestino(),
                navegacao.getQuantidadeWaypoints()
        );
    }
    private RoutePoint resolveOrigin(LocalizacaoMotorista localizacaoAtual, List<ViagemRotaStopPoint> stopPoints) {
        if (localizacaoAtual != null) {
            return new RoutePoint("DRIVER", "Motorista", localizacaoAtual.getLatitude(), localizacaoAtual.getLongitude(), 0);
        }

        ViagemRotaStopPoint firstStop = stopPoints.get(0);
        return new RoutePoint("STOP", firstStop.nome(), firstStop.latitude(), firstStop.longitude(), firstStop.ordem());
    }

    private RoutePoint resolveDestination(Viagem viagem, List<ViagemRotaStopPoint> stopPoints) {
        if (viagem.getDestinoFinalLatitudeSnapshot() != null
                && viagem.getDestinoFinalLongitudeSnapshot() != null) {
            String nome = viagem.getDestinoFinalNomeSnapshot() != null
                    ? viagem.getDestinoFinalNomeSnapshot()
                    : "Destino final";
            return new RoutePoint("DESTINATION", nome, viagem.getDestinoFinalLatitudeSnapshot(), viagem.getDestinoFinalLongitudeSnapshot(), stopPoints.size() + 1);
        }

        RotaBase rotaBase = viagem.getRotaBase();
        if (rotaBase != null
                && rotaBase.getDestinoFinalLatitude() != null
                && rotaBase.getDestinoFinalLongitude() != null) {
            String nome = rotaBase.getDestinoFinalNome() != null
                    ? rotaBase.getDestinoFinalNome()
                    : "Destino final";
            return new RoutePoint("DESTINATION", nome, rotaBase.getDestinoFinalLatitude(), rotaBase.getDestinoFinalLongitude(), stopPoints.size() + 1);
        }
        ViagemRotaStopPoint lastStop = stopPoints.get(stopPoints.size() - 1);
        return new RoutePoint("STOP", lastStop.nome(), lastStop.latitude(), lastStop.longitude(), lastStop.ordem());
    }

    private List<RoutePoint> resolveOrderedPath(RoutePoint origin,
                                                RoutePoint destination,
                                                List<ViagemRotaStopPoint> stopPoints) {
        List<RoutePoint> points = new ArrayList<>();
        points.add(origin);
        for (ViagemRotaStopPoint stop : stopPoints) {
            points.add(new RoutePoint("STOP", stop.nome(), stop.latitude(), stop.longitude(), stop.ordem()));
        }
        if (!sameCoordinate(points.get(points.size() - 1), destination)) {
            points.add(destination);
        }
        return collapseDuplicatedCoordinates(points);
    }

    private ViagemNavegacaoResponse buildRemainingNavigation(Viagem viagem,
                                                             LocalizacaoMotorista localizacaoAtual,
                                                             List<ViagemRotaStopPoint> stopPoints) {
        List<ViagemRotaStopPoint> pendingStops = stopPoints.stream()
                .filter(stop -> stop.status() == ViagemParadaStatus.PENDING)
                .toList();
        if (pendingStops.isEmpty()) {
            return null;
        }

        RoutePoint origin = resolveOrigin(localizacaoAtual, pendingStops);
        RoutePoint destination = resolveDestination(viagem, pendingStops);
        List<RoutePoint> remainingPath = resolveOrderedPath(origin, destination, pendingStops);
        return buildNavigation(origin, destination, remainingPath);
    }

    private List<RoutePoint> collapseDuplicatedCoordinates(List<RoutePoint> points) {
        List<RoutePoint> normalized = new ArrayList<>();
        for (RoutePoint point : points) {
            if (normalized.isEmpty() || !sameCoordinate(normalized.get(normalized.size() - 1), point)) {
                normalized.add(point);
            }
        }
        return normalized;
    }

    private List<ViagemRotaWaypointResponse> buildWaypoints(Viagem viagem,
                                                            LocalizacaoMotorista localizacaoAtual,
                                                            List<ViagemRotaStopPoint> stopPoints) {
        List<ViagemRotaWaypointResponse> waypoints = new ArrayList<>();
        if (localizacaoAtual != null) {
            waypoints.add(new ViagemRotaWaypointResponse(
                    "DRIVER",
                    null,
                    null,
                    viagem.getMotorista().getNome(),
                    localizacaoAtual.getLatitude(),
                    localizacaoAtual.getLongitude(),
                    0,
                    false,
                    false
            ));
        }

        for (ViagemRotaStopPoint stop : stopPoints) {
            waypoints.add(new ViagemRotaWaypointResponse(
                    "STOP",
                    stop.paradaId(),
                    stop.passageiroId(),
                    stop.nome(),
                    stop.latitude(),
                    stop.longitude(),
                    stop.ordem(),
                    stop.proximaParada(),
                    stop.paradaDoPassageiro()
            ));
        }
        return waypoints;
    }

    private List<ViagemRotaLegResponse> buildLegs(List<RoutePoint> orderedPath) {
        List<ViagemRotaLegResponse> legs = new ArrayList<>();
        for (int index = 0; index < orderedPath.size() - 1; index++) {
            RoutePoint current = orderedPath.get(index);
            RoutePoint next = orderedPath.get(index + 1);
            int distanceMeters = haversineMeters(current.latitude(), current.longitude(), next.latitude(), next.longitude());
            int durationSeconds = Math.max(60, (int) Math.round(distanceMeters / 6.94d));
            legs.add(new ViagemRotaLegResponse(
                    index + 1,
                    current.nome(),
                    next.nome(),
                    distanceMeters,
                    durationSeconds
            ));
        }
        return legs;
    }

    private List<ViagemRotaLegResponse> buildProviderLegs(GoogleDirectionsRoutingService.ComputedRoute computedRoute) {
        return computedRoute.legs().stream()
                .map(leg -> new ViagemRotaLegResponse(
                        leg.ordem(),
                        leg.fromName(),
                        leg.toName(),
                        leg.distanceMeters(),
                        leg.durationSeconds()
                ))
                .toList();
    }

    private ViagemNavegacaoResponse buildNavigation(RoutePoint origin,
                                                    RoutePoint destination,
                                                    List<RoutePoint> orderedPath) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl("https://www.google.com/maps/dir/")
                .queryParam("api", 1)
                .queryParam("destination", formatCoordinate(destination.latitude(), destination.longitude()))
                .queryParam("travelmode", "driving");

        if (!sameCoordinate(origin, destination)) {
            builder.queryParam("origin", formatCoordinate(origin.latitude(), origin.longitude()));
        }

        List<RoutePoint> waypoints = orderedPath.size() <= 2
                ? List.of()
                : orderedPath.subList(1, orderedPath.size() - 1);
        if (!waypoints.isEmpty()) {
            builder.queryParam("waypoints", waypoints.stream()
                    .map(point -> formatCoordinate(point.latitude(), point.longitude()))
                    .reduce((left, right) -> left + "|" + right)
                    .orElse(""));
        }

        return new ViagemNavegacaoResponse(
                "GOOGLE_MAPS_HANDOFF",
                builder.toUriString(),
                origin.nome(),
                destination.nome(),
                waypoints.size()
        );
    }

    private boolean sameCoordinate(RoutePoint left, RoutePoint right) {
        return Double.compare(left.latitude(), right.latitude()) == 0
                && Double.compare(left.longitude(), right.longitude()) == 0;
    }

    private List<GoogleDirectionsRoutingService.RoutingPoint> toRoutingPoints(List<RoutePoint> orderedPath) {
        return orderedPath.stream()
                .map(point -> new GoogleDirectionsRoutingService.RoutingPoint(
                        point.nome(),
                        point.latitude(),
                        point.longitude()
                ))
                .toList();
    }

    private int haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadius = 6371e3;
        double latRad1 = Math.toRadians(lat1);
        double latRad2 = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(latRad1) * Math.cos(latRad2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(earthRadius * c);
    }

    private String formatCoordinate(Double latitude, Double longitude) {
        return String.format(Locale.US, "%.6f,%.6f", latitude, longitude);
    }

    private record RoutePoint(String tipo,
                              String nome,
                              Double latitude,
                              Double longitude,
                              Integer ordem) {
    }
}
