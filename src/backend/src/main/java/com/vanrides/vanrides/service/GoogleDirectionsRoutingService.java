package com.vanrides.vanrides.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class GoogleDirectionsRoutingService {

    static final String PROVIDER_GOOGLE_DIRECTIONS = "GOOGLE_DIRECTIONS_V1";
    static final String PROVIDER_GOOGLE_DIRECTIONS_HYBRID = "GOOGLE_DIRECTIONS_HYBRID_V1";

    private static final Logger log = LoggerFactory.getLogger(GoogleDirectionsRoutingService.class);

    private final String apiKey;
    private final String routingProvider;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public GoogleDirectionsRoutingService(@Value("${google.api.key:}") String apiKey, @Value("${vanrides.routing.provider:static}") String routingProvider) {
        this(apiKey, routingProvider, new RestTemplate(), new ObjectMapper());
    }

    GoogleDirectionsRoutingService(String apiKey, String routingProvider, RestTemplate restTemplate) {
        this(apiKey, routingProvider, restTemplate, new ObjectMapper());
    }

    GoogleDirectionsRoutingService(String apiKey, String routingProvider, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.routingProvider = routingProvider;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ComputedRoute> compute(List<RoutingPoint> orderedPath) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        List<RoutingPoint> normalizedPath = collapseDuplicatedCoordinates(orderedPath);
        if (normalizedPath.size() < 2) {
            return Optional.empty();
        }

        Optional<ComputedRoute> singleRequestRoute = computeSingleRequest(normalizedPath);
        if (singleRequestRoute.isPresent()) {
            return singleRequestRoute;
        }

        return computeLegByLegRoute(normalizedPath);
    }

    private Optional<ComputedRoute> computeSingleRequest(List<RoutingPoint> normalizedPath) {
        try {
            String response = restTemplate.getForObject(buildUrl(normalizedPath), String.class);
            if (response == null || response.isBlank()) {
                return Optional.empty();
            }

            JsonNode json = objectMapper.readTree(response);
            String status = json.path("status").asText("");
            if (!"OK".equalsIgnoreCase(status)) {
                log.warn("Google Directions respondeu com status {}", status);
                return Optional.empty();
            }

            JsonNode routes = json.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                return Optional.empty();
            }

            JsonNode route = routes.get(0);
            String polyline = route.path("overview_polyline").path("points").asText(null);

            JsonNode legsJson = route.path("legs");
            if (polyline == null || polyline.isBlank() || !legsJson.isArray() || legsJson.size() != normalizedPath.size() - 1) {
                log.warn("Google Directions retornou rota incompleta para {} pontos", normalizedPath.size());
                return Optional.empty();
            }

            List<ComputedLeg> legs = new ArrayList<>();
            for (int index = 0; index < legsJson.size(); index++) {
                JsonNode leg = legsJson.get(index);
                JsonNode distance = leg.path("distance");
                JsonNode duration = leg.path("duration");
                if (!distance.isObject() || !duration.isObject()) {
                    return Optional.empty();
                }

                legs.add(new ComputedLeg(
                        index + 1,
                        normalizedPath.get(index).name(),
                        normalizedPath.get(index + 1).name(),
                        distance.path("value").asInt(),
                        duration.path("value").asInt()
                ));
            }

            return Optional.of(new ComputedRoute(PROVIDER_GOOGLE_DIRECTIONS, polyline, legs));
        } catch (Exception error) {
            log.warn("Falha ao calcular rota real via Google Directions: {}", error.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ComputedRoute> computeLegByLegRoute(List<RoutingPoint> normalizedPath) {
        if (normalizedPath.size() < 3) {
            return Optional.empty();
        }

        List<String> polylines = new ArrayList<>();
        List<ComputedLeg> mergedLegs = new ArrayList<>();
        int providerSegments = 0;

        for (int index = 0; index < normalizedPath.size() - 1; index++) {
            RoutingPoint from = normalizedPath.get(index);
            RoutingPoint to = normalizedPath.get(index + 1);

            Optional<ComputedRoute> segmentRoute = computeSingleRequest(List.of(from, to));
            if (segmentRoute.isPresent()) {
                providerSegments++;
                polylines.add(segmentRoute.get().polyline());
                ComputedLeg providerLeg = segmentRoute.get().legs().get(0);
                mergedLegs.add(new ComputedLeg(
                        index + 1,
                        providerLeg.fromName(),
                        providerLeg.toName(),
                        providerLeg.distanceMeters(),
                        providerLeg.durationSeconds()
                ));
                continue;
            }

            ComputedLeg staticLeg = buildStaticLeg(from, to, index + 1);
            polylines.add(GooglePolylineCodec.encode(List.of(
                    new GooglePolylineCodec.Point(from.latitude(), from.longitude()),
                    new GooglePolylineCodec.Point(to.latitude(), to.longitude())
            )));
            mergedLegs.add(staticLeg);
        }

        if (providerSegments == 0) {
            return Optional.empty();
        }

        String mergedPolyline = GooglePolylineCodec.merge(polylines);
        if (mergedPolyline == null || mergedPolyline.isBlank()) {
            return Optional.empty();
        }

        String provider = providerSegments == mergedLegs.size()
                ? PROVIDER_GOOGLE_DIRECTIONS
                : PROVIDER_GOOGLE_DIRECTIONS_HYBRID;
        log.info("Google Directions recuperado via estrategia por trechos para {} pontos (providerSegments={}, totalSegments={})",
                normalizedPath.size(), providerSegments, mergedLegs.size());
        return Optional.of(new ComputedRoute(provider, mergedPolyline, mergedLegs));
    }

    private boolean isEnabled() {
        return apiKey != null
                && !apiKey.isBlank()
                && "google-directions".equalsIgnoreCase(routingProvider);
    }

    private ComputedLeg buildStaticLeg(RoutingPoint from, RoutingPoint to, int ordem) {
        int distanceMeters = haversineMeters(from.latitude(), from.longitude(), to.latitude(), to.longitude());
        int durationSeconds = Math.max(60, (int) Math.round(distanceMeters / 6.94d));
        return new ComputedLeg(
                ordem,
                from.name(),
                to.name(),
                distanceMeters,
                durationSeconds
        );
    }

    private String buildUrl(List<RoutingPoint> orderedPath) {
        RoutingPoint origin = orderedPath.get(0);
        RoutingPoint destination = orderedPath.get(orderedPath.size() - 1);

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl("https://maps.googleapis.com/maps/api/directions/json")
                .queryParam("origin", formatCoordinate(origin.latitude(), origin.longitude()))
                .queryParam("destination", formatCoordinate(destination.latitude(), destination.longitude()))
                .queryParam("mode", "driving")
                .queryParam("key", apiKey);

        List<RoutingPoint> waypoints = orderedPath.size() <= 2
                ? List.of()
                : orderedPath.subList(1, orderedPath.size() - 1);
        if (!waypoints.isEmpty()) {
            builder.queryParam(
                    "waypoints",
                    waypoints.stream()
                            .map(point -> formatCoordinate(point.latitude(), point.longitude()))
                            .reduce((left, right) -> left + "|" + right)
                            .orElse("")
            );
        }

        return builder.toUriString();
    }

    private String formatCoordinate(Double latitude, Double longitude) {
        return String.format(Locale.US, "%.6f,%.6f", latitude, longitude);
    }

    private List<RoutingPoint> collapseDuplicatedCoordinates(List<RoutingPoint> orderedPath) {
        Map<String, RoutingPoint> uniqueByCoordinate = new LinkedHashMap<>();
        for (RoutingPoint point : orderedPath) {
            uniqueByCoordinate.putIfAbsent(
                    formatCoordinate(point.latitude(), point.longitude()),
                    point
            );
        }
        return new ArrayList<>(uniqueByCoordinate.values());
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

    record RoutingPoint(String name, Double latitude, Double longitude) {
    }

    record ComputedLeg(int ordem, String fromName, String toName, int distanceMeters, int durationSeconds) {
    }

    record ComputedRoute(String provider, String polyline, List<ComputedLeg> legs) {
    }
}
