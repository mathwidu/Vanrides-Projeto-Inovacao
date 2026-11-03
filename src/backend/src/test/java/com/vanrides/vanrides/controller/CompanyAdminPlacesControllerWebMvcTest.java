package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.PlacesProxyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminPlacesController.class)
class CompanyAdminPlacesControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlacesProxyService placesProxyService;

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
    void shouldRequireAuthForAutocomplete() throws Exception {
        mockMvc.perform(get("/company-admins/places/autocomplete")
                        .servletPath("/company-admins/places/autocomplete")
                        .param("input", "Escola"))
                .andExpect(status().isUnauthorized());

        verify(placesProxyService, never()).autocomplete(anyString());
    }

    @Test
    void shouldRequireAuthForDetails() throws Exception {
        mockMvc.perform(get("/company-admins/places/details")
                        .servletPath("/company-admins/places/details")
                        .param("placeId", "place-1"))
                .andExpect(status().isUnauthorized());

        verify(placesProxyService, never()).details(anyString());
    }

    @Test
    void shouldRejectAutocompleteAccessForNonCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("motorista.places@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "MOTORISTA", "motorista.places@vanrides.dev", "Motorista", 7L, 66L));

        mockMvc.perform(get("/company-admins/places/autocomplete")
                        .servletPath("/company-admins/places/autocomplete")
                        .param("input", "Escola")
                        .header("Authorization", basicHeader("motorista.places@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(placesProxyService, never()).autocomplete(anyString());
    }

    @Test
    void shouldRejectDetailsAccessForNonCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("motorista.places@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "MOTORISTA", "motorista.places@vanrides.dev", "Motorista", 7L, 66L));

        mockMvc.perform(get("/company-admins/places/details")
                        .servletPath("/company-admins/places/details")
                        .param("placeId", "place-1")
                        .header("Authorization", basicHeader("motorista.places@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(placesProxyService, never()).details(anyString());
    }

    @Test
    void shouldAutocompletePlacesForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.places@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.places@vanrides.dev", "Admin", null, 66L));
        when(placesProxyService.autocomplete("Escola"))
                .thenReturn(List.of(new PlacesProxyService.PlacePrediction(
                        "place-1",
                        "Escola Central",
                        "Porto Alegre, RS"
                )));

        mockMvc.perform(get("/company-admins/places/autocomplete")
                        .servletPath("/company-admins/places/autocomplete")
                        .param("input", "Escola")
                        .header("Authorization", basicHeader("admin.places@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placeId").value("place-1"))
                .andExpect(jsonPath("$[0].mainText").value("Escola Central"))
                .andExpect(jsonPath("$[0].secondaryText").value("Porto Alegre, RS"))
                .andExpect(jsonPath("$[0].description").doesNotExist())
                .andExpect(jsonPath("$[0].apiKey").doesNotExist());

        verify(placesProxyService).autocomplete("Escola");
    }

    @Test
    void shouldReturnPlaceDetailsForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.places@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.places@vanrides.dev", "Admin", null, 66L));
        when(placesProxyService.details("place-1"))
                .thenReturn(new PlacesProxyService.PlaceDetail(
                        "Escola Central",
                        "Av. Brasil, 100",
                        -30.0346,
                        -51.2177
                ));

        mockMvc.perform(get("/company-admins/places/details")
                        .servletPath("/company-admins/places/details")
                        .param("placeId", "place-1")
                        .header("Authorization", basicHeader("admin.places@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Escola Central"))
                .andExpect(jsonPath("$.endereco").value("Av. Brasil, 100"))
                .andExpect(jsonPath("$.latitude").value(-30.0346))
                .andExpect(jsonPath("$.longitude").value(-51.2177))
                .andExpect(jsonPath("$.placeId").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        verify(placesProxyService).details("place-1");
    }

    @Test
    void shouldReturnNotFoundWhenPlaceDetailsAreMissing() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.places@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.places@vanrides.dev", "Admin", null, 66L));
        when(placesProxyService.details("missing-place"))
                .thenReturn(null);

        mockMvc.perform(get("/company-admins/places/details")
                        .servletPath("/company-admins/places/details")
                        .param("placeId", "missing-place")
                        .header("Authorization", basicHeader("admin.places@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound());

        verify(placesProxyService).details("missing-place");
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
