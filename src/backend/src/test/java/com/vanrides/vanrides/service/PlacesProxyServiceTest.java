package com.vanrides.vanrides.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlacesProxyServiceTest {

    @Test
    void shouldParseAutocompletePredictions() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        PlacesProxyService service = new PlacesProxyService("test-key", restTemplate, new ObjectMapper());
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("""
                {
                  "status": "OK",
                  "predictions": [
                    {
                      "place_id": "place-1",
                      "description": "Escola Central, Porto Alegre",
                      "structured_formatting": {
                        "main_text": "Escola Central",
                        "secondary_text": "Porto Alegre"
                      }
                    }
                  ]
                }
                """);

        List<PlacesProxyService.PlacePrediction> predictions = service.autocomplete("Escola");

        assertThat(predictions).hasSize(1);
        assertThat(predictions.get(0).placeId()).isEqualTo("place-1");
        assertThat(predictions.get(0).mainText()).isEqualTo("Escola Central");
        assertThat(predictions.get(0).secondaryText()).isEqualTo("Porto Alegre");
    }

    @Test
    void shouldReturnEmptyAutocompleteWhenGoogleHasZeroResults() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        PlacesProxyService service = new PlacesProxyService("test-key", restTemplate, new ObjectMapper());
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("""
                {
                  "status": "ZERO_RESULTS",
                  "predictions": []
                }
                """);

        assertThat(service.autocomplete("Sem resultado")).isEmpty();
    }

    @Test
    void shouldParsePlaceDetailsAndResolveStreetAddress() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        PlacesProxyService service = new PlacesProxyService("test-key", restTemplate, new ObjectMapper());
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("""
                {
                  "status": "OK",
                  "result": {
                    "name": "Escola Central",
                    "formatted_address": "Av. Brasil, 100",
                    "address_components": [
                      {"long_name": "Av. Brasil", "types": ["route"]},
                      {"long_name": "100", "types": ["street_number"]}
                    ],
                    "geometry": {
                      "location": {
                        "lat": -30.0346,
                        "lng": -51.2177
                      }
                    }
                  }
                }
                """);

        PlacesProxyService.PlaceDetail detail = service.details("place-1");

        assertThat(detail.nome()).isEqualTo("Escola Central");
        assertThat(detail.endereco()).isEqualTo("Av. Brasil, 100");
        assertThat(detail.latitude()).isEqualTo(-30.0346);
        assertThat(detail.longitude()).isEqualTo(-51.2177);
    }

    @Test
    void shouldReturnNullDetailsWhenGoogleStatusIsNotOk() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        PlacesProxyService service = new PlacesProxyService("test-key", restTemplate, new ObjectMapper());
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("""
                {
                  "status": "NOT_FOUND"
                }
                """);

        assertThat(service.details("missing")).isNull();
    }
}
