package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.NavigationRoutePlanInstructionResponse;
import com.vanrides.vanrides.dto.NavigationRoutePlanLegResponse;
import com.vanrides.vanrides.dto.NavigationRoutePlanResponse;
import com.vanrides.vanrides.dto.ViagemRotaLegResponse;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.service.maps.MapsPlatformClientUnavailableException;
import com.vanrides.vanrides.service.maps.MapsPlatformRouteClient;
import com.vanrides.vanrides.service.maps.MapsNavigationInstruction;
import com.vanrides.vanrides.service.maps.MapsRouteLeg;
import com.vanrides.vanrides.service.maps.MapsRouteOptions;
import com.vanrides.vanrides.service.maps.MapsRoutePlanRequest;
import com.vanrides.vanrides.service.maps.MapsRoutePlanResponse;
import com.vanrides.vanrides.service.maps.MapsRouteStop;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class MapsPlatformTripRouteFacade {

    private static final String INITIAL_REGION = "BR-RS";
    private static final String VEHICLE_TYPE = "van_school_safe";

    private final MapsPlatformRouteClient mapsPlatformRouteClient;

    public MapsPlatformTripRouteFacade(MapsPlatformRouteClient mapsPlatformRouteClient) {
        this.mapsPlatformRouteClient = mapsPlatformRouteClient;
    }

    Optional<MapsPlatformPlannedRoute> planRoute(Viagem viagem,
                                                 LocalizacaoMotorista localizacaoAtual,
                                                 List<ViagemParada> paradas) {
        if (!mapsPlatformRouteClient.isEnabled()) {
            return Optional.empty();
        }
        RequestContext requestContext = buildRequest(viagem, localizacaoAtual, paradas);
        if (requestContext.request().stops().size() < 2) {
            return Optional.empty();
        }
        try {
            MapsRoutePlanResponse response = mapsPlatformRouteClient.planRoute(requestContext.request());
            if (!canPromote(response)) {
                return Optional.empty();
            }
            return Optional.of(toPlannedRoute(response, requestContext.labelsByStopId()));
        } catch (MapsPlatformClientUnavailableException error) {
            return Optional.empty();
        }
    }

    private RequestContext buildRequest(Viagem viagem,
                                        LocalizacaoMotorista localizacaoAtual,
                                        List<ViagemParada> paradas) {
        List<MapsRouteStop> stops = new ArrayList<>();
        Map<String, String> labelsByStopId = new LinkedHashMap<>();
        if (localizacaoAtual != null) {
            addStop(stops, labelsByStopId, "driver", viagem.getMotorista().getNome(), localizacaoAtual.getLatitude(), localizacaoAtual.getLongitude());
        }
        paradas.stream()
                .filter(parada -> parada.getPassageiro() != null)
                .filter(parada -> parada.getPassageiro().getLatitude() != null && parada.getPassageiro().getLongitude() != null)
                .sorted(Comparator.comparing(ViagemParada::getOrdem))
                .forEach(parada -> addStop(
                        stops,
                        labelsByStopId,
                        "stop-" + parada.getId(),
                        parada.getPassageiro().getNome(),
                        parada.getPassageiro().getLatitude(),
                        parada.getPassageiro().getLongitude()
                ));
        addDestinationWhenAvailable(viagem, stops, labelsByStopId);
        return new RequestContext(
                new MapsRoutePlanRequest(
                        resolveRegion(paradas),
                        VEHICLE_TYPE,
                        stops,
                        new MapsRouteOptions(false, true, true)
                ),
                labelsByStopId
        );
    }

    private void addDestinationWhenAvailable(Viagem viagem, List<MapsRouteStop> stops, Map<String, String> labelsByStopId) {
        RouteCoordinate destination = resolveDestination(viagem);
        if (destination == null || (!stops.isEmpty() && sameCoordinate(stops.get(stops.size() - 1), destination))) {
            return;
        }
        addStop(stops, labelsByStopId, "destination", destination.label(), destination.latitude(), destination.longitude());
    }

    private RouteCoordinate resolveDestination(Viagem viagem) {
        if (viagem.getDestinoFinalLatitudeSnapshot() != null && viagem.getDestinoFinalLongitudeSnapshot() != null) {
            return new RouteCoordinate(labelOrDefault(viagem.getDestinoFinalNomeSnapshot(), "Destino final"),
                    viagem.getDestinoFinalLatitudeSnapshot(), viagem.getDestinoFinalLongitudeSnapshot());
        }
        RotaBase rotaBase = viagem.getRotaBase();
        if (rotaBase != null && rotaBase.getDestinoFinalLatitude() != null && rotaBase.getDestinoFinalLongitude() != null) {
            return new RouteCoordinate(labelOrDefault(rotaBase.getDestinoFinalNome(), "Destino final"),
                    rotaBase.getDestinoFinalLatitude(), rotaBase.getDestinoFinalLongitude());
        }
        return null;
    }

    private void addStop(List<MapsRouteStop> stops,
                         Map<String, String> labelsByStopId,
                         String id,
                         String label,
                         double latitude,
                         double longitude) {
        stops.add(new MapsRouteStop(id, labelOrDefault(label, id), latitude, longitude));
        labelsByStopId.put(id, labelOrDefault(label, id));
    }

    private boolean canPromote(MapsRoutePlanResponse response) {
        return response != null
                && "validada".equalsIgnoreCase(response.coverageStatus())
                && "ready".equalsIgnoreCase(response.offlinePackStatus())
                && response.provider() != null
                && !"fixture".equalsIgnoreCase(response.provider())
                && response.polyline() != null
                && response.polyline().size() >= 2;
    }

    private MapsPlatformPlannedRoute toPlannedRoute(MapsRoutePlanResponse response, Map<String, String> labelsByStopId) {
        return new MapsPlatformPlannedRoute(
                "MAPS_PLATFORM_" + response.provider().replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT),
                PolylineEncoder.encode(response.polyline()),
                buildLegs(response.legs(), labelsByStopId),
                toNavigationRoutePlanResponse(response)
        );
    }

    private NavigationRoutePlanResponse toNavigationRoutePlanResponse(MapsRoutePlanResponse response) {
        return new NavigationRoutePlanResponse(
                response.routeId(),
                response.provider(),
                response.region(),
                response.vehicleType(),
                response.coverageStatus(),
                response.offlinePackStatus(),
                response.qualityScore(),
                response.fallbackReason(),
                response.estimatedDurationSeconds(),
                response.estimatedDistanceMeters(),
                response.polyline(),
                toNavigationRoutePlanLegs(response.legs()),
                toNavigationRoutePlanInstructions(response.instructions())
        );
    }

    private List<NavigationRoutePlanLegResponse> toNavigationRoutePlanLegs(List<MapsRouteLeg> legs) {
        if (legs == null) {
            return List.of();
        }
        return legs.stream()
                .map(leg -> new NavigationRoutePlanLegResponse(
                        leg.fromStopId(),
                        leg.toStopId(),
                        leg.distanceMeters(),
                        leg.durationSeconds()
                ))
                .toList();
    }

    private List<NavigationRoutePlanInstructionResponse> toNavigationRoutePlanInstructions(List<MapsNavigationInstruction> instructions) {
        if (instructions == null) {
            return List.of();
        }
        return instructions.stream()
                .map(instruction -> new NavigationRoutePlanInstructionResponse(
                        instruction.index(),
                        instruction.text(),
                        instruction.voiceText(),
                        instruction.distanceMeters(),
                        instruction.maneuver()
                ))
                .toList();
    }

    private List<ViagemRotaLegResponse> buildLegs(List<MapsRouteLeg> legs, Map<String, String> labelsByStopId) {
        if (legs == null) {
            return List.of();
        }
        List<ViagemRotaLegResponse> response = new ArrayList<>();
        for (int index = 0; index < legs.size(); index++) {
            MapsRouteLeg leg = legs.get(index);
            response.add(new ViagemRotaLegResponse(
                    index + 1,
                    labelsByStopId.getOrDefault(leg.fromStopId(), leg.fromStopId()),
                    labelsByStopId.getOrDefault(leg.toStopId(), leg.toStopId()),
                    leg.distanceMeters(),
                    leg.durationSeconds()
            ));
        }
        return response;
    }

    private String resolveRegion(List<ViagemParada> paradas) {
        return paradas.stream()
                .map(ViagemParada::getPassageiro)
                .filter(passageiro -> passageiro != null && passageiro.getEstado() != null)
                .map(Passageiro::getEstado)
                .map(String::trim)
                .filter(estado -> estado.equalsIgnoreCase("RS") || estado.equalsIgnoreCase("Rio Grande do Sul"))
                .findFirst()
                .map(estado -> INITIAL_REGION)
                .orElse(INITIAL_REGION);
    }

    private boolean sameCoordinate(MapsRouteStop stop, RouteCoordinate coordinate) {
        return Double.compare(stop.lat(), coordinate.latitude()) == 0
                && Double.compare(stop.lon(), coordinate.longitude()) == 0;
    }

    private String labelOrDefault(String label, String fallback) {
        return label == null || label.isBlank() ? fallback : label;
    }

    private record RequestContext(MapsRoutePlanRequest request, Map<String, String> labelsByStopId) {
    }

    private record RouteCoordinate(String label, double latitude, double longitude) {
    }
}
