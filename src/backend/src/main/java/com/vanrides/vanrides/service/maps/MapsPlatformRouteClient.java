package com.vanrides.vanrides.service.maps;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class MapsPlatformRouteClient {

    private static final String ROUTE_PLAN_PATH = "/maps/routes/plan";

    private final boolean enabled;
    private final String baseUrl;
    private final RestTemplate restTemplate;

    @Autowired
    public MapsPlatformRouteClient(
            @Value("${vanrides.maps-platform.enabled:false}") boolean enabled,
            @Value("${vanrides.maps-platform.base-url:http://127.0.0.1:18080}") String baseUrl
    ) {
        this(enabled, baseUrl, new RestTemplate());
    }

    MapsPlatformRouteClient(boolean enabled, String baseUrl, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.restTemplate = restTemplate;
    }

    public boolean isEnabled() {
        return enabled && !baseUrl.isBlank();
    }

    public MapsRoutePlanResponse planRoute(MapsRoutePlanRequest request) {
        if (!isEnabled()) {
            throw new MapsPlatformClientUnavailableException("Maps Platform client is disabled");
        }

        try {
            MapsRoutePlanResponse response = restTemplate.postForObject(
                    routePlanUrl(),
                    request,
                    MapsRoutePlanResponse.class
            );
            if (response == null) {
                throw new MapsPlatformClientUnavailableException("Maps Platform returned empty response");
            }
            return response;
        } catch (MapsPlatformClientUnavailableException error) {
            throw error;
        } catch (RestClientException error) {
            throw new MapsPlatformClientUnavailableException(
                    "Maps Platform request failed: " + error.getMessage(),
                    error
            );
        }
    }

    String routePlanUrl() {
        String normalizedBaseUrl = baseUrl;
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl + ROUTE_PLAN_PATH;
    }
}
