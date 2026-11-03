package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class LoginUserDirectoryServiceTest {

    @Mock
    private CompanyAdminRepository companyAdminRepository;
    @Mock
    private PassageiroRepository passageiroRepository;
    @Mock
    private GuardianRepository guardianRepository;
    @Mock
    private MotoristaRepository motoristaRepository;
    @Mock
    private LoginIdentityService loginIdentityService;
    @Mock
    private EntityManager entityManager;

    private LoginUserDirectoryService directory;

    @BeforeEach
    void setUp() {
        directory = new LoginUserDirectoryService(
                companyAdminRepository,
                passageiroRepository,
                guardianRepository,
                motoristaRepository,
                loginIdentityService,
                entityManager
        );
    }

    @Test
    void shouldLoadOnlyTheRoleAndIdSelectedByGlobalIdentity() {
        Guardian guardian = guardian(42L, "guardian@example.com", "hash", 8L);
        when(loginIdentityService.findOwner("GUARDIAN@example.com"))
                .thenReturn(Optional.of(new LoginIdentityService.LoginIdentityOwner(
                        UserRole.GUARDIAN,
                        42L,
                        "guardian@example.com"
                )));
        when(guardianRepository.findById(42L)).thenReturn(Optional.of(guardian));

        LoginUserDirectoryService.LoginUserSnapshot snapshot =
                directory.findByEmail("GUARDIAN@example.com");

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.role()).isEqualTo(UserRole.GUARDIAN);
        assertThat(snapshot.userId()).isEqualTo(42L);
        assertThat(snapshot.email()).isEqualTo("guardian@example.com");
        verify(companyAdminRepository, never()).findById(anyLong());
        verify(passageiroRepository, never()).findById(anyLong());
        verify(motoristaRepository, never()).findById(anyLong());
    }

    @Test
    void shouldFailClosedWhenRegistryOwnerAndAccountEmailDiverge() {
        Guardian guardian = guardian(42L, "different@example.com", "hash", 8L);
        when(loginIdentityService.findOwner("owner@example.com"))
                .thenReturn(Optional.of(new LoginIdentityService.LoginIdentityOwner(
                        UserRole.GUARDIAN,
                        42L,
                        "owner@example.com"
                )));
        when(guardianRepository.findById(42L)).thenReturn(Optional.of(guardian));

        assertThat(directory.findByEmail("owner@example.com")).isNull();
    }

    @Test
    void shouldNotProbeAccountTablesWhenIdentityDoesNotExist() {
        when(loginIdentityService.findOwner("missing@example.com")).thenReturn(Optional.empty());

        assertThat(directory.findByEmail("missing@example.com")).isNull();

        verify(companyAdminRepository, never()).findById(anyLong());
        verify(passageiroRepository, never()).findById(anyLong());
        verify(guardianRepository, never()).findById(anyLong());
        verify(motoristaRepository, never()).findById(anyLong());
    }

    @Test
    void shouldRejectRoleAndIdLookupWhenRegistryDoesNotOwnAccountEmail() {
        Passageiro passageiro = mock(Passageiro.class);
        when(passageiro.getId()).thenReturn(7L);
        when(passageiro.getEmail()).thenReturn("passenger@example.com");
        when(passageiroRepository.findById(7L)).thenReturn(Optional.of(passageiro));
        when(loginIdentityService.isOwnedBy("passenger@example.com", UserRole.PASSENGER, 7L))
                .thenReturn(false);

        assertThat(directory.findByRoleAndId(UserRole.PASSENGER, 7L)).isEmpty();
    }

    @Test
    void shouldLockTheAccountBeforeReturningItsConfirmationSnapshot() {
        Passageiro passageiro = mock(Passageiro.class);
        when(passageiro.getId()).thenReturn(7L);
        when(passageiro.getEmail()).thenReturn("passenger@example.com");
        when(entityManager.find(Passageiro.class, 7L, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(passageiro);

        assertThat(directory.lockAccountByRoleAndId(UserRole.PASSENGER, 7L))
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.userId()).isEqualTo(7L);
                    assertThat(snapshot.role()).isEqualTo(UserRole.PASSENGER);
                    assertThat(snapshot.email()).isEqualTo("passenger@example.com");
                });

        verify(entityManager).find(Passageiro.class, 7L, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void shouldNotMutateVerificationStateWhenRegistryOwnershipIsMissing() {
        Passageiro passageiro = mock(Passageiro.class);
        when(passageiro.getId()).thenReturn(7L);
        when(passageiro.getEmail()).thenReturn("passenger@example.com");
        when(passageiroRepository.findById(7L)).thenReturn(Optional.of(passageiro));
        when(loginIdentityService.isOwnedBy("passenger@example.com", UserRole.PASSENGER, 7L))
                .thenReturn(false);

        assertThatThrownBy(() -> directory.markPending(UserRole.PASSENGER, 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(passageiroRepository, never()).save(passageiro);
    }

    @Test
    void shouldExposeCredentialsOnlyForTheResolvedOwner() {
        Guardian guardian = guardian(42L, "guardian@example.com", "password-hash", 8L);
        when(loginIdentityService.findOwner("guardian@example.com"))
                .thenReturn(Optional.of(new LoginIdentityService.LoginIdentityOwner(
                        UserRole.GUARDIAN,
                        42L,
                        "guardian@example.com"
                )));
        when(guardianRepository.findById(42L)).thenReturn(Optional.of(guardian));

        LoginUserDirectoryService.LoginCredentialSnapshot credentials =
                directory.findCredentialsByEmail("guardian@example.com");

        assertThat(credentials).isNotNull();
        assertThat(credentials.role()).isEqualTo(UserRole.GUARDIAN);
        assertThat(credentials.passwordHash()).isEqualTo("password-hash");
        assertThat(credentials.companyId()).isEqualTo(8L);
        assertThat(credentials.accessConfigured()).isTrue();
    }

    private Guardian guardian(Long id, String email, String passwordHash, Long companyId) {
        Guardian guardian = mock(Guardian.class);
        Company company = mock(Company.class);
        when(guardian.getId()).thenReturn(id);
        when(guardian.getEmail()).thenReturn(email);
        when(guardian.getSenha()).thenReturn(passwordHash);
        lenient().when(guardian.getNome()).thenReturn("Guardian");
        when(guardian.getCompany()).thenReturn(company);
        when(company.getId()).thenReturn(companyId);
        return guardian;
    }
}
