package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.service.AuthService;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.DriverLocationWritePolicy;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.LocalizacaoMotoristaService;
import com.vanrides.vanrides.service.MotoristaService;
import com.vanrides.vanrides.service.PassengerLocationReadPolicy;
import com.vanrides.vanrides.service.PassageiroService;
import com.vanrides.vanrides.service.PresencaService;
import com.vanrides.vanrides.service.RefreshTokenService;
import com.vanrides.vanrides.service.ViagemRealtimePublisherService;
import com.vanrides.vanrides.service.ViagemTrackingStateResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        MotoristaController.class,
        PassageiroController.class,
        PresencaController.class,
        LocalizacaoMotoristaController.class
})
@Import({ViagemTrackingStateResolver.class, DriverLocationWritePolicy.class})
class TenantIsolationWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private MotoristaService motoristaService;

    @MockBean
    private PassageiroService passageiroService;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @MockBean
    private GuardianAccessService guardianAccessService;

    @MockitoBean
    private PassengerLocationReadPolicy passengerLocationReadPolicy;

    @MockBean
    private PresencaService presencaService;

    @MockBean
    private LocalizacaoMotoristaService localizacaoMotoristaService;

    @MockBean
    private ViagemRealtimePublisherService viagemRealtimePublisherService;

    private String authorizationHeader;

    @BeforeEach
    void setUp() {
        authorizationHeader = basicHeader("tenant.a@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(authorizationHeader))
                .thenReturn(new AuthenticatedUser(10L, "MOTORISTA", "tenant.a@vanrides.dev", null, null, 66L));
    }

    @Test
    void shouldReturnNotFoundWhenCompanyATriesToReadMotoristaFromCompanyB() throws Exception {
        when(motoristaService.buscarMotoristaPorId(999L, 66L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/motoristas/999")
                        .servletPath("/motoristas/999")
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenCompanyATriesToReadPassageiroFromCompanyB() throws Exception {
        String adminAuthorizationHeader = basicHeader("admin.tenant.a@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(adminAuthorizationHeader))
                .thenReturn(new AuthenticatedUser(11L, "COMPANY_ADMIN", "admin.tenant.a@vanrides.dev", null, null, 66L));
        when(passageiroService.buscarPassageiroPorId(999L, 66L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/passageiros/999")
                        .servletPath("/passageiros/999")
                        .header("Authorization", adminAuthorizationHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenCompanyATriesToCreatePresencaForPassageiroFromCompanyB() throws Exception {
        LocalDate hoje = LocalDate.now();
        String adminAuthorizationHeader = basicHeader("admin.tenant.a@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(adminAuthorizationHeader))
                .thenReturn(new AuthenticatedUser(11L, "COMPANY_ADMIN", "admin.tenant.a@vanrides.dev", null, null, 66L));
        when(presencaService.jaExistePresenca(999L, hoje, 66L)).thenReturn(false);
        when(presencaService.salvarPresenca(any(), eq(66L)))
                .thenThrow(new IllegalArgumentException("Passageiro não encontrado na empresa atual."));

        mockMvc.perform(post("/presencas")
                        .servletPath("/presencas")
                        .header("Authorization", adminAuthorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dataPresenca":"%s",
                                  "confirmado":true,
                                  "passageiro":{"id":999}
                                }
                                """.formatted(hoje)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Passageiro não encontrado na empresa atual."));
    }

    @Test
    void shouldReturnNotFoundWhenCompanyATriesToReadLocationFromCompanyB() throws Exception {
        when(motoristaService.buscarMotoristaPorId(999L, 66L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/localizacao/999")
                        .servletPath("/api/localizacao/999")
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldHideCrossTenantLocationFromCompanyAdminUsingTenantScopedLookup() throws Exception {
        String adminAuthorizationHeader = basicHeader(
                "admin.location.tenant.a@vanrides.dev",
                "Secret123"
        );
        when(authorizationService.authenticate(adminAuthorizationHeader))
                .thenReturn(new AuthenticatedUser(
                        11L,
                        "COMPANY_ADMIN",
                        "admin.location.tenant.a@vanrides.dev",
                        null,
                        null,
                        66L
                ));
        when(motoristaService.buscarMotoristaPorId(999L, 66L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/localizacao/999")
                        .servletPath("/api/localizacao/999")
                        .header("Authorization", adminAuthorizationHeader))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        verify(motoristaService).buscarMotoristaPorId(999L, 66L);
        verifyNoInteractions(
                passengerLocationReadPolicy,
                guardianAccessService,
                localizacaoMotoristaService
        );
    }

    @Test
    void shouldReturnNotFoundWhenCompanyATriesToReadLocationHistoryFromCompanyB() throws Exception {
        when(motoristaService.buscarMotoristaPorId(999L, 66L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/localizacao/999/historico")
                        .servletPath("/api/localizacao/999/historico")
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldHideLocationWriteTargetFromAnotherCompanyBeforeLookupOrEffects() throws Exception {
        when(motoristaService.buscarMotoristaPorId(999L, 66L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/localizacao/999")
                        .servletPath("/api/localizacao/999")
                        .header("Authorization", authorizationHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":-30.0,"longitude":-51.0}
                                """))
                .andExpect(status().isNotFound());

        verify(motoristaService, never()).buscarMotoristaPorId(999L, 66L);
        verifyNoInteractions(localizacaoMotoristaService, viagemRealtimePublisherService);
    }

    @Test
    void shouldReturnNotFoundWhenCompanyATriesToDeletePresencaFromCompanyB() throws Exception {
        String adminAuthorizationHeader = basicHeader("admin.tenant.a@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(adminAuthorizationHeader))
                .thenReturn(new AuthenticatedUser(11L, "COMPANY_ADMIN", "admin.tenant.a@vanrides.dev", null, null, 66L));
        when(presencaService.buscarPresencaPorId(999L, 66L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/presencas/999")
                        .servletPath("/presencas/999")
                        .header("Authorization", adminAuthorizationHeader))
                .andExpect(status().isNotFound());
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
