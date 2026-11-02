package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.ViagemMotoristaResponse;
import com.vanrides.vanrides.dto.ViagemNavegacaoResponse;
import com.vanrides.vanrides.dto.ViagemParadaResponse;
import com.vanrides.vanrides.dto.ViagemPassageiroResumoResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.dto.ViagemRotaLegResponse;
import com.vanrides.vanrides.dto.ViagemRotaWaypointResponse;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.OperationalDateProvider;
import com.vanrides.vanrides.service.ViagemOperacionalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static com.vanrides.vanrides.testsupport.NavigationRoutePlanTestFixture.navigationRoutePlan;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ViagemController.class)
class ViagemMotoristaNavigationRoutePlanControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ViagemOperacionalService viagemOperacionalService;
    @MockBean
    private GuardianAccessService guardianAccessService;
    @MockBean
    private AuthorizationService authorizationService;
    @MockitoBean
    private OperationalDateProvider operationalDateProvider;

    @Test
    void shouldExposeDriverNavigationRoutePlanAsTopLevelPayloadField() throws Exception {
        String expectedContractFixture = Files.readString(Path.of(
                "src/test/resources/contracts/driver-route-plan-payload.validated-ready.json"
        ));
        when(authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "driver@vanrides.dev", "Motorista", null, 66L));
        when(viagemOperacionalService.buscarViagemDoMotorista(7L, LocalDate.of(2026, 3, 12), 66L))
                .thenReturn(Optional.of(driverTripResponse()));

        mockMvc.perform(get("/viagens/motorista/7/hoje")
                        .servletPath("/viagens/motorista/7/hoje")
                        .param("data", "2026-03-12")
                        .header("Authorization", basicHeader("driver@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedContractFixture, false))
                .andExpect(jsonPath("$.navigationRoutePlan.routeId").value("route-br-rs-001"))
                .andExpect(jsonPath("$.navigationRoutePlan.coverageStatus").value("validada"))
                .andExpect(jsonPath("$.navigationRoutePlan.offlinePackStatus").value("ready"))
                .andExpect(jsonPath("$.navigationRoutePlan.instructions[0].voiceText")
                        .value("Em 200 metros, siga pela Avenida Ipiranga."))
                .andExpect(jsonPath("$.rotaGeografica.navigationRoutePlan").doesNotExist());
    }

    private ViagemMotoristaResponse driverTripResponse() {
        ViagemParadaResponse parada = new ViagemParadaResponse(
                300L,
                1,
                ViagemParadaStatus.PENDING,
                12,
                true,
                true,
                new ViagemPassageiroResumoResponse(21L, "Aluno Um", true)
        );
        return new ViagemMotoristaResponse(
                30L,
                10L,
                15L,
                "Rota Escolar Manha",
                "Rota Escolar Manha",
                null,
                LocalDate.of(2026, 3, 12),
                null,
                ViagemStatus.IN_PROGRESS,
                2,
                12,
                LocalDate.of(2026, 3, 12).atStartOfDay(),
                null,
                parada,
                List.of(parada),
                rotaGeograficaResponse().withNavigationRoutePlan(navigationRoutePlan())
        );
    }

    private ViagemRotaGeograficaResponse rotaGeograficaResponse() {
        return new ViagemRotaGeograficaResponse(
                "STATIC_GEOMETRY_V1",
                "abcd",
                new ViagemNavegacaoResponse("GOOGLE_MAPS_HANDOFF", "https://maps.example", "Motorista", "Aluno Um", 0),
                List.of(new ViagemRotaWaypointResponse("STOP", 300L, 21L, "Aluno Um", -29.0, -51.0, 1, true, true)),
                List.of(new ViagemRotaLegResponse(1, "Motorista", "Aluno Um", 1200, 300)),
                1,
                1,
                0
        );
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
