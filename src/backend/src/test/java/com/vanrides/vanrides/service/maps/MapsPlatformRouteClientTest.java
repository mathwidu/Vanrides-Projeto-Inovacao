package com.vanrides.vanrides.service.maps;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MapsPlatformRouteClientTest {

    @Test
    void shouldNotCallMapsPlatformWhenClientIsDisabled() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MapsPlatformRouteClient client = new MapsPlatformRouteClient(
                false,
                "http://127.0.0.1:18080/",
                restTemplate
        );

        assertThatThrownBy(() -> client.planRoute(sampleRequest()))
                .isInstanceOf(MapsPlatformClientUnavailableException.class)
                .hasMessageContaining("disabled");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void shouldPostRoutePlanToConfiguredMapsPlatform() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MapsPlatformRouteClient client = new MapsPlatformRouteClient(
                true,
                "http://127.0.0.1:18080/",
                restTemplate
        );
        MapsRoutePlanRequest request = sampleRequest();
        MapsRoutePlanResponse response = sampleResponse();

        when(restTemplate.postForObject(
                eq("http://127.0.0.1:18080/maps/routes/plan"),
                eq(request),
                eq(MapsRoutePlanResponse.class)
        )).thenReturn(response);

        MapsRoutePlanResponse actual = client.planRoute(request);

        assertThat(actual.routeId()).isEqualTo("fixture-br-rs-porto-alegre-001");
        assertThat(actual.provider()).isEqualTo("fixture");
        assertThat(actual.coverageStatus()).isEqualTo("fallback");
        assertThat(actual.offlinePackStatus()).isEqualTo("missing");
        assertThat(actual.instructions()).hasSize(2);
        assertThat(actual.instructions().get(0).voiceText()).isEqualTo("Siga ate a proxima parada de teste.");
        verify(restTemplate).postForObject(
                "http://127.0.0.1:18080/maps/routes/plan",
                request,
                MapsRoutePlanResponse.class
        );
    }

    @Test
    void shouldNormalizeBaseUrlWithoutTrailingSlash() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MapsPlatformRouteClient client = new MapsPlatformRouteClient(
                true,
                "http://127.0.0.1:18080",
                restTemplate
        );
        MapsRoutePlanRequest request = sampleRequest();

        when(restTemplate.postForObject(
                eq("http://127.0.0.1:18080/maps/routes/plan"),
                eq(request),
                eq(MapsRoutePlanResponse.class)
        )).thenReturn(sampleResponse());

        client.planRoute(request);

        verify(restTemplate).postForObject(
                "http://127.0.0.1:18080/maps/routes/plan",
                request,
                MapsRoutePlanResponse.class
        );
    }

    @Test
    void shouldFailWhenMapsPlatformReturnsEmptyBody() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        MapsPlatformRouteClient client = new MapsPlatformRouteClient(
                true,
                "http://127.0.0.1:18080",
                restTemplate
        );
        MapsRoutePlanRequest request = sampleRequest();

        when(restTemplate.postForObject(
                eq("http://127.0.0.1:18080/maps/routes/plan"),
                eq(request),
                eq(MapsRoutePlanResponse.class)
        )).thenReturn(null);

        assertThatThrownBy(() -> client.planRoute(request))
                .isInstanceOf(MapsPlatformClientUnavailableException.class)
                .hasMessageContaining("empty response");
    }

    private MapsRoutePlanRequest sampleRequest() {
        return new MapsRoutePlanRequest(
                "BR-RS",
                "van_school_safe",
                List.of(
                        new MapsRouteStop("origin", "Origem publica de teste", -30.0346, -51.2177),
                        new MapsRouteStop("stop-1", "Parada publica de teste", -30.0277, -51.2287),
                        new MapsRouteStop("destination", "Destino publico de teste", -30.0318, -51.2344)
                ),
                new MapsRouteOptions(false, true, true)
        );
    }

    private MapsRoutePlanResponse sampleResponse() {
        return new MapsRoutePlanResponse(
                "fixture-br-rs-porto-alegre-001",
                "fixture",
                "BR-RS",
                "van_school_safe",
                "fallback",
                "missing",
                0.42,
                "fixture_route_not_validated_for_navigation",
                780,
                4200,
                List.of(
                        List.of(-30.0346, -51.2177),
                        List.of(-30.0277, -51.2287),
                        List.of(-30.0318, -51.2344)
                ),
                List.of(
                        new MapsRouteLeg("origin", "stop-1", 2100, 390),
                        new MapsRouteLeg("stop-1", "destination", 2100, 390)
                ),
                List.of(
                        new MapsNavigationInstruction(
                                0,
                                "Siga ate a proxima parada de teste.",
                                "Siga ate a proxima parada de teste.",
                                2100,
                                "depart"
                        ),
                        new MapsNavigationInstruction(
                                1,
                                "Continue ate o destino publico de teste.",
                                "Continue ate o destino publico de teste.",
                                2100,
                                "arrive"
                        )
                )
        );
    }
}
