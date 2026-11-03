package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthService;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.DriverLocationWritePolicy;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.LocalizacaoMotoristaService;
import com.vanrides.vanrides.service.MotoristaService;
import com.vanrides.vanrides.service.PassengerLocationReadPolicy;
import com.vanrides.vanrides.service.PassageiroService;
import com.vanrides.vanrides.service.RefreshTokenService;
import com.vanrides.vanrides.service.ViagemRealtimePublisherService;
import com.vanrides.vanrides.service.ViagemTrackingStateResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        MotoristaController.class,
        PassageiroController.class,
        LocalizacaoMotoristaController.class
})
@Import({ViagemTrackingStateResolver.class, DriverLocationWritePolicy.class})
class RbacWebMvcTest {

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
    private LocalizacaoMotoristaService localizacaoMotoristaService;

    @MockBean
    private PassageiroRepository passageiroRepository;

    @MockBean
    private MotoristaRepository motoristaRepository;

    @MockBean
    private ViagemRealtimePublisherService viagemRealtimePublisherService;

    @Test
    void shouldForbidDeletingMotoristaForDriverRole() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver.rbac@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(11L, "DRIVER", "driver.rbac@vanrides.dev", null, null, 66L));

        mockMvc.perform(delete("/motoristas/7")
                        .servletPath("/motoristas/7")
                        .header("Authorization", basicHeader("driver.rbac@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(motoristaService, never()).buscarMotoristaPorId(eq(7L), eq(66L));
    }

    @Test
    void shouldAllowDeletingMotoristaForCompanyAdminRole() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.rbac@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(12L, "COMPANY_ADMIN", "admin.rbac@vanrides.dev", null, null, 66L));
        when(motoristaService.buscarMotoristaPorId(7L, 66L)).thenReturn(Optional.of(new Motorista()));

        mockMvc.perform(delete("/motoristas/7")
                        .servletPath("/motoristas/7")
                        .header("Authorization", basicHeader("admin.rbac@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent());

        verify(motoristaService).buscarMotoristaPorId(eq(7L), eq(66L));
        verify(motoristaService).deletarMotorista(7L);
    }

    @Test
    void shouldDelegatePassengerUpdateToTransactionalBoundaryForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.update@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(
                        12L,
                        "COMPANY_ADMIN",
                        "admin.update@vanrides.dev",
                        null,
                        null,
                        66L
                ));
        Passageiro saved = new Passageiro();
        saved.setId(9L);
        saved.setEmail("novo@vanrides.dev");
        when(passageiroService.atualizarPassageiroProtegido(eq(9L), eq(66L), any(Passageiro.class)))
                .thenReturn(Optional.of(saved));

        mockMvc.perform(put("/passageiros/9")
                        .servletPath("/passageiros/9")
                        .header("Authorization", basicHeader("admin.update@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Novo Nome",
                                  "email":"novo@vanrides.dev",
                                  "telefone":"51999990001",
                                  "endereco":"Rua A",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-000"
                                }
                                """))
                .andExpect(status().isOk());

        verify(passageiroService).atualizarPassageiroProtegido(
                eq(9L),
                eq(66L),
                any(Passageiro.class)
        );
    }

    @Test
    void shouldForbidUpdatingPassageiroForPassengerRole() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro.rbac@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(13L, "PASSENGER", "passageiro.rbac@vanrides.dev", null, null, 66L));

        mockMvc.perform(put("/passageiros/9")
                        .servletPath("/passageiros/9")
                        .header("Authorization", basicHeader("passageiro.rbac@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Novo Nome",
                                  "email":"novo@vanrides.dev",
                                  "telefone":"51999990001",
                                  "endereco":"Rua A",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-000"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(passageiroService, never()).atualizarPassageiroProtegido(
                eq(9L),
                eq(66L),
                any(Passageiro.class)
        );
    }

    @Test
    void shouldRejectUpdatingPassageiroForGuardianRoleAsUnavailable() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.rbac@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(14L, "GUARDIAN", "guardian.rbac@vanrides.dev", null, null, 66L));

        mockMvc.perform(put("/passageiros/9")
                        .servletPath("/passageiros/9")
                        .header("Authorization", basicHeader("guardian.rbac@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Novo Nome",
                                  "email":"novo@vanrides.dev",
                                  "telefone":"51999990001",
                                  "endereco":"Rua A",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-000"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_UNAVAILABLE"));

        verify(guardianAccessService, never()).hasLinkedPassageiro(any(), any(), any());
        verify(passageiroService, never()).buscarPassageiroPorId(eq(9L), eq(66L));
        verify(passageiroService, never()).atualizarPassageiroProtegido(
                eq(9L),
                eq(66L),
                any(Passageiro.class)
        );
    }

    @Test
    void shouldForbidPostingLocationForPassengerRole() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(15L, "PASSENGER", "passageiro.localizacao@vanrides.dev", null, null, 66L));

        mockMvc.perform(post("/api/localizacao/4")
                        .servletPath("/api/localizacao/4")
                        .header("Authorization", basicHeader("passageiro.localizacao@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":-30.0,"longitude":-51.0}
                                """))
                .andExpect(status().isForbidden());

        verify(motoristaService, never()).buscarMotoristaPorId(eq(4L), eq(66L));
        verifyNoInteractions(passengerLocationReadPolicy, localizacaoMotoristaService);
    }

    @Test
    void shouldHideLocationWriteForDifferentDriverIdentity() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(16L, "DRIVER", "driver.localizacao@vanrides.dev", null, null, 66L));
        when(motoristaService.buscarMotoristaPorId(4L, 66L)).thenReturn(Optional.of(new Motorista()));
        when(localizacaoMotoristaService.salvar(any(LocalizacaoMotorista.class))).thenReturn(new LocalizacaoMotorista());

        mockMvc.perform(post("/api/localizacao/4")
                        .servletPath("/api/localizacao/4")
                        .header("Authorization", basicHeader("driver.localizacao@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":-30.0,"longitude":-51.0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Motorista não encontrado na empresa atual."));

        verify(motoristaService, never()).buscarMotoristaPorId(eq(4L), eq(66L));
        verify(localizacaoMotoristaService, never()).salvar(any(LocalizacaoMotorista.class));
        verify(viagemRealtimePublisherService, never()).publishTodayTripSnapshotForMotorista(any(), any());
    }

    @Test
    void shouldAllowPostingLocationForOwnDriverIdentity() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver.own.localizacao@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(16L, "DRIVER", "driver.own.localizacao@vanrides.dev", null, null, 66L));
        Motorista motorista = new Motorista();
        motorista.setId(16L);
        when(motoristaService.buscarMotoristaPorId(16L, 66L)).thenReturn(Optional.of(motorista));
        when(localizacaoMotoristaService.salvar(any(LocalizacaoMotorista.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/localizacao/16")
                        .servletPath("/api/localizacao/16")
                        .header("Authorization", basicHeader("driver.own.localizacao@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":-30.0,"longitude":-51.0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(-30.0))
                .andExpect(jsonPath("$.longitude").value(-51.0));

        verify(motoristaService).buscarMotoristaPorId(eq(16L), eq(66L));
        verify(localizacaoMotoristaService).salvar(any(LocalizacaoMotorista.class));
        verify(viagemRealtimePublisherService).publishTodayTripSnapshotForMotorista(16L, 66L);
    }

    @Test
    void shouldForbidLocationHistoryForPassengerRole() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro.historico@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(17L, "PASSENGER", "passageiro.historico@vanrides.dev", null, null, 66L));

        mockMvc.perform(get("/api/localizacao/4/historico")
                        .servletPath("/api/localizacao/4/historico")
                        .header("Authorization", basicHeader("passageiro.historico@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(motoristaService, never()).buscarMotoristaPorId(eq(4L), eq(66L));
        verifyNoInteractions(passengerLocationReadPolicy, localizacaoMotoristaService);
    }

    @Test
    void shouldAllowLocationHistoryForCompanyAdminRole() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.historico@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(18L, "COMPANY_ADMIN", "admin.historico@vanrides.dev", null, null, 66L));
        when(motoristaService.buscarMotoristaPorId(4L, 66L)).thenReturn(Optional.of(new Motorista()));
        when(localizacaoMotoristaService.buscarHistoricoPorMotorista(4L, 66L)).thenReturn(List.of());

        mockMvc.perform(get("/api/localizacao/4/historico")
                        .servletPath("/api/localizacao/4/historico")
                        .header("Authorization", basicHeader("admin.historico@vanrides.dev", "Secret123")))
                .andExpect(status().isOk());

        verify(motoristaService).buscarMotoristaPorId(eq(4L), eq(66L));
        verify(localizacaoMotoristaService).buscarHistoricoPorMotorista(eq(4L), eq(66L));
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
