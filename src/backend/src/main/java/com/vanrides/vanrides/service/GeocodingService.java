package com.vanrides.vanrides.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GeocodingService {

    private final String apiKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeocodingService(@Value("${google.api.key}") String apiKey) {
        this(apiKey, new RestTemplate(), new ObjectMapper());
    }

    GeocodingService(String apiKey, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public Coordenadas buscarCoordenadas(String enderecoCompleto) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/geocode/json")
                    .queryParam("address", enderecoCompleto)
                    .queryParam("key", apiKey)
                    .toUriString();

            String resposta = restTemplate.getForObject(url, String.class);

            JsonNode json = objectMapper.readTree(resposta);
            String status = json.path("status").asText("");

            if (!status.equals("OK")) {
                throw new RuntimeException("Erro na resposta da API: " + status);
            }

            JsonNode results = json.path("results");
            if (!results.isArray() || results.isEmpty()) {
                throw new RuntimeException("Resposta da API sem resultados.");
            }
            JsonNode location = results.get(0).path("geometry").path("location");
            if (!location.hasNonNull("lat") || !location.hasNonNull("lng")) {
                throw new RuntimeException("Resposta da API sem coordenadas.");
            }

            double latitude = location.path("lat").asDouble();
            double longitude = location.path("lng").asDouble();

            return new Coordenadas(latitude, longitude);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar coordenadas: " + e.getMessage(), e);
        }
    }

    public static class Coordenadas {
        private double latitude;
        private double longitude;

        public Coordenadas(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }
}
