package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.DriverLocationWritePolicy;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.ViagemTrackingStateResolver;
import com.vanrides.vanrides.service.ViagemRealtimePublisherService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocalizacaoMotoristaController.class)
@Import({ViagemTrackingStateResolver.class, DriverLocationWritePolicy.class})
class LocalizacaoMotoristaControllerWebMvcTest extends PassengerLocationReadWebMvcContract {

    // Necessário porque o FilterAuthBasic é carregado no contexto WebMvc.
    @MockBean
    private PassageiroRepository passageiroRepository;

    @MockBean
    private MotoristaRepository motoristaRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private GuardianAccessService guardianAccessService;

    @MockBean
    private ViagemRealtimePublisherService viagemRealtimePublisherService;

    @Test
    void shouldRejectLocationEndpointWithoutAuthorizationHeader() throws Exception {
        when(authorizationService.authenticate(null))
                .thenThrow(new IllegalArgumentException("Cabeçalho Authorization ausente"));

        mockMvc.perform(get("/api/localizacao/1").servletPath("/api/localizacao/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Cabeçalho Authorization ausente"));
    }

    @Test
    void shouldReturnNoContentWhenNoLocationExistsInsideCompanyScope() throws Exception {
        Motorista motorista = authenticatedMotorista(1L, 66L, "driver.localizacao@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(basicHeader("driver.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(1L, "MOTORISTA", "driver.localizacao@vanrides.dev", null, null, 66L));
        when(motoristaService.buscarMotoristaPorId(1L, 66L)).thenReturn(Optional.of(motorista));
        when(localizacaoService.buscarUltimaPorMotorista(1L, 66L)).thenReturn(null);

        mockMvc.perform(get("/api/localizacao/1")
                        .servletPath("/api/localizacao/1")
                        .header("Authorization", basicHeader("driver.localizacao@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verifyNoInteractions(passengerLocationReadPolicy);
    }

    @Test
    void shouldReturnOkWithLatitudeAndLongitudeWhenLocationExistsInsideCompanyScope() throws Exception {
        Motorista motorista = authenticatedMotorista(2L, 66L, "driver.localizacao@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(basicHeader("driver.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(2L, "MOTORISTA", "driver.localizacao@vanrides.dev", null, null, 66L));
        when(motoristaService.buscarMotoristaPorId(2L, 66L)).thenReturn(Optional.of(motorista));

        LocalizacaoMotorista ultima = new LocalizacaoMotorista();
        ultima.setLatitude(-30.0346);
        ultima.setLongitude(-51.2177);
        ultima.setDataHora(LocalDateTime.now().minusSeconds(10));
        when(localizacaoService.buscarUltimaPorMotorista(2L, 66L)).thenReturn(ultima);

        mockMvc.perform(get("/api/localizacao/2")
                        .servletPath("/api/localizacao/2")
                        .header("Authorization", basicHeader("driver.localizacao@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(-30.0346))
                .andExpect(jsonPath("$.longitude").value(-51.2177))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.freshness").value("LIVE"));

        verifyNoInteractions(passengerLocationReadPolicy);
    }

    @Test
    void shouldReturnNotFoundWhenMotoristaBelongsToAnotherCompany() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(3L, "MOTORISTA", "driver.localizacao@vanrides.dev", null, null, 66L));
        when(motoristaService.buscarMotoristaPorId(999L, 66L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/localizacao/999")
                        .servletPath("/api/localizacao/999")
                        .header("Authorization", basicHeader("driver.localizacao@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowGuardianToReadLinkedMotoristaLocation() throws Exception {
        Motorista motorista = authenticatedMotorista(5L, 66L, "driver.guardian.localizacao@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(basicHeader("guardian.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.localizacao@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedMotorista(35L, 5L, 66L)).thenReturn(true);
        when(motoristaService.buscarMotoristaPorId(5L, 66L)).thenReturn(Optional.of(motorista));

        LocalizacaoMotorista ultima = new LocalizacaoMotorista();
        ultima.setLatitude(-30.0346);
        ultima.setLongitude(-51.2177);
        ultima.setDataHora(LocalDateTime.now().minusSeconds(60));
        when(localizacaoService.buscarUltimaPorMotorista(5L, 66L)).thenReturn(ultima);

        mockMvc.perform(get("/api/localizacao/5")
                        .servletPath("/api/localizacao/5")
                        .header("Authorization", basicHeader("guardian.localizacao@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(-30.0346))
                .andExpect(jsonPath("$.longitude").value(-51.2177))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.freshness").value("DELAYED"));

        verifyNoInteractions(passengerLocationReadPolicy);
    }

    @Test
    void shouldHideUnlinkedMotoristaLocationFromGuardian() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.localizacao.outro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.localizacao.outro@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedMotorista(35L, 999L, 66L)).thenReturn(false);

        mockMvc.perform(get("/api/localizacao/999")
                        .servletPath("/api/localizacao/999")
                .header("Authorization", basicHeader("guardian.localizacao.outro@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Motorista não encontrado na empresa atual."));

        verifyNoInteractions(passengerLocationReadPolicy);
    }

    @Test
    void shouldAllowGuardianToReadLinkedMotoristaHistory() throws Exception {
        Motorista motorista = authenticatedMotorista(6L, 66L, "driver.guardian.historico@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(basicHeader("guardian.historico@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.historico@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedMotorista(35L, 6L, 66L)).thenReturn(true);
        when(motoristaService.buscarMotoristaPorId(6L, 66L)).thenReturn(Optional.of(motorista));
        when(localizacaoService.buscarHistoricoPorMotorista(6L, 66L)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/localizacao/6/historico")
                        .servletPath("/api/localizacao/6/historico")
                        .header("Authorization", basicHeader("guardian.historico@vanrides.dev", "Secret123")))
                .andExpect(status().isOk());

        verifyNoInteractions(passengerLocationReadPolicy);
    }

    @Test
    void shouldAllowGuardianToReadOperationalTripMotoristaHistory() throws Exception {
        Motorista motorista = authenticatedMotorista(8L, 66L, "driver.guardian.operacional@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(basicHeader("guardian.operacional@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.operacional@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedMotorista(35L, 8L, 66L)).thenReturn(true);
        when(motoristaService.buscarMotoristaPorId(8L, 66L)).thenReturn(Optional.of(motorista));
        when(localizacaoService.buscarHistoricoPorMotorista(8L, 66L)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/localizacao/8/historico")
                        .servletPath("/api/localizacao/8/historico")
                        .header("Authorization", basicHeader("guardian.operacional@vanrides.dev", "Secret123")))
                .andExpect(status().isOk());

        verifyNoInteractions(passengerLocationReadPolicy);
    }

    @Test
    void shouldPublishTripRealtimeWhenDriverPostsLocation() throws Exception {
        Motorista motorista = authenticatedMotorista(4L, 66L, "driver.post.localizacao@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(basicHeader("driver.post.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(4L, "DRIVER", "driver.post.localizacao@vanrides.dev", null, null, 66L));
        when(motoristaService.buscarMotoristaPorId(4L, 66L)).thenReturn(Optional.of(motorista));
        when(localizacaoService.salvar(org.mockito.ArgumentMatchers.any(LocalizacaoMotorista.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/localizacao/4")
                        .servletPath("/api/localizacao/4")
                        .header("Authorization", basicHeader("driver.post.localizacao@vanrides.dev", "Secret123"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":-30.0,"longitude":-51.0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(-30.0))
                .andExpect(jsonPath("$.longitude").value(-51.0));

        org.mockito.InOrder persistenceThenPublish = inOrder(localizacaoService, viagemRealtimePublisherService);
        persistenceThenPublish.verify(localizacaoService).salvar(any(LocalizacaoMotorista.class));
        persistenceThenPublish.verify(viagemRealtimePublisherService).publishTodayTripSnapshotForMotorista(4L, 66L);
    }

    @Test
    void shouldHideSameCompanyDriverTargetBeforeAnyLocationEffect() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver.post.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(4L, "DRIVER", "driver.post.localizacao@vanrides.dev", null, null, 66L));
        when(motoristaService.buscarMotoristaPorId(5L, 66L))
                .thenReturn(Optional.of(authenticatedMotorista(
                        5L,
                        66L,
                        "other.driver.post.localizacao@vanrides.dev",
                        "Secret123"
                )));

        mockMvc.perform(post("/api/localizacao/5")
                        .servletPath("/api/localizacao/5")
                        .header("Authorization", basicHeader("driver.post.localizacao@vanrides.dev", "Secret123"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":-30.0,"longitude":-51.0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Motorista não encontrado na empresa atual."));

        verifyNoInteractions(motoristaService, localizacaoService, viagemRealtimePublisherService);
    }

    @Test
    void shouldHideCrossTenantOrUnknownDriverTargetBeforeAnyLocationEffect() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver.post.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(4L, "DRIVER", "driver.post.localizacao@vanrides.dev", null, null, 66L));

        mockMvc.perform(post("/api/localizacao/999")
                        .servletPath("/api/localizacao/999")
                        .header("Authorization", basicHeader("driver.post.localizacao@vanrides.dev", "Secret123"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":-30.0,"longitude":-51.0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Motorista não encontrado na empresa atual."));

        verifyNoInteractions(motoristaService, localizacaoService, viagemRealtimePublisherService);
    }

    @Test
    void shouldIgnorePersistenceControlledFieldsWhenDriverPostsOwnLocation() throws Exception {
        Motorista motorista = authenticatedMotorista(4L, 66L, "driver.post.localizacao@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(basicHeader("driver.post.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(4L, "DRIVER", "driver.post.localizacao@vanrides.dev", null, null, 66L));
        when(motoristaService.buscarMotoristaPorId(4L, 66L)).thenReturn(Optional.of(motorista));
        when(localizacaoService.salvar(any(LocalizacaoMotorista.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime requestStartedAt = LocalDateTime.now();

        mockMvc.perform(post("/api/localizacao/4")
                        .servletPath("/api/localizacao/4")
                        .header("Authorization", basicHeader("driver.post.localizacao@vanrides.dev", "Secret123"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 987,
                                  "latitude": -30.123,
                                  "longitude": -51.456,
                                  "dataHora": "2000-01-01T00:00:00",
                                  "motorista": {"id": 999}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(-30.123))
                .andExpect(jsonPath("$.longitude").value(-51.456));
        LocalDateTime requestCompletedAt = LocalDateTime.now();

        org.mockito.ArgumentCaptor<LocalizacaoMotorista> locationCaptor =
                org.mockito.ArgumentCaptor.forClass(LocalizacaoMotorista.class);
        verify(localizacaoService).salvar(locationCaptor.capture());
        LocalizacaoMotorista savedLocation = locationCaptor.getValue();
        assertThat(savedLocation.getId()).isNull();
        assertThat(savedLocation.getLatitude()).isEqualTo(-30.123);
        assertThat(savedLocation.getLongitude()).isEqualTo(-51.456);
        assertThat(savedLocation.getDataHora()).isAfterOrEqualTo(requestStartedAt);
        assertThat(savedLocation.getDataHora()).isBeforeOrEqualTo(requestCompletedAt);
        assertThat(savedLocation.getMotorista()).isSameAs(motorista);
        verify(viagemRealtimePublisherService).publishTodayTripSnapshotForMotorista(4L, 66L);
        verify(motoristaService, never()).buscarMotoristaPorId(999L, 66L);
    }

    @Test
    void shouldReturnNotFoundWithoutEffectsWhenOwnDriverRecordDoesNotExistInTenant() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver.missing.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(4L, "DRIVER", "driver.missing.localizacao@vanrides.dev", null, null, 66L));
        when(motoristaService.buscarMotoristaPorId(4L, 66L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/localizacao/4")
                        .servletPath("/api/localizacao/4")
                        .header("Authorization", basicHeader("driver.missing.localizacao@vanrides.dev", "Secret123"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":-30.0,"longitude":-51.0}
                                """))
                .andExpect(status().isNotFound());

        verify(motoristaService).buscarMotoristaPorId(4L, 66L);
        verifyNoInteractions(localizacaoService, viagemRealtimePublisherService);
    }

}
