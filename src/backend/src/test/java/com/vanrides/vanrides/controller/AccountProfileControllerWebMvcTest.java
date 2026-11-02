package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AccountMeResponse;
import com.vanrides.vanrides.dto.AccountProfileUpdateRequest;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AccountAvatarService;
import com.vanrides.vanrides.service.AccountAvatarStorageService;
import com.vanrides.vanrides.service.AccountMeService;
import com.vanrides.vanrides.service.AuthorizationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountProfileControllerWebMvcTest {

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
    void shouldPatchCurrentAccountProfileWithoutChangingSensitiveFields() throws Exception {
        String authorization = basicHeader("ana@vanrides.dev", "Secret!123");
        when(authorizationService.authenticate(authorization))
                .thenReturn(new AuthenticatedUser(21L, UserRole.PASSENGER, "ana@vanrides.dev", "Ana", 44L, 66L));
        AccountMeResponse response = new AccountMeResponse(
                21L,
                "Passageiro",
                "PASSENGER",
                "Ana Atualizada",
                "ana@vanrides.dev",
                "51988888888",
                true,
                new AccountMeResponse.EmpresaResponse(66L, "Escola Aurora", "escola-aurora"),
                21L,
                44L,
                null,
                null,
                LocalDate.of(2011, 8, 9),
                "/uploads/avatars/passenger-21.png",
                true
        );
        when(accountMeService.updateProfile(eq(UserRole.PASSENGER), eq(21L), any(AccountProfileUpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/account/me")
                        .servletPath("/account/me")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Ana Atualizada",
                                  "telefone": "51988888888",
                                  "dataNascimento": "2011-08-09",
                                  "email": "tentativa@vanrides.dev",
                                  "papel": "COMPANY_ADMIN",
                                  "empresa": {"id": 999},
                                  "senha": "NaoPode123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Ana Atualizada"))
                .andExpect(jsonPath("$.telefone").value("51988888888"))
                .andExpect(jsonPath("$.email").value("ana@vanrides.dev"))
                .andExpect(jsonPath("$.papel").value("PASSENGER"))
                .andExpect(jsonPath("$.dataNascimento").value("2011-08-09"))
                .andExpect(jsonPath("$.fotoPerfilUrl").value("/uploads/avatars/passenger-21.png"))
                .andExpect(content().string(not(containsString("senha"))));

        verify(accountMeService).updateProfile(eq(UserRole.PASSENGER), eq(21L), any(AccountProfileUpdateRequest.class));
    }

    @Test
    void shouldPatchCurrentAccountProfilePhotoDataUri() throws Exception {
        String authorization = basicHeader("driver@vanrides.dev", "Driver!123");
        when(authorizationService.authenticate(authorization))
                .thenReturn(new AuthenticatedUser(31L, UserRole.DRIVER, "driver@vanrides.dev", "Driver", null, null));
        String photoDataUri = "data:image/png;base64,iVBORw0KGgo=";
        AccountMeResponse response = new AccountMeResponse(
                31L,
                "Motorista",
                "DRIVER",
                "Bruno Motorista",
                "driver@vanrides.dev",
                "51922222222",
                true,
                null,
                null,
                31L,
                null,
                null,
                LocalDate.of(1990, 1, 15),
                photoDataUri,
                null
        );
        when(accountMeService.updateProfile(eq(UserRole.DRIVER), eq(31L), any(AccountProfileUpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/account/me")
                        .servletPath("/account/me")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fotoPerfilUrl": "data:image/png;base64,iVBORw0KGgo="
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoPerfilUrl").value(photoDataUri))
                .andExpect(content().string(not(containsString("senha"))));

        verify(accountMeService).updateProfile(eq(UserRole.DRIVER), eq(31L), any(AccountProfileUpdateRequest.class));
    }

    @Test
    void shouldUploadCurrentAccountAvatar() throws Exception {
        String authorization = basicHeader("driver@vanrides.dev", "Driver!123");
        when(authorizationService.authenticate(authorization))
                .thenReturn(new AuthenticatedUser(31L, UserRole.DRIVER, "driver@vanrides.dev", "Driver", null, null));
        String avatarUrl = "/uploads/avatars/driver-31-test.png";
        AccountMeResponse response = new AccountMeResponse(
                31L,
                "Motorista",
                "DRIVER",
                "Bruno Motorista",
                "driver@vanrides.dev",
                "51922222222",
                true,
                null,
                null,
                31L,
                null,
                null,
                LocalDate.of(1990, 1, 15),
                avatarUrl,
                null
        );
        when(avatarStorageService.store(eq(UserRole.DRIVER), eq(31L), any()))
                .thenReturn(avatarUrl);
        when(accountAvatarService.updateAvatar(UserRole.DRIVER, 31L, avatarUrl))
                .thenReturn(response);

        mockMvc.perform(multipart("/account/me/avatar")
                        .file("file", new byte[]{1, 2, 3, 4})
                        .servletPath("/account/me/avatar")
                        .header("Authorization", authorization)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoPerfilUrl").value(avatarUrl))
                .andExpect(content().string(not(containsString("senha"))));

        verify(avatarStorageService).store(eq(UserRole.DRIVER), eq(31L), any());
        verify(accountAvatarService).updateAvatar(UserRole.DRIVER, 31L, avatarUrl);
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
