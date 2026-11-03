package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.exception.ApiStatusException;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.PassageiroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

abstract class PassageiroControllerWebMvcTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @MockBean
    protected PassageiroService passageiroService;

    @MockBean
    protected AuthorizationService authorizationService;

    // Necessário porque o FilterAuthBasic é carregado no contexto WebMvc.
    @MockBean
    protected PassageiroRepository passageiroRepository;

    @MockBean
    protected MotoristaRepository motoristaRepository;

    @MockBean
    protected JwtService jwtService;

    @MockBean
    protected GuardianAccessService guardianAccessService;

    @MockBean
    protected EmailVerificationService emailVerificationService;

    protected Passageiro savedPassengerWithPrivateFields() {
        Passageiro saved = new Passageiro();
        saved.setId(15L);
        saved.setNome("Passageiro QA");
        saved.setEmail("passageiro.qa@vanrides.dev");
        saved.setTelefone("51999990005");
        saved.setEndereco("Rua QA");
        saved.setBairro("Centro");
        saved.setCidade("Porto Alegre");
        saved.setEstado("RS");
        saved.setCep("90000-111");
        saved.setLatitude(-30.0346);
        saved.setLongitude(-51.2177);
        saved.setDataNascimento(LocalDate.of(2010, 5, 20));
        saved.setFotoPerfilUrl("/uploads/avatars/passenger-15.png");
        saved.setSenha("$2a$12$hash.fake");
        saved.setEmailVerificationLastSentAt(
                OffsetDateTime.parse("2026-07-02T17:05:00-03:00")
        );
        Motorista driver = new Motorista();
        driver.setId(7L);
        driver.setNome("Motorista PII");
        driver.setEmail("motorista.pii@vanrides.dev");
        driver.setTelefone("51999990007");
        driver.setSenha("$2a$12$hash.fake");
        saved.setMotorista(driver);
        return saved;
    }

    protected ApiStatusException invalidEmail() {
        return new ApiStatusException(
                HttpStatus.BAD_REQUEST,
                "INVALID_EMAIL",
                "Informe um e-mail válido."
        );
    }

    protected ResponseStatusException passengerNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Passageiro não encontrado na empresa atual."
        );
    }

    protected void stubAdminAuthorization() {
        when(authorizationService.authenticate(any(String.class))).thenReturn(
                new AuthenticatedUser(
                        90L, "COMPANY_ADMIN", "admin@vanrides.dev", "Admin", null, 66L
                ));
    }

    protected String basicHeader() {
        return "Basic YWRtaW5AdmFucmlkZXMuZGV2OlNlY3JldDEyMw==";
    }

    protected String validPassengerPayload(String email) {
        return """
                {
                  "nome":"Passageiro QA",
                  "email":"%s",
                  "telefone":"51999990005",
                  "senha":"Secret123",
                  "endereco":"Rua QA",
                  "bairro":"Centro",
                  "cidade":"Porto Alegre",
                  "estado":"RS",
                  "cep":"90000-111"
                }
                """.formatted(email);
    }
}
