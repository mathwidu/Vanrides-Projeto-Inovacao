package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminPassageiroUpdateRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassageiroServiceCredentialPolicyTest extends PassageiroServiceTestSupport {

    @Test
    void shouldHashBcryptShapedPasswordForNewPassenger() {
        Passageiro passageiro = buildPassageiro();
        String ignoredEntityCredential = "EntityCredentialMustBeIgnored!";
        passageiro.setSenha(ignoredEntityCredential);
        passageiro.setLatitude(-30.0);
        passageiro.setLongitude(-51.0);
        when(passageiroRepository.saveAndFlush(passageiro)).thenReturn(passageiro);

        Passageiro saved = passageiroService.salvarNovoPassageiro(passageiro, BCRYPT_SHAPED_PASSWORD);

        assertThat(saved.getSenha()).isNotEqualTo(BCRYPT_SHAPED_PASSWORD);
        assertThat(saved.getSenha()).startsWith("$2a$12$");
        assertThat(BCrypt.verifyer().verify(
                BCRYPT_SHAPED_PASSWORD.toCharArray(),
                saved.getSenha().toCharArray()
        ).verified).isTrue();
        assertThat(BCrypt.verifyer().verify(
                ignoredEntityCredential.toCharArray(),
                saved.getSenha().toCharArray()
        ).verified).isFalse();
    }

    @Test
    void shouldRejectMissingRawPasswordArgumentForNewPassenger() {
        Passageiro passageiro = buildPassageiro();
        passageiro.setLatitude(-30.0);
        passageiro.setLongitude(-51.0);

        assertThatThrownBy(() -> passageiroService.salvarNovoPassageiro(passageiro, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("Senha é obrigatória.");
                });

        verify(passageiroRepository, never()).saveAndFlush(any(Passageiro.class));
        verify(loginIdentityService, never()).executeIdentityWrite(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "$2a$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u",
            "$2b$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u",
            "$2y$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u"
    })
    void shouldPreserveStoredBcryptVariantsLoadedInsideAdministrativeUpdate(String storedHash) {
        Passageiro passageiro = buildPassageiro();
        passageiro.setId(41L);
        passageiro.setSenha(storedHash);
        passageiro.setLatitude(-30.0);
        passageiro.setLongitude(-51.0);
        AdminPassageiroUpdateRequest request = adminUpdateRequest(passageiro.getEmail(), null);
        when(passageiroRepository.findByIdAndCompanyId(41L, 7L)).thenReturn(Optional.of(passageiro));
        when(loginIdentityService.requireOwnedBy(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                41L
        )).thenReturn(passageiro.getEmail());
        when(loginIdentityService.requireAvailableFor(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                41L
        )).thenReturn(passageiro.getEmail());
        when(passageiroRepository.saveAndFlush(passageiro)).thenReturn(passageiro);

        Passageiro saved = passageiroService.atualizarPassageiroAdministrativo(41L, 7L, request);

        assertThat(saved.getSenha()).isEqualTo(storedHash);
    }

    @Test
    void shouldRejectExistingPassengerAtNewPassengerBoundaryEvenWithValidBcryptShape() {
        Passageiro passageiro = buildPassageiro();
        passageiro.setId(44L);
        passageiro.setSenha(BCRYPT_SHAPED_PASSWORD);
        passageiro.setLatitude(-30.0);
        passageiro.setLongitude(-51.0);

        assertThatThrownBy(() -> passageiroService.salvarNovoPassageiro(
                passageiro,
                BCRYPT_SHAPED_PASSWORD
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("salvarNovoPassageiro aceita apenas passageiros ainda não persistidos.");

        verify(passageiroRepository, never()).saveAndFlush(any(Passageiro.class));
        verify(loginIdentityService, never()).executeIdentityWrite(any());
        verify(loginIdentityService, never()).requireAvailable(anyString());
    }

    @Test
    void shouldTreatBcryptShapedAdministrativePasswordAsRaw() {
        Passageiro passageiro = buildPassageiro();
        passageiro.setId(42L);
        passageiro.setSenha(BCRYPT_SHAPED_PASSWORD.replace("$2a$", "$2y$"));
        passageiro.setLatitude(-30.0);
        passageiro.setLongitude(-51.0);
        AdminPassageiroUpdateRequest request = adminUpdateRequest(
                passageiro.getEmail(),
                BCRYPT_SHAPED_PASSWORD
        );
        when(passageiroRepository.findByIdAndCompanyId(42L, 7L)).thenReturn(Optional.of(passageiro));
        when(loginIdentityService.requireOwnedBy(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                42L
        )).thenReturn(passageiro.getEmail());
        when(loginIdentityService.requireAvailableFor(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                42L
        )).thenReturn(passageiro.getEmail());
        when(passageiroRepository.saveAndFlush(passageiro)).thenReturn(passageiro);

        Passageiro saved = passageiroService.atualizarPassageiroAdministrativo(42L, 7L, request);

        assertThat(saved.getSenha()).isNotEqualTo(BCRYPT_SHAPED_PASSWORD);
        assertThat(BCrypt.verifyer().verify(
                BCRYPT_SHAPED_PASSWORD.toCharArray(),
                saved.getSenha().toCharArray()
        ).verified).isTrue();
    }

    @Test
    void shouldReassignDriverWithoutAcceptingCredentialInput() {
        Company company = new Company("Empresa Segura", "empresa-segura");
        company.setId(7L);
        Motorista motorista = new Motorista(
                "Motorista Seguro",
                "driver.safe@vanrides.dev",
                "51999990012",
                BCRYPT_SHAPED_PASSWORD
        );
        motorista.setId(9L);
        motorista.setCompany(company);
        Passageiro passageiro = buildPassageiro();
        passageiro.setId(45L);
        passageiro.setCompany(company);
        passageiro.setSenha(BCRYPT_SHAPED_PASSWORD.replace("$2a$", "$2b$"));
        passageiro.setLatitude(-30.0);
        passageiro.setLongitude(-51.0);

        when(passageiroRepository.findByIdAndCompanyId(45L, 7L)).thenReturn(Optional.of(passageiro));
        when(loginIdentityService.requireOwnedBy(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                45L
        )).thenReturn(passageiro.getEmail());
        when(motoristaRepository.findByIdAndCompanyId(9L, 7L)).thenReturn(Optional.of(motorista));
        when(motoristaRepository.findById(9L)).thenReturn(Optional.of(motorista));
        when(passageiroRepository.saveAndFlush(passageiro)).thenReturn(passageiro);

        Passageiro saved = passageiroService.reatribuirMotorista(45L, 7L, 9L);

        assertThat(saved.getMotorista()).isSameAs(motorista);
        assertThat(saved.getSenha()).isEqualTo(BCRYPT_SHAPED_PASSWORD.replace("$2a$", "$2b$"));
    }

    @Test
    void shouldRemoveDriverWithoutAcceptingCredentialInput() {
        Passageiro passageiro = buildPassageiro();
        passageiro.setId(46L);
        passageiro.setSenha(BCRYPT_SHAPED_PASSWORD.replace("$2a$", "$2y$"));
        passageiro.setMotorista(new Motorista());
        passageiro.setLatitude(-30.0);
        passageiro.setLongitude(-51.0);

        when(passageiroRepository.findByIdAndCompanyId(46L, 7L)).thenReturn(Optional.of(passageiro));
        when(loginIdentityService.requireOwnedBy(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                46L
        )).thenReturn(passageiro.getEmail());
        when(passageiroRepository.saveAndFlush(passageiro)).thenReturn(passageiro);

        Passageiro saved = passageiroService.removerMotorista(46L, 7L);

        assertThat(saved.getMotorista()).isNull();
        assertThat(saved.getSenha()).isEqualTo(BCRYPT_SHAPED_PASSWORD.replace("$2a$", "$2y$"));
    }
}
