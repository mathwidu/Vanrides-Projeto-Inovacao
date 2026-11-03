package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.service.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminOpenApiController.class)
class CompanyAdminOpenApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthorizationService authorizationService;

    @Test
    void shouldExposeProtectedMinimalOpenApiForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate("Bearer admin-token"))
                .thenReturn(new AuthenticatedUser(
                        90L,
                        "COMPANY_ADMIN",
                        "admin@vanrides.dev",
                        "Admin",
                        null,
                        77L
                ));

        mockMvc.perform(get("/company-admins/openapi")
                        .servletPath("/company-admins/openapi")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.3"))
                .andExpect(jsonPath("$.info.title").value("VanRides API"))
                .andExpect(jsonPath("$.paths['/login'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/company-admins/viagens'].get.summary").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"));
    }

    @Test
    void shouldRejectOpenApiWithoutAuth() throws Exception {
        when(authorizationService.authenticate(null))
                .thenThrow(new IllegalArgumentException("Cabeçalho Authorization ausente"));

        mockMvc.perform(get("/company-admins/openapi")
                        .servletPath("/company-admins/openapi"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Cabeçalho Authorization ausente"));
    }
}
