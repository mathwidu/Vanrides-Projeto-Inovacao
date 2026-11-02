package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.dto.PublicDriverRegistrationRequest;
import com.vanrides.vanrides.exception.ApiStatusException;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.MotoristaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MotoristaController.class)
class MotoristaControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MotoristaService motoristaService;

    @MockBean
    private AuthorizationService authorizationService;

    // Necessário porque o FilterAuthBasic é carregado no contexto WebMvc.
    @MockBean
    private PassageiroRepository passageiroRepository;

    @MockBean
    private MotoristaRepository motoristaRepository;

    @MockBean
    private JwtService jwtService;

    @Test
    void shouldListPublicMotoristasWithoutExposingPersonalData() throws Exception {
        Motorista motorista = new Motorista();
        motorista.setId(1L);
        motorista.setNome("Motorista QA");
        motorista.setEmail("qa.motorista@vanrides.dev");
        motorista.setTelefone("51999990000");
        motorista.setSenha("$2a$12$hash.fake");
        motorista.setDataNascimento(LocalDate.of(1990, 1, 15));
        motorista.setFotoPerfilUrl("/uploads/avatars/driver-1.png");

        when(motoristaService.listarTodosMotoristas()).thenReturn(List.of(motorista));

        mockMvc.perform(get("/motoristas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Motorista QA"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].telefone").doesNotExist())
                .andExpect(jsonPath("$[0].dataNascimento").doesNotExist())
                .andExpect(jsonPath("$[0].fotoPerfilUrl").doesNotExist())
                .andExpect(jsonPath("$[0].emailVerified").doesNotExist())
                .andExpect(jsonPath("$[0].emailVerifiedAt").doesNotExist())
                .andExpect(jsonPath("$[0].emailVerificationLastSentAt").doesNotExist())
                .andExpect(jsonPath("$[0].company").doesNotExist())
                .andExpect(jsonPath("$[0].senha").doesNotExist());
    }

    @Test
    void shouldReturnBadRequestWhenSenhaIsMissingOnCreateMotorista() throws Exception {
        mockMvc.perform(post("/motoristas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Sem Senha","email":"sem.senha@vanrides.dev","telefone":"51999990001"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Senha é obrigatória."));
    }

    @Test
    void shouldReturnInvalidEmailContractOnCreateMotorista() throws Exception {
        when(motoristaService.registrarMotoristaPublico(any(PublicDriverRegistrationRequest.class)))
                .thenThrow(new ApiStatusException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_EMAIL",
                        "Informe um e-mail válido."
                ));

        mockMvc.perform(post("/motoristas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Email Invalido","email":"invalido","telefone":"51999990001","senha":"Secret123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldCreateMotoristaWhenPayloadIsValid() throws Exception {
        Motorista salvo = new Motorista();
        salvo.setId(10L);
        salvo.setNome("Novo Motorista");
        salvo.setEmail("novo.motorista@vanrides.dev");
        salvo.setTelefone("51999990002");
        salvo.setSenha("$2a$12$hash.fake");
        salvo.setEmailVerified(false);
        salvo.setDataNascimento(LocalDate.of(1990, 1, 15));
        salvo.setFotoPerfilUrl("/uploads/avatars/driver-10.png");
        salvo.setEmailVerificationLastSentAt(OffsetDateTime.parse("2026-07-02T17:00:00-03:00"));

        when(motoristaService.registrarMotoristaPublico(any(PublicDriverRegistrationRequest.class))).thenReturn(salvo);

        mockMvc.perform(post("/motoristas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Novo Motorista",
                                  "email":"novo.motorista@vanrides.dev",
                                  "telefone":"51999990002",
                                  "senha":"Secret123",
                                  "id":999,
                                  "company":{"id":66},
                                  "emailVerified":true,
                                  "fotoPerfilUrl":"https://attacker.invalid/profile.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.nome").value("Novo Motorista"))
                .andExpect(jsonPath("$.email").value("novo.motorista@vanrides.dev"))
                .andExpect(jsonPath("$.telefone").value("51999990002"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.emailVerificationLastSentAt").value("2026-07-02T17:00:00-03:00"))
                .andExpect(jsonPath("$.dataNascimento").doesNotExist())
                .andExpect(jsonPath("$.fotoPerfilUrl").doesNotExist())
                .andExpect(jsonPath("$.company").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());

        ArgumentCaptor<PublicDriverRegistrationRequest> requestCaptor =
                ArgumentCaptor.forClass(PublicDriverRegistrationRequest.class);
        org.mockito.Mockito.verify(motoristaService)
                .registrarMotoristaPublico(requestCaptor.capture());
        assertThat(requestCaptor.getValue().email()).isEqualTo("novo.motorista@vanrides.dev");
        assertThat(PublicDriverRegistrationRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("id", "company", "emailVerified", "fotoPerfilUrl");
    }

    @Test
    void shouldReturnConflictWithoutOverwritingExistingPendingDriver() throws Exception {
        when(motoristaService.registrarMotoristaPublico(any(PublicDriverRegistrationRequest.class)))
                .thenThrow(new LoginIdentityConflictException());

        mockMvc.perform(post("/motoristas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Retry","email":"pending.driver@vanrides.dev","telefone":"51999990003","senha":"AttackerPassword123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }
}
