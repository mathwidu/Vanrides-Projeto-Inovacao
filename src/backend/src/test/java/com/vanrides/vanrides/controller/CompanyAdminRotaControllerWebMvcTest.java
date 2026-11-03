package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AdminRotaBaseSummaryResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.CompanyAdminRotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminRotaController.class)
class CompanyAdminRotaControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyAdminRotaService companyAdminRotaService;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private PassageiroRepository passageiroRepository;

    @MockBean
    private MotoristaRepository motoristaRepository;

    @MockBean
    private GuardianRepository guardianRepository;

    @MockBean
    private CompanyAdminRepository companyAdminRepository;

    @Test
    void shouldListRouteTemplatesForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.rotas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminRotaService.listarRotasBase(66L))
                .thenReturn(List.of(new AdminRotaBaseSummaryResponse(
                        10L,
                        "Rota Escolar Manha",
                        null,
                        List.of(21L, 22L),
                        true,
                        true,
                        List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
                        null,
                        null,
                        null,
                        null,
                        null
                )));

        mockMvc.perform(get("/company-admins/rotas-base")
                        .servletPath("/company-admins/rotas-base")
                        .header("Authorization", basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].nome").value("Rota Escolar Manha"))
                .andExpect(jsonPath("$[0].quantidadeParadas").value(2))
                .andExpect(jsonPath("$[0].geracaoAutomaticaAtiva").value(true))
                .andExpect(jsonPath("$[0].diasOperacao[0]").value("MONDAY"));
    }

    @Test
    void shouldCreateRouteTemplateForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.rotas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminRotaService.criarRotaBase(eq(66L), any()))
                .thenReturn(new AdminRotaBaseSummaryResponse(
                        11L,
                        "Rota Escolar Tarde",
                        null,
                        List.of(21L, 22L, 23L),
                        true,
                        true,
                        List.of("MONDAY", "TUESDAY", "WEDNESDAY"),
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        mockMvc.perform(post("/company-admins/rotas-base")
                        .servletPath("/company-admins/rotas-base")
                        .header("Authorization", basicHeader("admin.rotas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Rota Escolar Tarde",
                                  "motoristaId":7,
                                  "passageiroIds":[21,22,23]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.nome").value("Rota Escolar Tarde"))
                .andExpect(jsonPath("$.geracaoAutomaticaAtiva").value(true));
    }

    @Test
    void shouldUpdateRouteTemplateForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.rotas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminRotaService.editarRotaBase(eq(66L), eq(11L), any()))
                .thenReturn(new AdminRotaBaseSummaryResponse(
                        11L,
                        "Rota Escolar Atualizada",
                        null,
                        List.of(21L, 22L, 23L, 24L),
                        true,
                        true,
                        List.of("MONDAY", "THURSDAY"),
                        null,
                        "Escola Central",
                        "Rua Principal, 100",
                        -30.01,
                        -51.22
                ));

        mockMvc.perform(put("/company-admins/rotas-base/11")
                        .servletPath("/company-admins/rotas-base/11")
                        .header("Authorization", basicHeader("admin.rotas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Rota Escolar Atualizada",
                                  "motoristaId":7,
                                  "passageiroIds":[21,22,23,24],
                                  "diasOperacao":["MONDAY","THURSDAY"],
                                  "destinoFinalNome":"Escola Central",
                                  "destinoFinalEndereco":"Rua Principal, 100",
                                  "destinoFinalLatitude":-30.01,
                                  "destinoFinalLongitude":-51.22
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.nome").value("Rota Escolar Atualizada"))
                .andExpect(jsonPath("$.quantidadeParadas").value(4))
                .andExpect(jsonPath("$.destinoFinalNome").value("Escola Central"));
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingRouteTemplateFromAnotherCompany() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.rotas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminRotaService.editarRotaBase(eq(66L), eq(99L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Rota base não encontrada na empresa atual."));

        mockMvc.perform(put("/company-admins/rotas-base/99")
                        .servletPath("/company-admins/rotas-base/99")
                        .header("Authorization", basicHeader("admin.rotas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Rota Externa",
                                  "motoristaId":7,
                                  "passageiroIds":[21]
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldInactivateRouteTemplateWhenNoTripIsInProgress() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.rotas@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(delete("/company-admins/rotas-base/11")
                        .servletPath("/company-admins/rotas-base/11")
                        .header("Authorization", basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnConflictWhenInactivatingRouteTemplateWithTripInProgress() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.rotas@vanrides.dev", "Admin", null, 66L));
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Existe viagem em andamento para esta rota."))
                .when(companyAdminRotaService)
                .inativarRotaBase(66L, 11L);

        mockMvc.perform(delete("/company-admins/rotas-base/11")
                        .servletPath("/company-admins/rotas-base/11")
                        .header("Authorization", basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldNotReturnInactiveRouteTemplatesOnList() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.rotas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminRotaService.listarRotasBase(66L))
                .thenReturn(List.of(new AdminRotaBaseSummaryResponse(
                        10L,
                        "Rota Ativa",
                        null,
                        List.of(21L, 22L),
                        true,
                        true,
                        List.of("MONDAY"),
                        null,
                        null,
                        null,
                        null,
                        null
                )));

        mockMvc.perform(get("/company-admins/rotas-base")
                        .servletPath("/company-admins/rotas-base")
                        .header("Authorization", basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Rota Ativa"));
    }

    @Test
    void shouldRejectRouteTemplateCreateWhenRequestIsInvalid() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.rotas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.rotas@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(post("/company-admins/rotas-base")
                        .servletPath("/company-admins/rotas-base")
                        .header("Authorization", basicHeader("admin.rotas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"  ",
                                  "motoristaId":7,
                                  "passageiroIds":[]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldForbidPassengerFromAdminRouteTemplates() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, 66L));

        mockMvc.perform(get("/company-admins/rotas-base")
                        .servletPath("/company-admins/rotas-base")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(companyAdminRotaService, never()).listarRotasBase(eq(66L));
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
