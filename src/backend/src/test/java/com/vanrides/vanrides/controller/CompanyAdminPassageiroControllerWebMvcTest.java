package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminPassageiroDetailResponse;
import com.vanrides.vanrides.dto.AdminPassageiroServicoResponse;
import com.vanrides.vanrides.dto.AdminPassageiroServicosUpdateRequest;
import com.vanrides.vanrides.dto.AdminPassageiroSummaryResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.TipoOperacaoServico;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminPassageiroController.class)
class CompanyAdminPassageiroControllerWebMvcTest
        extends CompanyAdminPassageiroControllerWebMvcTestSupport {

    @Test
    void shouldListCompanyPassengersForAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.portal@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.portal@vanrides.dev", "Admin", null, 66L));
        when(companyAdminPassageiroService.listarPassageiros(66L)).thenReturn(List.of(
                new AdminPassageiroSummaryResponse(21L, "Aluno Um", "aluno1@vanrides.dev", "51999990001", true,
                        new AdminMotoristaSummaryResponse(7L, "Motorista Um", "mot1@vanrides.dev", "51999990007"), 2)
        ));

        mockMvc.perform(get("/company-admins/passageiros")
                        .servletPath("/company-admins/passageiros")
                        .header("Authorization", basicHeader("admin.portal@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(21))
                .andExpect(jsonPath("$[0].motorista.id").value(7))
                .andExpect(jsonPath("$[0].responsavelCount").value(2));
    }

    @Test
    void shouldForbidPassengerRoleFromListingAdminPassengers() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro.portal@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(23L, "PASSENGER", "passageiro.portal@vanrides.dev", "Passageiro", null, 66L));

        mockMvc.perform(get("/company-admins/passageiros")
                        .servletPath("/company-admins/passageiros")
                        .header("Authorization", basicHeader("passageiro.portal@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(companyAdminPassageiroService, never()).listarPassageiros(eq(66L));
    }

    @Test
    void shouldCreatePassengerWithinAdminCompany() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.create@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.create@vanrides.dev", "Admin", null, 66L));
        when(companyAdminPassageiroService.criarPassageiro(eq(66L), any())).thenReturn(
                new AdminPassageiroDetailResponse(
                        25L, "Aluno Novo", "aluno.novo@vanrides.dev", "51999990025",
                        "Rua Nova", "Centro", "Porto Alegre", "RS", "90000-000",
                        -30.0, -51.0, true,
                        new AdminMotoristaSummaryResponse(7L, "Motorista Um", "mot1@vanrides.dev", "51999990007"),
                        List.of()
                )
        );

        mockMvc.perform(post("/company-admins/passageiros")
                        .servletPath("/company-admins/passageiros")
                        .header("Authorization", basicHeader("admin.create@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Aluno Novo",
                                  "email":"aluno.novo@vanrides.dev",
                                  "telefone":"51999990025",
                                  "endereco":"Rua Nova",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-000",
                                  "senha":"Aluno@123",
                                  "menorDeIdade":true,
                                  "motoristaId":7,
                                  "servicoIds":[10,11]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.motorista.id").value(7));
    }

    @Test
    void shouldReturnAdminPassengerDetail() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.detail@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.detail@vanrides.dev", "Admin", null, 66L));
        when(companyAdminPassageiroService.buscarDetalhe(21L, 66L)).thenReturn(Optional.of(
                new AdminPassageiroDetailResponse(
                        21L, "Aluno Detalhe", "aluno@vanrides.dev", "51999990021",
                        "Rua Teste", "Centro", "Porto Alegre", "RS", "90000-000",
                        -30.0, -51.0, true,
                        new AdminMotoristaSummaryResponse(7L, "Motorista Um", "mot1@vanrides.dev", "51999990007"),
                        List.of()
                )
        ));

        mockMvc.perform(get("/company-admins/passageiros/21")
                        .servletPath("/company-admins/passageiros/21")
                        .header("Authorization", basicHeader("admin.detail@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.menorDeIdade").value(true));
    }

    @Test
    void shouldListPassengerServiceEligibilityForAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.services@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.services@vanrides.dev", "Admin", null, 66L));
        when(companyAdminPassageiroService.listarServicosDoPassageiro(21L, 66L)).thenReturn(List.of(
                new AdminPassageiroServicoResponse(
                        10L,
                        "Ida manhã",
                        TipoOperacaoServico.IDA,
                        LocalTime.of(7, 30),
                        new AdminMotoristaSummaryResponse(7L, "Motorista Um", "mot1@vanrides.dev", "51999990007"),
                        true,
                        101L,
                        3,
                        null,
                        null
                ),
                new AdminPassageiroServicoResponse(
                        11L,
                        "Volta tarde",
                        TipoOperacaoServico.VOLTA,
                        LocalTime.of(17, 0),
                        null,
                        false,
                        null,
                        null,
                        null,
                        null
                )
        ));

        mockMvc.perform(get("/company-admins/passageiros/21/servicos")
                        .servletPath("/company-admins/passageiros/21/servicos")
                        .header("Authorization", basicHeader("admin.services@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].servicoId").value(10))
                .andExpect(jsonPath("$[0].nome").value("Ida manhã"))
                .andExpect(jsonPath("$[0].habilitado").value(true))
                .andExpect(jsonPath("$[0].motoristaPadrao.id").value(7))
                .andExpect(jsonPath("$[1].servicoId").value(11))
                .andExpect(jsonPath("$[1].habilitado").value(false));
    }

    @Test
    void shouldReplacePassengerServiceEligibilityForAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.services.update@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.services.update@vanrides.dev", "Admin", null, 66L));
        when(companyAdminPassageiroService.substituirServicosDoPassageiro(eq(21L), eq(66L), any(AdminPassageiroServicosUpdateRequest.class)))
                .thenReturn(List.of(
                        new AdminPassageiroServicoResponse(
                                10L,
                                "Ida manhã",
                                TipoOperacaoServico.IDA,
                                LocalTime.of(7, 30),
                                null,
                                true,
                                101L,
                                3,
                                null,
                                null
                        )
                ));

        mockMvc.perform(put("/company-admins/passageiros/21/servicos")
                        .servletPath("/company-admins/passageiros/21/servicos")
                        .header("Authorization", basicHeader("admin.services.update@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "servicoIds":[10]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].servicoId").value(10))
                .andExpect(jsonPath("$[0].habilitado").value(true));
    }

    @Test
    void shouldUpdateAdminPassenger() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.update@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.update@vanrides.dev", "Admin", null, 66L));
        when(companyAdminPassageiroService.atualizarPassageiro(eq(21L), eq(66L), any())).thenReturn(
                new AdminPassageiroDetailResponse(
                        21L, "Aluno Atualizado", "aluno.atualizado@vanrides.dev", "51999990021",
                        "Rua Atualizada", "Centro", "Porto Alegre", "RS", "90000-000",
                        -30.0, -51.0, false,
                        new AdminMotoristaSummaryResponse(8L, "Motorista Dois", "mot2@vanrides.dev", "51999990008"),
                        List.of()
                )
        );

        mockMvc.perform(put("/company-admins/passageiros/21")
                        .servletPath("/company-admins/passageiros/21")
                        .header("Authorization", basicHeader("admin.update@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Aluno Atualizado",
                                  "email":"aluno.atualizado@vanrides.dev",
                                  "telefone":"51999990021",
                                  "endereco":"Rua Atualizada",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-000",
                                  "menorDeIdade":false,
                                  "motoristaId":8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motorista.id").value(8));
    }

    @Test
    void shouldReassignPassengerDriverForAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.reassign@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.reassign@vanrides.dev", "Admin", null, 66L));
        when(companyAdminPassageiroService.reatribuirMotorista(21L, 66L, 8L)).thenReturn(
                new AdminPassageiroDetailResponse(
                        21L, "Aluno Atualizado", "aluno.atualizado@vanrides.dev", "51999990021",
                        "Rua Atualizada", "Centro", "Porto Alegre", "RS", "90000-000",
                        -30.0, -51.0, false,
                        new AdminMotoristaSummaryResponse(8L, "Motorista Dois", "mot2@vanrides.dev", "51999990008"),
                        List.of()
                )
        );

        mockMvc.perform(put("/company-admins/passageiros/21/motorista")
                        .servletPath("/company-admins/passageiros/21/motorista")
                        .header("Authorization", basicHeader("admin.reassign@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motoristaId":8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motorista.id").value(8));
    }

    @Test
    void shouldRejectReassignWhenMotoristaIdIsMissing() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.reassign.bad@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.reassign.bad@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(put("/company-admins/passageiros/21/motorista")
                        .servletPath("/company-admins/passageiros/21/motorista")
                        .header("Authorization", basicHeader("admin.reassign.bad@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectCreateWhenPayloadIsInvalid() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.bad@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.bad@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(post("/company-admins/passageiros")
                        .servletPath("/company-admins/passageiros")
                        .header("Authorization", basicHeader("admin.bad@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Aluno Sem Senha"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectCreateWhenOnlyOneCoordinateIsProvided() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.coords@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.coords@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(post("/company-admins/passageiros")
                        .servletPath("/company-admins/passageiros")
                        .header("Authorization", basicHeader("admin.coords@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Aluno Coordenada Parcial",
                                  "email":"aluno.coords@vanrides.dev",
                                  "telefone":"51999990025",
                                  "endereco":"Rua Nova",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-000",
                                  "senha":"Aluno@123",
                                  "latitude":-29.55
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

}
