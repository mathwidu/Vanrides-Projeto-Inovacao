package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AccountPasswordChangeRequest;
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
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountPasswordControllerWebMvcTest {

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
    void shouldChangeCurrentAccountPassword() throws Exception {
        String authorization = basicHeader("admin@vanrides.dev", "Admin!123");
        when(authorizationService.authenticate(authorization))
                .thenReturn(new AuthenticatedUser(90L, UserRole.COMPANY_ADMIN, "admin@vanrides.dev", "Admin", null, 66L));
        doNothing().when(accountMeService)
                .changePassword(eq(UserRole.COMPANY_ADMIN), eq(90L), any(AccountPasswordChangeRequest.class));

        mockMvc.perform(post("/account/password/change")
                        .servletPath("/account/password/change")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senhaAtual": "Admin!123",
                                  "novaSenha": "Admin!456"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(accountMeService).changePassword(eq(UserRole.COMPANY_ADMIN), eq(90L), any(AccountPasswordChangeRequest.class));
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
