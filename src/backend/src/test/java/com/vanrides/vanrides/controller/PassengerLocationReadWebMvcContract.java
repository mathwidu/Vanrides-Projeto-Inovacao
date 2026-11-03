package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.LocalizacaoMotoristaService;
import com.vanrides.vanrides.service.MotoristaService;
import com.vanrides.vanrides.service.PassengerLocationReadPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class PassengerLocationReadWebMvcContract {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected LocalizacaoMotoristaService localizacaoService;

    @MockitoBean
    protected MotoristaService motoristaService;

    @MockitoBean
    protected AuthorizationService authorizationService;

    @MockitoBean
    protected PassengerLocationReadPolicy passengerLocationReadPolicy;

    @Test
    void shouldReturnEquivalentNotFoundForUnlinkedUnknownAndCrossTenantMotorista() throws Exception {
        String authorizationHeader = basicHeader("passenger.unlinked.location@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(authorizationHeader))
                .thenReturn(new AuthenticatedUser(
                        41L,
                        "PASSENGER",
                        "passenger.unlinked.location@vanrides.dev",
                        null,
                        null,
                        66L
                ));
        for (Long motoristaId : new Long[]{5L, 999L, 77L}) {
            doThrow(new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Motorista não encontrado na empresa atual."
            )).when(passengerLocationReadPolicy).requireCanReadMotorista(41L, motoristaId, 66L);
        }

        MockHttpServletResponse sameTenantUnlinked = performDeniedLocationRead(
                authorizationHeader,
                5L
        );
        MockHttpServletResponse unknown = performDeniedLocationRead(authorizationHeader, 999L);
        MockHttpServletResponse crossTenant = performDeniedLocationRead(authorizationHeader, 77L);

        assertEquivalentResponse(unknown, sameTenantUnlinked);
        assertEquivalentResponse(crossTenant, sameTenantUnlinked);

        verify(passengerLocationReadPolicy).requireCanReadMotorista(41L, 5L, 66L);
        verify(passengerLocationReadPolicy).requireCanReadMotorista(41L, 999L, 66L);
        verify(passengerLocationReadPolicy).requireCanReadMotorista(41L, 77L, 66L);
        verifyNoInteractions(motoristaService, localizacaoService);
    }

    @Test
    void shouldAllowPassengerToReadRelatedMotoristaLocation() throws Exception {
        String authorizationHeader = basicHeader("passenger.related.location@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(authorizationHeader))
                .thenReturn(new AuthenticatedUser(
                        42L,
                        "PASSENGER",
                        "passenger.related.location@vanrides.dev",
                        null,
                        null,
                        66L
                ));
        when(motoristaService.buscarMotoristaPorId(5L, 66L))
                .thenReturn(Optional.of(authenticatedMotorista(
                        5L,
                        66L,
                        "driver.related.location@vanrides.dev",
                        "Secret123"
                )));
        LocalizacaoMotorista ultima = location(-30.0346, -51.2177);
        when(localizacaoService.buscarUltimaPorMotorista(5L, 66L)).thenReturn(ultima);

        mockMvc.perform(get("/api/localizacao/5")
                        .servletPath("/api/localizacao/5")
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(-30.0346))
                .andExpect(jsonPath("$.longitude").value(-51.2177));

        org.mockito.InOrder policyThenRead = inOrder(
                passengerLocationReadPolicy,
                motoristaService,
                localizacaoService
        );
        policyThenRead.verify(passengerLocationReadPolicy).requireCanReadMotorista(42L, 5L, 66L);
        policyThenRead.verify(motoristaService).buscarMotoristaPorId(5L, 66L);
        policyThenRead.verify(localizacaoService).buscarUltimaPorMotorista(5L, 66L);
    }

    @Test
    void shouldReturnNoContentForAuthorizedPassengerWhenLocationIsMissing() throws Exception {
        String authorizationHeader = basicHeader("passenger.related.empty-location@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(authorizationHeader))
                .thenReturn(new AuthenticatedUser(
                        43L,
                        "PASSENGER",
                        "passenger.related.empty-location@vanrides.dev",
                        null,
                        null,
                        66L
                ));
        when(motoristaService.buscarMotoristaPorId(6L, 66L))
                .thenReturn(Optional.of(authenticatedMotorista(
                        6L,
                        66L,
                        "driver.related.empty-location@vanrides.dev",
                        "Secret123"
                )));
        when(localizacaoService.buscarUltimaPorMotorista(6L, 66L)).thenReturn(null);

        mockMvc.perform(get("/api/localizacao/6")
                        .servletPath("/api/localizacao/6")
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        org.mockito.InOrder policyThenRead = inOrder(
                passengerLocationReadPolicy,
                motoristaService,
                localizacaoService
        );
        policyThenRead.verify(passengerLocationReadPolicy).requireCanReadMotorista(43L, 6L, 66L);
        policyThenRead.verify(motoristaService).buscarMotoristaPorId(6L, 66L);
        policyThenRead.verify(localizacaoService).buscarUltimaPorMotorista(6L, 66L);
    }

    @Test
    void shouldAllowCompanyAdminToReadTenantMotoristaLocation() throws Exception {
        String authorizationHeader = basicHeader("admin.location@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(authorizationHeader))
                .thenReturn(new AuthenticatedUser(
                        44L,
                        "COMPANY_ADMIN",
                        "admin.location@vanrides.dev",
                        null,
                        null,
                        66L
                ));
        when(motoristaService.buscarMotoristaPorId(7L, 66L))
                .thenReturn(Optional.of(authenticatedMotorista(
                        7L,
                        66L,
                        "driver.admin-visible.location@vanrides.dev",
                        "Secret123"
                )));
        when(localizacaoService.buscarUltimaPorMotorista(7L, 66L)).thenReturn(null);

        mockMvc.perform(get("/api/localizacao/7")
                        .servletPath("/api/localizacao/7")
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isNoContent());

        verifyNoInteractions(passengerLocationReadPolicy);
        verify(motoristaService).buscarMotoristaPorId(7L, 66L);
        verify(localizacaoService).buscarUltimaPorMotorista(7L, 66L);
    }

    protected Motorista authenticatedMotorista(Long motoristaId, Long companyId, String email, String senha) {
        Motorista motorista = new Motorista();
        motorista.setId(motoristaId);
        motorista.setEmail(email);
        motorista.setSenha(senha);
        Company company = new Company("Empresa Localizacao", "empresa-localizacao");
        company.setId(companyId);
        motorista.setCompany(company);
        return motorista;
    }

    protected String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private MockHttpServletResponse performDeniedLocationRead(
            String authorizationHeader,
            Long motoristaId) throws Exception {
        return mockMvc.perform(get("/api/localizacao/{motoristaId}", motoristaId)
                        .servletPath("/api/localizacao/" + motoristaId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Motorista não encontrado na empresa atual."))
                .andReturn()
                .getResponse();
    }

    private void assertEquivalentResponse(
            MockHttpServletResponse actual,
            MockHttpServletResponse reference) {
        assertThat(actual.getStatus()).isEqualTo(reference.getStatus());
        assertThat(actual.getContentType()).isEqualTo(reference.getContentType());
        assertThat(actual.getContentAsByteArray())
                .containsExactly(reference.getContentAsByteArray());
    }

    private LocalizacaoMotorista location(double latitude, double longitude) {
        LocalizacaoMotorista location = new LocalizacaoMotorista();
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setDataHora(LocalDateTime.now().minusSeconds(10));
        return location;
    }
}
