package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.PublicDriverRegistrationRequest;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.MotoristaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MotoristaPublicRegistrationServiceTest {

    @Mock private MotoristaRepository motoristaRepository;
    @Mock private CompanyService companyService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private LoginIdentityService loginIdentityService;

    @InjectMocks
    private MotoristaService motoristaService;

    @BeforeEach
    void configureIdentitySeam() {
        lenient().when(loginIdentityService.requireAvailable(any()))
                .thenAnswer(invocation -> normalize(invocation.getArgument(0, String.class)));
        lenient().when(loginIdentityService.executeIdentityWrite(any()))
                .thenAnswer(invocation -> {
                    Supplier<?> write = invocation.getArgument(0);
                    return write.get();
                });
    }

    @Test
    void shouldAutoVerifyPublicDriverWhenQaDomainMatches() {
        ReflectionTestUtils.setField(motoristaService, "qaAutoVerifyEmailDomains", "vanrides.dev");
        Motorista motorista = new Motorista(
                "Motorista QA",
                "qa.driver@vanrides.dev",
                "51999990010",
                "Secret123!"
        );

        when(motoristaRepository.saveAndFlush(any(Motorista.class))).thenAnswer(invocation -> {
            Motorista saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        Motorista salvo = motoristaService.registrarMotoristaPublico(publicRequest(motorista));

        assertThat(salvo.getCompany()).isNull();
        assertThat(salvo.isEmailVerified()).isTrue();
        assertThat(salvo.getEmailVerifiedAt()).isNotNull();
        assertThat(salvo.getEmailVerificationLastSentAt()).isNull();
        verify(companyService, never()).getDefaultCompany();
        verify(emailVerificationService, never()).markPendingAndRevokeSessions(any(), any());
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
    }

    @Test
    void shouldCreateServerOwnedDriverIdentityWithoutClientControlledFields() {
        ReflectionTestUtils.setField(motoristaService, "qaAutoVerifyEmailDomains", "vanrides.dev");
        Company attackerCompany = new Company("Empresa Injetada", "empresa-injetada");
        Motorista request = new Motorista(
                "Motorista Público",
                "public.driver@vanrides.dev",
                "51999990011",
                "Secret123!"
        );
        request.setId(900L);
        request.setCompany(attackerCompany);
        request.setFotoPerfilUrl("https://attacker.invalid/profile.png");
        request.setEmailVerified(true);

        when(motoristaRepository.saveAndFlush(any(Motorista.class))).thenAnswer(invocation -> {
            Motorista candidate = invocation.getArgument(0);
            assertThat(candidate.getId()).isNull();
            candidate.setId(91L);
            return candidate;
        });

        Motorista saved = motoristaService.registrarMotoristaPublico(publicRequest(request));

        assertThat(saved.getId()).isEqualTo(91L);
        assertThat(saved.getCompany()).isNull();
        assertThat(saved.getFotoPerfilUrl()).isNull();
        verifyNoInteractions(companyService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectDuplicateDriverWithoutMutatingIdentity(boolean emailVerified) {
        ReflectionTestUtils.setField(motoristaService, "qaAutoVerifyEmailDomains", "vanrides.dev");
        Company originalCompany = new Company("Empresa Original", "empresa-original");
        Motorista pending = new Motorista(
                "Motorista Original",
                "pending.driver@vanrides.dev",
                "51999990012",
                "$2a$12$original.hash"
        );
        pending.setId(92L);
        pending.setCompany(originalCompany);
        pending.setFotoPerfilUrl("/uploads/original.png");
        pending.setEmailVerified(emailVerified);
        Motorista retry = new Motorista(
                "Atacante",
                "pending.driver@vanrides.dev",
                "51999990013",
                "AttackerPassword123!"
        );

        LoginIdentityConflictException conflict = new LoginIdentityConflictException();
        when(loginIdentityService.requireAvailable("pending.driver@vanrides.dev"))
                .thenThrow(conflict);

        assertThatThrownBy(() -> motoristaService.registrarMotoristaPublico(publicRequest(retry)))
                .isSameAs(conflict)
                .isInstanceOfSatisfying(LoginIdentityConflictException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason())
                            .isEqualTo("O e-mail informado já está em uso.");
                });

        assertThat(pending.getNome()).isEqualTo("Motorista Original");
        assertThat(pending.getTelefone()).isEqualTo("51999990012");
        assertThat(pending.getSenha()).isEqualTo("$2a$12$original.hash");
        assertThat(pending.getCompany()).isSameAs(originalCompany);
        assertThat(pending.getFotoPerfilUrl()).isEqualTo("/uploads/original.png");
        assertThat(pending.isEmailVerified()).isEqualTo(emailVerified);
        verify(motoristaRepository, never()).saveAndFlush(any(Motorista.class));
        verifyNoInteractions(emailVerificationService);
    }

    private PublicDriverRegistrationRequest publicRequest(Motorista motorista) {
        return new PublicDriverRegistrationRequest(
                motorista.getNome(),
                motorista.getEmail(),
                motorista.getTelefone(),
                motorista.getSenha(),
                motorista.getDataNascimento()
        );
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
