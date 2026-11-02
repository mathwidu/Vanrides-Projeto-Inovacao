package com.vanrides.vanrides.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlacesProxyService {

    private final String apiKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public PlacesProxyService(@Value("${google.api.key:}") String apiKey) {
        this(apiKey, new RestTemplate(), new ObjectMapper());
    }

    PlacesProxyService(String apiKey, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<PlacePrediction> autocomplete(String input) {
        if (isBlank(apiKey) || isBlank(input)) {
            return List.of();
        }

        String url = UriComponentsBuilder
                .fromHttpUrl("https://maps.googleapis.com/maps/api/place/autocomplete/json")
                .queryParam("input", input)
                .queryParam("language", "pt")
                .queryParam("components", "country:br")
                .queryParam("key", apiKey)
                .toUriString();

        String raw = restTemplate.getForObject(url, String.class);
        JsonNode json = readJson(raw);
        String status = json.path("status").asText("");

        if (!"OK".equals(status) && !"ZERO_RESULTS".equals(status)) {
            return List.of();
        }

        JsonNode predictions = json.path("predictions");
        if (!predictions.isArray()) return List.of();

        List<PlacePrediction> result = new ArrayList<>();
        for (JsonNode p : predictions) {
            JsonNode structured = p.path("structured_formatting");
            String description = p.path("description").asText("");
            result.add(new PlacePrediction(
                    p.path("place_id").asText(""),
                    structured.isObject() ? structured.path("main_text").asText(description) : description,
                    structured.isObject() ? structured.path("secondary_text").asText("") : ""
            ));
        }
        return result;
    }

    public PlaceDetail details(String placeId) {
        if (isBlank(apiKey) || isBlank(placeId)) {
            return null;
        }

        String url = UriComponentsBuilder
                .fromHttpUrl("https://maps.googleapis.com/maps/api/place/details/json")
                .queryParam("place_id", placeId)
                .queryParam("fields", "name,formatted_address,address_components,geometry")
                .queryParam("language", "pt")
                .queryParam("key", apiKey)
                .toUriString();

        String raw = restTemplate.getForObject(url, String.class);
        JsonNode json = readJson(raw);

        if (!"OK".equals(json.path("status").asText(""))) return null;

        JsonNode result = json.path("result");
        JsonNode location = result.path("geometry").path("location");

        String nome = result.path("name").asText("");
        String formattedAddress = result.path("formatted_address").asText("");

        String endereco = resolveStreetAddress(result.path("address_components"), formattedAddress);

        return new PlaceDetail(nome, endereco, location.path("lat").asDouble(), location.path("lng").asDouble());
    }

    private String resolveStreetAddress(JsonNode components, String fallback) {
        if (!components.isArray()) return fallback;
        String rua = null;
        String numero = null;
        for (JsonNode component : components) {
            JsonNode types = component.path("types");
            if (!types.isArray()) continue;
            for (JsonNode type : types) {
                if ("route".equals(type.asText())) rua = component.path("long_name").asText("");
                if ("street_number".equals(type.asText())) numero = component.path("long_name").asText("");
            }
        }
        if (rua != null) {
            return numero != null ? rua + ", " + numero : rua;
        }
        return fallback;
    }

    private JsonNode readJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception error) {
            throw new IllegalArgumentException("Resposta JSON invalida.", error);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record PlacePrediction(String placeId, String mainText, String secondaryText) {}

    public record PlaceDetail(String nome, String endereco, double latitude, double longitude) {}
}
