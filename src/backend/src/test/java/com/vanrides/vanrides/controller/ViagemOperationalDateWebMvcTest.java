package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.OperationalDateProvider;
import com.vanrides.vanrides.service.ViagemOperacionalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ViagemController.class)
class ViagemOperationalDateWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ViagemOperacionalService viagemOperacionalService;

    @MockitoBean
    private GuardianAccessService guardianAccessService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @MockitoBean
    private OperationalDateProvider operationalDateProvider;

    @Test
    void shouldUseOperationalDateWhenRequestDateIsOmitted() throws Exception {
        LocalDate operationalDate = LocalDate.of(2026, 7, 16);
        String authorizationHeader = basicHeader("driver.operational-date@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(authorizationHeader))
                .thenReturn(new AuthenticatedUser(
                        7L,
                        "DRIVER",
                        "driver.operational-date@vanrides.dev",
                        "Motorista",
                        null,
                        66L
                ));
        when(operationalDateProvider.today()).thenReturn(operationalDate);
        when(viagemOperacionalService.buscarViagemDoMotorista(7L, operationalDate, 66L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/viagens/motorista/7/hoje")
                        .servletPath("/viagens/motorista/7/hoje")
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isNoContent());

        verify(operationalDateProvider).today();
        verify(viagemOperacionalService).buscarViagemDoMotorista(7L, operationalDate, 66L);
    }

    @Test
    void shouldPreserveExplicitDateWithoutReadingOperationalDefault() throws Exception {
        LocalDate explicitDate = LocalDate.of(2026, 3, 12);
        String authorizationHeader = basicHeader("driver.explicit-date@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(authorizationHeader))
                .thenReturn(new AuthenticatedUser(
                        8L,
                        "DRIVER",
                        "driver.explicit-date@vanrides.dev",
                        "Motorista",
                        null,
                        66L
                ));
        when(viagemOperacionalService.buscarViagemDoMotorista(8L, explicitDate, 66L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/viagens/motorista/8/hoje")
                        .servletPath("/viagens/motorista/8/hoje")
                        .param("data", explicitDate.toString())
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isNoContent());

        verifyNoInteractions(operationalDateProvider);
        verify(viagemOperacionalService).buscarViagemDoMotorista(8L, explicitDate, 66L);
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
