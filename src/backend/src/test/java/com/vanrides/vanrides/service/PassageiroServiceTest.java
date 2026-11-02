package com.vanrides.vanrides.service;

import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassageiroServiceTest extends PassageiroServiceTestSupport {

    @Test
    void shouldAllowPassengerWithoutCompanyWhenMotoristaIsMissing() {
        Passageiro passageiro = buildPassageiro();

        when(geocodingService.buscarCoordenadas(anyString()))
                .thenReturn(new GeocodingService.Coordenadas(-30.0, -51.0));
        when(passageiroRepository.saveAndFlush(any(Passageiro.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Passageiro salvo = passageiroService.salvarNovoPassageiro(passageiro, passageiro.getSenha());

        ArgumentCaptor<Passageiro> captor = ArgumentCaptor.forClass(Passageiro.class);
        verify(passageiroRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getCompany()).isNull();
        assertThat(captor.getValue().getSenha()).startsWith("$2a$");
        assertThat(salvo.getLatitude()).isEqualTo(-30.0);
        assertThat(salvo.getLongitude()).isEqualTo(-51.0);
    }

    @Test
    void shouldReuseMotoristaCompanyWhenPassageiroReferencesExistingMotorista() {
        Company company = new Company("Empresa Azul", "empresa-azul");
        Motorista motorista = new Motorista(
                "Motorista Base",
                "motorista.base@vanrides.dev",
                "51999990010",
                "$2a$12$hash.fake"
        );
        motorista.setId(99L);
        motorista.setCompany(company);

        Passageiro passageiro = buildPassageiro();
        passageiro.setMotorista(new Motorista());
        passageiro.getMotorista().setId(99L);

        when(motoristaRepository.findById(99L)).thenReturn(Optional.of(motorista));
        when(geocodingService.buscarCoordenadas(anyString()))
                .thenReturn(new GeocodingService.Coordenadas(-30.1, -51.1));
        when(passageiroRepository.saveAndFlush(any(Passageiro.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Passageiro salvo = passageiroService.salvarNovoPassageiro(passageiro, passageiro.getSenha());

        assertThat(salvo.getMotorista()).isEqualTo(motorista);
        assertThat(salvo.getCompany()).isEqualTo(company);
    }

    @Test
    void shouldAutoVerifyPublicPassengerWhenQaDomainMatches() {
        ReflectionTestUtils.setField(passageiroService, "qaAutoVerifyEmailDomains", "vanrides.dev");
        Passageiro passageiro = buildPassageiro();
        passageiro.setEmail("qa.passenger@vanrides.dev");
        passageiro.setLatitude(-29.5753);
        passageiro.setLongitude(-50.7871);

        when(passageiroRepository.saveAndFlush(any(Passageiro.class))).thenAnswer(invocation -> {
            Passageiro saved = invocation.getArgument(0);
            saved.setId(88L);
            return saved;
        });

        Passageiro salvo = passageiroService.registrarPassageiroPublico(publicRequest(passageiro));

        assertThat(salvo.isEmailVerified()).isTrue();
        assertThat(salvo.getEmailVerifiedAt()).isNotNull();
        assertThat(salvo.getEmailVerificationLastSentAt()).isNull();
        verify(emailVerificationService, never()).markPendingAndRevokeSessions(any(), any());
        verify(emailVerificationService, never()).sendVerificationIfAllowed(any(), any(), any());
    }

    @Test
    void shouldCreateServerOwnedIdentityFromPublicRegistrationDto() {
        ReflectionTestUtils.setField(passageiroService, "qaAutoVerifyEmailDomains", "vanrides.dev");
        Passageiro request = buildPassageiro();
        request.setEmail("public.boundary@vanrides.dev");
        request.setLatitude(-30.0);
        request.setLongitude(-51.0);

        when(passageiroRepository.saveAndFlush(any(Passageiro.class))).thenAnswer(invocation -> {
            Passageiro candidate = invocation.getArgument(0);
            assertThat(candidate.getId()).isNull();
            candidate.setId(123L);
            return candidate;
        });

        Passageiro saved = passageiroService.registrarPassageiroPublico(publicRequest(request));

        assertThat(saved.getId()).isEqualTo(123L);
        assertThat(saved.getMotorista()).isNull();
        assertThat(saved.getCompany()).isNull();
        assertThat(saved.getFotoPerfilUrl()).isNull();
        verifyNoInteractions(motoristaRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectGloballyReservedEmailWithoutMutatingAnyAccount(boolean emailVerified) {
        ReflectionTestUtils.setField(passageiroService, "qaAutoVerifyEmailDomains", "vanrides.dev");
        Company pollutedCompany = new Company("Empresa Legada", "empresa-legada");
        Motorista pollutedDriver = new Motorista();
        pollutedDriver.setId(77L);
        pollutedDriver.setCompany(pollutedCompany);
        Passageiro pending = buildPassageiro();
        pending.setEmailVerified(emailVerified);
        pending.setMotorista(pollutedDriver);
        pending.setCompany(pollutedCompany);
        String originalPassword = pending.getSenha();
        Passageiro retry = buildPassageiro();
        retry.setSenha("AttackerPassword123");
        retry.setLatitude(-30.0);
        retry.setLongitude(-51.0);

        when(loginIdentityService.requireAvailable("passageiro.service@vanrides.dev"))
                .thenThrow(new LoginIdentityConflictException());

        assertThatThrownBy(() -> passageiroService.registrarPassageiroPublico(publicRequest(retry)))
                .isInstanceOfSatisfying(LoginIdentityConflictException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).isEqualTo("O e-mail informado já está em uso.");
                });

        assertThat(pending.getSenha()).isEqualTo(originalPassword);
        assertThat(pending.getMotorista()).isSameAs(pollutedDriver);
        assertThat(pending.getCompany()).isSameAs(pollutedCompany);
        assertThat(pending.isEmailVerified()).isEqualTo(emailVerified);
        verify(passageiroRepository, never()).saveAndFlush(any(Passageiro.class));
        verifyNoInteractions(motoristaRepository, emailVerificationService);
    }
}
