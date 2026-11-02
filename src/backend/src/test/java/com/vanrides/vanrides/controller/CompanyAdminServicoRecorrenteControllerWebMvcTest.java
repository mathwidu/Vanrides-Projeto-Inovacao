package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminPassageiroLinkedSummaryResponse;
import com.vanrides.vanrides.dto.AdminServicoPassageiroResponse;
import com.vanrides.vanrides.dto.AdminServicoRecorrenteResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.TipoOperacaoServico;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.CompanyAdminServicoRecorrenteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
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

@WebMvcTest(CompanyAdminServicoRecorrenteController.class)
class CompanyAdminServicoRecorrenteControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyAdminServicoRecorrenteService companyAdminServicoRecorrenteService;

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
    void shouldListRecurringServicesForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.servicos@vanrides.dev", "Admin", null, 66L));
        when(companyAdminServicoRecorrenteService.listarServicos(66L))
                .thenReturn(List.of(servicoResponse(10L, List.of(21L, 22L))));

        mockMvc.perform(get("/company-admins/servicos-recorrentes")
                        .servletPath("/company-admins/servicos-recorrentes")
                        .header("Authorization", basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].nome").value("Ida manha"))
                .andExpect(jsonPath("$[0].tipoOperacao").value("IDA"))
                .andExpect(jsonPath("$[0].quantidadePassageiros").value(2))
                .andExpect(jsonPath("$[0].motoristaPadrao.id").value(7));
    }

    @Test
    void shouldCreateRecurringServiceForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.servicos@vanrides.dev", "Admin", null, 66L));
        when(companyAdminServicoRecorrenteService.criarServico(eq(66L), any()))
                .thenReturn(servicoResponse(11L, List.of()));

        mockMvc.perform(post("/company-admins/servicos-recorrentes")
                        .servletPath("/company-admins/servicos-recorrentes")
                        .header("Authorization", basicHeader("admin.servicos@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Ida manha",
                                  "tipoOperacao":"IDA",
                                  "politicaPresenca":"INCLUIR_POR_PADRAO",
                                  "horarioPartida":"07:15:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.quantidadePassageiros").value(0));
    }

    @Test
    void shouldDetailRecurringServiceForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.servicos@vanrides.dev", "Admin", null, 66L));
        when(companyAdminServicoRecorrenteService.detalharServico(66L, 10L))
                .thenReturn(servicoResponse(10L, List.of(21L)));

        mockMvc.perform(get("/company-admins/servicos-recorrentes/10")
                        .servletPath("/company-admins/servicos-recorrentes/10")
                        .header("Authorization", basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.passageiroIds[0]").value(21));
    }

    @Test
    void shouldUpdateRecurringServiceForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.servicos@vanrides.dev", "Admin", null, 66L));
        when(companyAdminServicoRecorrenteService.editarServico(eq(66L), eq(10L), any()))
                .thenReturn(servicoResponse(10L, List.of(21L, 22L)));

        mockMvc.perform(put("/company-admins/servicos-recorrentes/10")
                        .servletPath("/company-admins/servicos-recorrentes/10")
                        .header("Authorization", basicHeader("admin.servicos@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Ida manha",
                                  "motoristaPadraoId":7,
                                  "tipoOperacao":"IDA",
                                  "politicaPresenca":"INCLUIR_POR_PADRAO",
                                  "diasOperacao":["MONDAY","TUESDAY"],
                                  "passageiroIds":[21,22]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.passageiroIds.length()").value(2));
    }

    @Test
    void shouldListEligiblePassengersForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.servicos@vanrides.dev", "Admin", null, 66L));
        when(companyAdminServicoRecorrenteService.listarPassageiros(66L, 10L))
                .thenReturn(List.of(new AdminServicoPassageiroResponse(
                        30L,
                        new AdminPassageiroLinkedSummaryResponse(21L, "Ana", "ana@vanrides.dev", false, null),
                        1,
                        true,
                        null,
                        null
                )));

        mockMvc.perform(get("/company-admins/servicos-recorrentes/10/passageiros")
                        .servletPath("/company-admins/servicos-recorrentes/10/passageiros")
                        .header("Authorization", basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passageiro.nome").value("Ana"));
    }

    @Test
    void shouldReplaceEligiblePassengersForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.servicos@vanrides.dev", "Admin", null, 66L));
        when(companyAdminServicoRecorrenteService.substituirPassageiros(eq(66L), eq(10L), any()))
                .thenReturn(List.of(new AdminServicoPassageiroResponse(
                        30L,
                        new AdminPassageiroLinkedSummaryResponse(21L, "Ana", "ana@vanrides.dev", false, null),
                        1,
                        true,
                        null,
                        null
                )));

        mockMvc.perform(put("/company-admins/servicos-recorrentes/10/passageiros")
                        .servletPath("/company-admins/servicos-recorrentes/10/passageiros")
                        .header("Authorization", basicHeader("admin.servicos@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "passageiroIds":[21]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passageiro.id").value(21))
                .andExpect(jsonPath("$[0].ordemPadrao").value(1));
    }

    @Test
    void shouldInactivateRecurringServiceForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.servicos@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(delete("/company-admins/servicos-recorrentes/10")
                        .servletPath("/company-admins/servicos-recorrentes/10")
                        .header("Authorization", basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent());

        verify(companyAdminServicoRecorrenteService).inativarServico(66L, 10L);
    }

    @Test
    void shouldReturnConflictWhenRecurringServiceHasTripInProgress() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.servicos@vanrides.dev", "Admin", null, 66L));
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Não é possível inativar o serviço: existe viagem em andamento."
                ))
                .when(companyAdminServicoRecorrenteService)
                .inativarServico(66L, 10L);

        mockMvc.perform(delete("/company-admins/servicos-recorrentes/10")
                        .servletPath("/company-admins/servicos-recorrentes/10")
                        .header("Authorization", basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .andExpect(status().isConflict());

        verify(companyAdminServicoRecorrenteService).inativarServico(66L, 10L);
    }

    @Test
    void shouldReturnBadRequestWhenServiceRejectsInvalidPayload() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.servicos@vanrides.dev", "Admin", null, 66L));
        when(companyAdminServicoRecorrenteService.criarServico(eq(66L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do serviço é obrigatório."));

        mockMvc.perform(post("/company-admins/servicos-recorrentes")
                        .servletPath("/company-admins/servicos-recorrentes")
                        .header("Authorization", basicHeader("admin.servicos@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":" "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldForbidDriverFromAdminRecurringServices() throws Exception {
        when(authorizationService.authenticate(basicHeader("motorista@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "motorista@vanrides.dev", "Motorista", null, 66L));

        mockMvc.perform(get("/company-admins/servicos-recorrentes")
                        .servletPath("/company-admins/servicos-recorrentes")
                        .header("Authorization", basicHeader("motorista@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(companyAdminServicoRecorrenteService, never()).listarServicos(66L);
    }

    private AdminServicoRecorrenteResponse servicoResponse(Long id, List<Long> passageiroIds) {
        return new AdminServicoRecorrenteResponse(
                id,
                null,
                "Ida manha",
                TipoOperacaoServico.IDA,
                new AdminMotoristaSummaryResponse(7L, "Joao", "joao@vanrides.dev", "51999990000"),
                passageiroIds,
                true,
                true,
                PoliticaPresencaServico.INCLUIR_POR_PADRAO,
                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
                LocalTime.of(7, 15),
                "Escola Central",
                "Rua Escola, 100",
                -30.01,
                -51.22,
                12
        );
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
