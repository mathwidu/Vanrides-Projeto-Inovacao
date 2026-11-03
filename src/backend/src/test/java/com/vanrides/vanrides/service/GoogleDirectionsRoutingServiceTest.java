package com.vanrides.vanrides.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleDirectionsRoutingServiceTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(GoogleDirectionsRoutingService.class)
            .withPropertyValues(
                    "google.api.key=test-key",
                    "vanrides.routing.provider=google-directions"
            );

    @Test
    void shouldComputeStreetRouteWhenProviderIsEnabled() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GoogleDirectionsRoutingService service = new GoogleDirectionsRoutingService(
                "test-key",
                "google-directions",
                restTemplate
        );

        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn("""
                        {
                          "status":"OK",
                          "routes":[
                            {
                              "overview_polyline":{"points":"abc123"},
                              "legs":[
                                {
                                  "distance":{"value":3200},
                                  "duration":{"value":540}
                                },
                                {
                                  "distance":{"value":1800},
                                  "duration":{"value":360}
                                }
                              ]
                            }
                          ]
                        }
                        """);

        Optional<GoogleDirectionsRoutingService.ComputedRoute> route = service.compute(List.of(
                new GoogleDirectionsRoutingService.RoutingPoint("Motorista", -29.640000, -50.850000),
                new GoogleDirectionsRoutingService.RoutingPoint("Parada 1", -29.630000, -50.860000),
                new GoogleDirectionsRoutingService.RoutingPoint("Parada 2", -29.620000, -50.870000)
        ));

        assertThat(route).isPresent();
        assertThat(route.get().provider()).isEqualTo(GoogleDirectionsRoutingService.PROVIDER_GOOGLE_DIRECTIONS);
        assertThat(route.get().polyline()).isEqualTo("abc123");
        assertThat(route.get().legs()).hasSize(2);
        assertThat(route.get().legs().get(0).distanceMeters()).isEqualTo(3200);
        assertThat(route.get().legs().get(1).durationSeconds()).isEqualTo(360);
    }

    @Test
    void shouldReturnEmptyWhenProviderIsDisabled() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GoogleDirectionsRoutingService service = new GoogleDirectionsRoutingService(
                "",
                "static",
                restTemplate
        );

        Optional<GoogleDirectionsRoutingService.ComputedRoute> route = service.compute(List.of(
                new GoogleDirectionsRoutingService.RoutingPoint("Motorista", -29.640000, -50.850000),
                new GoogleDirectionsRoutingService.RoutingPoint("Parada 1", -29.630000, -50.860000)
        ));

        assertThat(route).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenDirectionsStatusIsNotOk() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GoogleDirectionsRoutingService service = new GoogleDirectionsRoutingService(
                "test-key",
                "google-directions",
                restTemplate
        );

        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn("""
                        {
                          "status":"ZERO_RESULTS",
                          "routes":[]
                        }
                        """);

        Optional<GoogleDirectionsRoutingService.ComputedRoute> route = service.compute(List.of(
                new GoogleDirectionsRoutingService.RoutingPoint("Motorista", -29.640000, -50.850000),
                new GoogleDirectionsRoutingService.RoutingPoint("Parada 1", -29.630000, -50.860000)
        ));

        assertThat(route).isEmpty();
    }

    @Test
    void shouldRecoverUsingHybridLegByLegRouteWhenSingleRequestFails() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        GoogleDirectionsRoutingService service = new GoogleDirectionsRoutingService(
                "test-key",
                "google-directions",
                restTemplate
        );

        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(
                        """
                        {
                          "status":"ZERO_RESULTS",
                          "routes":[]
                        }
                        """,
                        """
                        {
                          "status":"OK",
                          "routes":[
                            {
                              "overview_polyline":{"points":"_gayD~xpqHm}@_|B"},
                              "legs":[
                                {
                                  "distance":{"value":537},
                                  "duration":{"value":77}
                                }
                              ]
                            }
                          ]
                        }
                        """,
                        """
                        {
                          "status":"ZERO_RESULTS",
                          "routes":[]
                        }
                        """,
                        """
                        {
                          "status":"ZERO_RESULTS",
                          "routes":[]
                        }
                        """
                );

        Optional<GoogleDirectionsRoutingService.ComputedRoute> route = service.compute(List.of(
                new GoogleDirectionsRoutingService.RoutingPoint("Motorista", -29.572800, -50.791500),
                new GoogleDirectionsRoutingService.RoutingPoint("Parada 1", -29.575500, -50.786900),
                new GoogleDirectionsRoutingService.RoutingPoint("Parada 2", -29.568900, -50.781800),
                new GoogleDirectionsRoutingService.RoutingPoint("Parada 3", -29.579400, -50.799800)
        ));

        assertThat(route).isPresent();
        assertThat(route.get().provider()).isEqualTo(GoogleDirectionsRoutingService.PROVIDER_GOOGLE_DIRECTIONS_HYBRID);
        assertThat(route.get().polyline()).isNotBlank();
        assertThat(route.get().legs()).hasSize(3);
        assertThat(route.get().legs().get(0).fromName()).isEqualTo("Motorista");
        assertThat(route.get().legs().get(0).toName()).isEqualTo("Parada 1");
        assertThat(route.get().legs().get(1).fromName()).isEqualTo("Parada 1");
        assertThat(route.get().legs().get(1).toName()).isEqualTo("Parada 2");
        assertThat(route.get().legs().get(1).ordem()).isEqualTo(2);
        assertThat(route.get().legs().get(2).ordem()).isEqualTo(3);
    }

    @Test
    void shouldInstantiateServiceBeanInsideSpringContext() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GoogleDirectionsRoutingService.class);
        });
    }
}
