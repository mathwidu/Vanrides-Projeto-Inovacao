package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.MotoristaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MotoristaServiceTest {

    private static final String BCRYPT_SHAPED_PASSWORD =
            "$2a$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u";

    @Mock
    private MotoristaRepository motoristaRepository;

    @Mock
    private CompanyService companyService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private LoginIdentityService loginIdentityService;

    @InjectMocks
    private MotoristaService motoristaService;

    @BeforeEach
    void configureIdentitySeam() {
        lenient().when(loginIdentityService.requireAvailable(any()))
                .thenAnswer(invocation -> normalize(invocation.getArgument(0, String.class)));
        lenient().when(loginIdentityService.requireAvailableFor(any(), any(), any()))
                .thenAnswer(invocation -> normalize(invocation.getArgument(0, String.class)));
        lenient().when(loginIdentityService.requireOwnedBy(any(), any(), any()))
                .thenAnswer(invocation -> normalize(invocation.getArgument(0, String.class)));
        lenient().when(loginIdentityService.executeIdentityWrite(any()))
                .thenAnswer(invocation -> {
                    Supplier<?> write = invocation.getArgument(0);
                    return write.get();
                });
    }

    @Test
    void shouldHashSenhaAndAssignDefaultCompanyWhenSavingMotorista() {
        Company defaultCompany = new Company("VanRides Default", "default");
        Motorista motorista = new Motorista("Motorista Teste", "motorista.service@vanrides.dev", "51999990009", "Secret123");

        when(companyService.getDefaultCompany()).thenReturn(defaultCompany);
        when(motoristaRepository.saveAndFlush(any(Motorista.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Motorista salvo = motoristaService.salvarNovoMotorista(motorista, motorista.getSenha());

        ArgumentCaptor<Motorista> captor = ArgumentCaptor.forClass(Motorista.class);
        verify(motoristaRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getCompany()).isEqualTo(defaultCompany);
        assertThat(captor.getValue().getSenha()).startsWith("$2a$");
        assertThat(captor.getValue().getSenha()).isNotEqualTo("Secret123");
        assertThat(salvo.getCompany()).isEqualTo(defaultCompany);
        verify(loginIdentityService).requireAvailable("motorista.service@vanrides.dev");
        verify(loginIdentityService).executeIdentityWrite(any());
    }

    @Test
    void shouldHashBcryptShapedPasswordForNewDriver() {
        Company defaultCompany = new Company("VanRides Default", "default");
        String ignoredEntityCredential = "EntityCredentialMustBeIgnored!";
        Motorista motorista = new Motorista(
                "Motorista Novo",
                "new.driver@vanrides.dev",
                "51999990008",
                ignoredEntityCredential
        );
        when(companyService.getDefaultCompany()).thenReturn(defaultCompany);
        when(motoristaRepository.saveAndFlush(motorista)).thenReturn(motorista);

        Motorista saved = motoristaService.salvarNovoMotorista(motorista, BCRYPT_SHAPED_PASSWORD);

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

    @ParameterizedTest
    @ValueSource(strings = {
            "$2a$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u",
            "$2b$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u",
            "$2y$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u",
            "RawPassword!123"
    })
    void shouldRejectEveryExistingDriverAtNewDriverBoundary(String credential) {
        Motorista motorista = new Motorista(
                "Motorista Existente",
                "existing.variant@vanrides.dev",
                "51999990007",
                credential
        );
        motorista.setId(43L);

        assertThatThrownBy(() -> motoristaService.salvarNovoMotorista(motorista, credential))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("salvarNovoMotorista aceita apenas motoristas ainda não persistidos.");

        verify(motoristaRepository, never()).saveAndFlush(any(Motorista.class));
        verify(loginIdentityService, never()).executeIdentityWrite(any());
        verify(loginIdentityService, never()).requireAvailable(any());
    }

    @Test
    void shouldValidateOldOwnerAndDestinationBeforeUpdatingPendingDriverEmail() {
        String storedHash = BCRYPT_SHAPED_PASSWORD.replace("$2a$", "$2y$");
        Motorista motorista = new Motorista(
                "Motorista Pendente",
                "old.driver@vanrides.dev",
                "51999990022",
                storedHash
        );
        motorista.setId(22L);
        motorista.setEmailVerified(true);
        motorista.setEmailVerifiedAt(OffsetDateTime.now());
        when(motoristaRepository.findById(22L)).thenReturn(Optional.of(motorista));
        when(motoristaRepository.saveAndFlush(motorista)).thenAnswer(invocation -> {
            assertThat(motorista.isEmailVerified()).isFalse();
            assertThat(motorista.getEmailVerifiedAt()).isNull();
            return motorista;
        });

        motoristaService.updatePendingDriverEmail(22L, " New.Driver@VanRides.dev ");

        var order = inOrder(loginIdentityService, motoristaRepository);
        order.verify(loginIdentityService).requireOwnedBy(
                "old.driver@vanrides.dev",
                UserRole.DRIVER,
                22L
        );
        order.verify(loginIdentityService).requireAvailableFor(
                " New.Driver@VanRides.dev ",
                UserRole.DRIVER,
                22L
        );
        order.verify(loginIdentityService).executeIdentityWrite(any());
        verify(motoristaRepository).saveAndFlush(motorista);
        assertThat(motorista.getEmail()).isEqualTo("new.driver@vanrides.dev");
        assertThat(motorista.getSenha()).isEqualTo(storedHash);
        assertThat(motorista.isEmailVerified()).isFalse();
        assertThat(motorista.getEmailVerifiedAt()).isNull();
    }

    @Test
    void shouldRevokeAccountArtifactsBeforeHardDeletingAndFlushingDriver() {
        Motorista motorista = new Motorista(
                "Motorista Excluído",
                "deleted.driver@vanrides.dev",
                "51999990023",
                "$2a$12$existing.hash"
        );
        motorista.setId(23L);
        when(motoristaRepository.findById(23L)).thenReturn(Optional.of(motorista));

        motoristaService.deletarMotorista(23L);

        var order = inOrder(loginIdentityService, emailVerificationService, motoristaRepository);
        order.verify(loginIdentityService).requireOwnedBy(
                "deleted.driver@vanrides.dev",
                UserRole.DRIVER,
                23L
        );
        order.verify(emailVerificationService).revokeAccountArtifacts(UserRole.DRIVER, 23L);
        order.verify(motoristaRepository).delete(motorista);
        order.verify(motoristaRepository).flush();
    }

    @Test
    void shouldResolveLegacyEmailLookupThroughTheGlobalDriverId() {
        Motorista motorista = new Motorista(
                "Motorista Owner",
                "driver.owner@vanrides.dev",
                "51999990024",
                "$2a$12$existing.hash"
        );
        motorista.setId(24L);
        var owner = new LoginIdentityService.LoginIdentityOwner(
                UserRole.DRIVER,
                24L,
                "driver.owner@vanrides.dev"
        );
        when(loginIdentityService.findOwner(" Driver.Owner@VanRides.dev "))
                .thenReturn(Optional.of(owner));
        when(motoristaRepository.findById(24L)).thenReturn(Optional.of(motorista));

        assertThat(motoristaService.buscarPorEmail(" Driver.Owner@VanRides.dev "))
                .isSameAs(motorista);
        verify(motoristaRepository, never()).findByEmail(any());
    }

    @Test
    void shouldRejectMissingPasswordWithoutWritingTheNewDriver() {
        Motorista motorista = new Motorista(
                "Motorista Sem Senha",
                "no.password@vanrides.dev",
                "51999990025",
                null
        );

        assertThatThrownBy(() -> motoristaService.salvarNovoMotorista(motorista, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("Senha é obrigatória.");
                });

        verify(loginIdentityService).requireAvailable("no.password@vanrides.dev");
        verify(loginIdentityService, never()).executeIdentityWrite(any());
        verify(motoristaRepository, never()).saveAndFlush(any(Motorista.class));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
