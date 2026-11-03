package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AccountMeResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AccountAvatarService;
import com.vanrides.vanrides.service.AccountAvatarStorageService;
import com.vanrides.vanrides.service.AccountMeService;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.EmailVerificationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountMeService accountMeService;

    @MockitoBean
    private AccountAvatarStorageService avatarStorageService;

    @MockitoBean
    private AccountAvatarService accountAvatarService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @MockitoBean
    private PassageiroRepository passageiroRepository;

    @MockitoBean
    private MotoristaRepository motoristaRepository;

    @MockitoBean
    private GuardianRepository guardianRepository;

    @MockitoBean
    private CompanyAdminRepository companyAdminRepository;

    @Test
    void shouldReturnCurrentPassengerAccountWithoutSensitiveFields() throws Exception {
        String authorization = basicHeader("ana@vanrides.dev", "Secret!123");
        when(authorizationService.authenticate(authorization))
                .thenReturn(new AuthenticatedUser(21L, UserRole.PASSENGER, "ana@vanrides.dev", "Ana", 44L, 66L));
        AccountMeResponse response = new AccountMeResponse(
                21L,
                "Passageiro",
                "PASSENGER",
                "Ana Passageira",
                "ana@vanrides.dev",
                "51999999999",
                true,
                new AccountMeResponse.EmpresaResponse(66L, "Escola Aurora", "escola-aurora"),
                21L,
                44L,
                null,
                null,
                LocalDate.of(2012, 5, 20),
                "/uploads/avatars/passenger-21.png",
                true
        );
        when(accountMeService.resolve(UserRole.PASSENGER, 21L)).thenReturn(response);

        mockMvc.perform(get("/account/me")
                        .servletPath("/account/me")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.tipoUsuario").value("Passageiro"))
                .andExpect(jsonPath("$.papel").value("PASSENGER"))
                .andExpect(jsonPath("$.nome").value("Ana Passageira"))
                .andExpect(jsonPath("$.email").value("ana@vanrides.dev"))
                .andExpect(jsonPath("$.telefone").value("51999999999"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.empresa.id").value(66))
                .andExpect(jsonPath("$.empresa.nome").value("Escola Aurora"))
                .andExpect(jsonPath("$.empresa.slug").value("escola-aurora"))
                .andExpect(jsonPath("$.passageiroId").value(21))
                .andExpect(jsonPath("$.motoristaId").value(44))
                .andExpect(jsonPath("$.dataNascimento").value("2012-05-20"))
                .andExpect(jsonPath("$.fotoPerfilUrl").value("/uploads/avatars/passenger-21.png"))
                .andExpect(jsonPath("$.menorDeIdade").value(true))
                .andExpect(jsonPath("$.responsavelId").doesNotExist())
                .andExpect(jsonPath("$.companyAdminId").doesNotExist())
                .andExpect(content().string(not(containsString("senha"))))
                .andExpect(content().string(not(containsString("accessToken"))))
                .andExpect(content().string(not(containsString("refreshToken"))))
                .andExpect(content().string(not(containsString("roles"))));

        verify(accountMeService).resolve(UserRole.PASSENGER, 21L);
    }

    @Test
    void shouldReturnCurrentDriverAccount() throws Exception {
        String authorization = basicHeader("driver@vanrides.dev", "Driver!123");
        when(authorizationService.authenticate(authorization))
                .thenReturn(new AuthenticatedUser(31L, UserRole.DRIVER, "driver@vanrides.dev", "Driver", null, null));
        AccountMeResponse response = new AccountMeResponse(
                31L,
                "Motorista",
                "DRIVER",
                "Bruno Motorista",
                "driver@vanrides.dev",
                "51922222222",
                LocalDate.of(1988, 7, 14),
                "/uploads/avatars/bruno.webp",
                true,
                null,
                null,
                31L,
                null,
                null
        );
        when(accountMeService.resolve(UserRole.DRIVER, 31L)).thenReturn(response);

        mockMvc.perform(get("/account/me")
                        .servletPath("/account/me")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("Motorista"))
                .andExpect(jsonPath("$.papel").value("DRIVER"))
                .andExpect(jsonPath("$.motoristaId").value(31))
                .andExpect(jsonPath("$.dataNascimento").value("1988-07-14"))
                .andExpect(jsonPath("$.fotoPerfilUrl").value("/uploads/avatars/bruno.webp"))
                .andExpect(jsonPath("$.menorDeIdade").doesNotExist())
                .andExpect(jsonPath("$.empresa").doesNotExist());

        verify(accountMeService).resolve(UserRole.DRIVER, 31L);
    }

    @Test
    void shouldReturnCurrentGuardianAccount() throws Exception {
        String authorization = basicHeader("responsavel@vanrides.dev", "Resp!123");
        when(authorizationService.authenticate(authorization))
                .thenReturn(new AuthenticatedUser(71L, UserRole.GUARDIAN, "responsavel@vanrides.dev", "Responsavel", null, 66L));
        AccountMeResponse response = new AccountMeResponse(
                71L,
                "Responsavel",
                "GUARDIAN",
                "Carla Responsavel",
                "responsavel@vanrides.dev",
                "51933333333",
                true,
                new AccountMeResponse.EmpresaResponse(66L, "Escola Aurora", "escola-aurora"),
                null,
                null,
                71L,
                null
        );
        when(accountMeService.resolve(UserRole.GUARDIAN, 71L)).thenReturn(response);

        mockMvc.perform(get("/account/me")
                        .servletPath("/account/me")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("Responsavel"))
                .andExpect(jsonPath("$.papel").value("GUARDIAN"))
                .andExpect(jsonPath("$.responsavelId").value(71))
                .andExpect(jsonPath("$.dataNascimento").doesNotExist())
                .andExpect(jsonPath("$.fotoPerfilUrl").doesNotExist())
                .andExpect(jsonPath("$.empresa.id").value(66));

        verify(accountMeService).resolve(UserRole.GUARDIAN, 71L);
    }

    @Test
    void shouldReturnCurrentCompanyAdminAccount() throws Exception {
        String authorization = basicHeader("admin@vanrides.dev", "Admin!123");
        when(authorizationService.authenticate(authorization))
                .thenReturn(new AuthenticatedUser(90L, UserRole.COMPANY_ADMIN, "admin@vanrides.dev", "Admin", null, 66L));
        AccountMeResponse response = new AccountMeResponse(
                90L,
                "Admin",
                "COMPANY_ADMIN",
                "Admin Empresa",
                "admin@vanrides.dev",
                "51944444444",
                true,
                new AccountMeResponse.EmpresaResponse(66L, "Escola Aurora", "escola-aurora"),
                null,
                null,
                null,
                90L
        );
        when(accountMeService.resolve(UserRole.COMPANY_ADMIN, 90L)).thenReturn(response);

        mockMvc.perform(get("/account/me")
                        .servletPath("/account/me")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("Admin"))
                .andExpect(jsonPath("$.papel").value("COMPANY_ADMIN"))
                .andExpect(jsonPath("$.companyAdminId").value(90))
                .andExpect(jsonPath("$.dataNascimento").doesNotExist())
                .andExpect(jsonPath("$.fotoPerfilUrl").doesNotExist())
                .andExpect(jsonPath("$.empresa.slug").value("escola-aurora"));

        verify(accountMeService).resolve(UserRole.COMPANY_ADMIN, 90L);
    }

    @Test
    void shouldReturnUnauthorizedWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/account/me")
                        .servletPath("/account/me"))
                .andExpect(status().isUnauthorized());

        verify(accountMeService, never()).resolve(UserRole.PASSENGER, 21L);
    }

    @Test
    void shouldReturnUnauthorizedWhenUpdatingWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(patch("/account/me")
                        .servletPath("/account/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Ana",
                                  "telefone": "51999999999"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountMeService);
    }

    @Test
    void shouldReturnForbiddenWhenEmailIsNotVerified() throws Exception {
        String authorization = basicHeader("ana@vanrides.dev", "Secret!123");
        when(authorizationService.authenticate(authorization))
                .thenThrow(new EmailVerificationException(
                        "EMAIL_NOT_VERIFIED",
                        "O e-mail informado ainda não foi verificado.",
                        "ana@vanrides.dev",
                        403
                ));

        mockMvc.perform(get("/account/me")
                        .servletPath("/account/me")
                        .header("Authorization", authorization))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.email").value("ana@vanrides.dev"));

        verify(accountMeService, never()).resolve(UserRole.PASSENGER, 21L);
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
