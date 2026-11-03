package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthService;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.DriverOnboardingService;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.MotoristaService;
import com.vanrides.vanrides.service.PassengerOnboardingService;
import com.vanrides.vanrides.service.PassageiroService;
import com.vanrides.vanrides.service.PresencaService;
import com.vanrides.vanrides.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        LoginController.class,
        MotoristaController.class,
        PassageiroController.class,
        PresencaController.class
})
class SecurityFilterWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private MotoristaService motoristaService;

    @MockitoBean
    private PassageiroService passageiroService;

    @MockitoBean
    private PassengerOnboardingService passengerOnboardingService;

    @MockitoBean
    private DriverOnboardingService driverOnboardingService;

    @MockitoBean
    private PresencaService presencaService;

    @MockitoBean
    private GuardianAccessService guardianAccessService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private PassageiroRepository passageiroRepository;

    @MockitoBean
    private MotoristaRepository motoristaRepository;

    @Test
    void shouldAllowCadastroMotoristaWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/motoristas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Sem Senha","email":"sem.senha@vanrides.dev","telefone":"51999990021"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Senha é obrigatória."));
    }

    @Test
    void shouldAllowCadastroPassageiroWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/passageiros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Sem Senha",
                                  "email":"passageiro.sem.senha@vanrides.dev",
                                  "telefone":"51999990022",
                                  "endereco":"Rua A",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-000"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Senha é obrigatória."));
    }

    @Test
    void shouldAllowLoginWithoutAuthorizationHeader() throws Exception {
        when(authService.authenticate("user@vanrides.dev", "wrong")).thenReturn(null);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@vanrides.dev","senha":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Credenciais inválidas."));
    }

    @Test
    void shouldAllowPublicListMotoristasWithoutAuthorizationHeader() throws Exception {
        when(motoristaService.listarTodosMotoristas()).thenReturn(List.of());

        mockMvc.perform(get("/motoristas"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectProtectedRouteWithoutAuthorizationHeader() throws Exception {
        when(authorizationService.authenticate(null))
                .thenThrow(new IllegalArgumentException("Cabeçalho Authorization ausente"));

        mockMvc.perform(get("/presencas").servletPath("/presencas"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Cabeçalho Authorization ausente"));
    }

    @Test
    void shouldAllowProtectedRouteWithValidBasicAuthorization() throws Exception {
        LocalDate hoje = LocalDate.now();
        when(authorizationService.authenticate(basicHeader("driver.security@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(88L, "MOTORISTA", "driver.security@vanrides.dev", null, null, 66L));
        when(presencaService.listarPresencasPorMotoristaEData(88L, hoje, 66L)).thenReturn(List.of());

        mockMvc.perform(get("/presencas/motorista/88")
                        .queryParam("data", hoje.toString())
                        .servletPath("/presencas/motorista/88")
                        .header("Authorization", basicHeader("driver.security@vanrides.dev", "Secret123")))
                .andExpect(status().isOk());

        verify(presencaService).listarPresencasPorMotoristaEData(eq(88L), eq(hoje), eq(66L));
    }

    @Test
    void shouldReturnForbiddenJsonWhenBasicAuthEmailIsNotVerified() throws Exception {
        doThrow(new com.vanrides.vanrides.service.EmailVerificationException(
                "EMAIL_NOT_VERIFIED",
                "O e-mail informado ainda não foi verificado.",
                "driver.security@vanrides.dev",
                403
        )).when(authorizationService).authenticate(basicHeader("driver.security@vanrides.dev", "Secret123"));

                mockMvc.perform(get("/presencas/motorista/88")
                        .queryParam("data", LocalDate.now().toString())
                        .servletPath("/presencas/motorista/88")
                        .header("Authorization", basicHeader("driver.security@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void shouldReturnNotFoundWhenPassageiroBelongsToAnotherCompany() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.scope@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.scope@vanrides.dev", null, null, 66L));
        when(passageiroService.buscarPassageiroPorId(999L, 66L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/passageiros/999")
                        .servletPath("/passageiros/999")
                        .header("Authorization", basicHeader("admin.scope@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound());

        verify(passageiroService).buscarPassageiroPorId(eq(999L), eq(66L));
    }

    @Test
    void shouldRejectPresencaCreationWhenPassageiroIsOutsideCompanyScope() throws Exception {
        LocalDate hoje = LocalDate.now();

        when(authorizationService.authenticate(basicHeader("admin.presenca@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(91L, "COMPANY_ADMIN", "admin.presenca@vanrides.dev", null, null, 66L));
        when(presencaService.jaExistePresenca(999L, hoje, 66L)).thenReturn(false);
        when(presencaService.salvarPresenca(any(), eq(66L)))
                .thenThrow(new IllegalArgumentException("Passageiro não encontrado na empresa atual."));

        mockMvc.perform(post("/presencas")
                        .servletPath("/presencas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basicHeader("admin.presenca@vanrides.dev", "Secret123"))
                        .content("""
                                {
                                  "dataPresenca":"%s",
                                  "confirmado":true,
                                  "passageiro":{"id":999}
                                }
                                """.formatted(hoje)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Passageiro não encontrado na empresa atual."));

        verify(presencaService).jaExistePresenca(eq(999L), eq(hoje), eq(66L));
        verify(presencaService).salvarPresenca(any(), eq(66L));
        verify(presencaService, never()).listarTodasPresencas(eq(66L));
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
