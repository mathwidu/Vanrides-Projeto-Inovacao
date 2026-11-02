package com.vanrides.vanrides.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeocodingServiceTest {

    @Test
    void shouldParseCoordinatesWhenGoogleReturnsOk() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GeocodingService service = new GeocodingService("test-key", restTemplate, new ObjectMapper());
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("""
                {
                  "status": "OK",
                  "results": [
                    {
                      "geometry": {
                        "location": {
                          "lat": -30.0346,
                          "lng": -51.2177
                        }
                      }
                    }
                  ]
                }
                """);

        GeocodingService.Coordenadas coordenadas = service.buscarCoordenadas("Rua Teste, Porto Alegre");

        assertThat(coordenadas.getLatitude()).isEqualTo(-30.0346);
        assertThat(coordenadas.getLongitude()).isEqualTo(-51.2177);
    }

    @Test
    void shouldKeepGoogleStatusInErrorMessageWhenStatusIsNotOk() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GeocodingService service = new GeocodingService("test-key", restTemplate, new ObjectMapper());
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("""
                {
                  "status": "ZERO_RESULTS",
                  "results": []
                }
                """);

        assertThatThrownBy(() -> service.buscarCoordenadas("Endereco desconhecido"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro na resposta da API: ZERO_RESULTS");
    }
}
