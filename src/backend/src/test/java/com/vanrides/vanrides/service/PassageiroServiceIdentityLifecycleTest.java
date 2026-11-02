package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.model.Passageiro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassageiroServiceIdentityLifecycleTest extends PassageiroServiceTestSupport {

    @Test
    void shouldRejectDriverReassignmentWhenPassengerIsOutsideTenant() {
        when(passageiroRepository.findByIdAndCompanyId(47L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passageiroService.reatribuirMotorista(47L, 7L, 9L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Passageiro não encontrado na empresa atual.");
                });

        verify(motoristaRepository, never()).findByIdAndCompanyId(any(), any());
        verify(passageiroRepository, never()).saveAndFlush(any(Passageiro.class));
        verify(loginIdentityService, never()).executeIdentityWrite(any());
    }

    @Test
    void shouldRejectDriverReassignmentWhenDriverIsOutsideTenant() {
        Passageiro passageiro = buildPassageiro();
        passageiro.setId(48L);
        passageiro.setSenha(BCRYPT_SHAPED_PASSWORD);
        passageiro.setLatitude(-30.0);
        passageiro.setLongitude(-51.0);
        when(passageiroRepository.findByIdAndCompanyId(48L, 7L)).thenReturn(Optional.of(passageiro));
        when(loginIdentityService.requireOwnedBy(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                48L
        )).thenReturn(passageiro.getEmail());
        when(motoristaRepository.findByIdAndCompanyId(9L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passageiroService.reatribuirMotorista(48L, 7L, 9L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Motorista não encontrado na empresa atual.");
                });

        verify(passageiroRepository, never()).saveAndFlush(any(Passageiro.class));
        verify(loginIdentityService, never()).executeIdentityWrite(any());
    }

    @Test
    void shouldUpdateLegacyPassengerInsideTheGlobalIdentityBoundary() {
        Passageiro current = buildPassageiro();
        current.setId(31L);
        String storedHash = BCRYPT_SHAPED_PASSWORD.replace("$2a$", "$2y$");
        current.setSenha(storedHash);
        current.setLatitude(-30.0);
        current.setLongitude(-51.0);
        Passageiro request = buildPassageiro();
        request.setSenha(null);
        request.setEmail(" New.Passenger@Example.com ");
        request.setLatitude(-30.1);
        request.setLongitude(-51.1);

        when(passageiroRepository.findByIdAndCompanyId(31L, 7L)).thenReturn(Optional.of(current));
        when(loginIdentityService.requireOwnedBy(
                "passageiro.service@vanrides.dev",
                UserRole.PASSENGER,
                31L
        )).thenReturn("passageiro.service@vanrides.dev");
        when(loginIdentityService.requireAvailableFor(
                " New.Passenger@Example.com ",
                UserRole.PASSENGER,
                31L
        )).thenReturn("new.passenger@example.com");
        when(passageiroRepository.saveAndFlush(current)).thenReturn(current);

        Optional<Passageiro> result = passageiroService.atualizarPassageiroProtegido(31L, 7L, request);

        assertThat(result).contains(current);
        assertThat(current.getEmail()).isEqualTo("new.passenger@example.com");
        assertThat(current.getSenha()).isEqualTo(storedHash);
        assertThat(current.isEmailVerified()).isFalse();
        verify(emailVerificationService).markPendingAndRevokeSessions(UserRole.PASSENGER, 31L);
        verify(emailVerificationService).sendVerificationIfAllowed(
                UserRole.PASSENGER,
                31L,
                "new.passenger@example.com"
        );
    }

    @Test
    void shouldLeaveArtifactsUntouchedWhenLegacyUpdateLosesTheRace() {
        Passageiro current = buildPassageiro();
        current.setId(32L);
        current.setSenha("$2a$12$hash.fake");
        current.setLatitude(-30.0);
        current.setLongitude(-51.0);
        Passageiro request = buildPassageiro();
        request.setEmail("race@example.com");
        request.setLatitude(-30.1);
        request.setLongitude(-51.1);

        when(passageiroRepository.findByIdAndCompanyId(32L, 7L)).thenReturn(Optional.of(current));
        when(loginIdentityService.requireOwnedBy(anyString(), any(), any()))
                .thenReturn("passageiro.service@vanrides.dev");
        when(loginIdentityService.requireAvailableFor(anyString(), any(), any()))
                .thenReturn("race@example.com");
        doThrow(new LoginIdentityConflictException())
                .when(loginIdentityService).executeIdentityWrite(any());

        assertThatThrownBy(() -> passageiroService.atualizarPassageiroProtegido(32L, 7L, request))
                .isInstanceOf(LoginIdentityConflictException.class);

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldDeletePassengerWithOneTenantScopedDmlAndNoJavaRevocation() {
        when(passageiroRepository.deleteByIdAndCompanyIdAtomically(33L, 7L)).thenReturn(1);

        passageiroService.deletarPassageiro(33L, 7L);

        verify(passageiroRepository).deleteByIdAndCompanyIdAtomically(33L, 7L);
        verify(passageiroRepository, never()).findById(any());
        verify(passageiroRepository, never()).findByIdAndCompanyId(any(), any());
        verify(passageiroRepository, never()).delete(any(Passageiro.class));
        verifyNoInteractions(loginIdentityService, emailVerificationService);
    }

    @Test
    void shouldReturnTenantSafeNotFoundWhenAtomicDeleteMatchesNoRow() {
        when(passageiroRepository.deleteByIdAndCompanyIdAtomically(33L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> passageiroService.deletarPassageiro(33L, 7L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Passageiro não encontrado na empresa atual.");
                });

        verify(passageiroRepository).deleteByIdAndCompanyIdAtomically(33L, 7L);
        verify(passageiroRepository, never()).findById(any());
        verify(passageiroRepository, never()).findByIdAndCompanyId(any(), any());
        verify(passageiroRepository, never()).delete(any(Passageiro.class));
        verifyNoInteractions(loginIdentityService, emailVerificationService);
    }

    @Test
    void shouldFailClosedWhenAtomicDeleteReportsAnImpossibleRowCount() {
        when(passageiroRepository.deleteByIdAndCompanyIdAtomically(33L, 7L)).thenReturn(2);

        assertThatThrownBy(() -> passageiroService.deletarPassageiro(33L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Delete tenant-scoped removeu mais de um passageiro.");

        verify(passageiroRepository).deleteByIdAndCompanyIdAtomically(33L, 7L);
        verifyNoInteractions(loginIdentityService, emailVerificationService);
    }

    @Test
    void shouldFailClosedWhenLegacyEmailLookupFindsOwnerDrift() {
        Passageiro drifted = buildPassageiro();
        drifted.setId(34L);
        drifted.setEmail("different@example.com");
        when(loginIdentityService.findOwner("owner@example.com")).thenReturn(Optional.of(
                new LoginIdentityService.LoginIdentityOwner(
                        UserRole.PASSENGER,
                        34L,
                        "owner@example.com"
                )
        ));
        when(passageiroRepository.findById(34L)).thenReturn(Optional.of(drifted));

        assertThat(passageiroService.buscarPorEmail("owner@example.com")).isNull();
    }
}
