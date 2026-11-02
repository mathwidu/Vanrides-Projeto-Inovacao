package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.model.LoginIdentity;
import com.vanrides.vanrides.repository.LoginIdentityRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginIdentityServiceTest {

    @Mock
    private LoginIdentityRepository repository;

    private LoginIdentityService service;

    @BeforeEach
    void setUp() {
        service = new LoginIdentityService(repository, new EmailAddressNormalizer());
    }

    @Test
    void shouldResolveOneOwnerByCanonicalEmail() {
        LoginIdentity identity = identityWithEmail(UserRole.GUARDIAN, 42L, "guardian@example.com");
        when(repository.findById("guardian@example.com")).thenReturn(Optional.of(identity));

        Optional<LoginIdentityService.LoginIdentityOwner> owner =
                service.findOwner("  GUARDIAN@EXAMPLE.COM ");

        assertThat(owner).contains(new LoginIdentityService.LoginIdentityOwner(
                UserRole.GUARDIAN,
                42L,
                "guardian@example.com"
        ));
    }

    @Test
    void shouldTreatInvalidLookupAsAbsentWithoutQueryingRepository() {
        assertThat(service.findOwner("invalid email")).isEmpty();

        verify(repository, never()).findById(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldNormalizeAndResolveAnOptionalOwnerWithOneRequiredLookup() {
        LoginIdentity identity = identityWithEmail(UserRole.DRIVER, 11L, "driver@example.com");
        when(repository.findById("driver@example.com")).thenReturn(Optional.of(identity));

        LoginIdentityService.LoginIdentityLookup lookup =
                service.lookupRequired("  DRIVER@EXAMPLE.COM ");

        assertThat(lookup.normalizedEmail()).isEqualTo("driver@example.com");
        assertThat(lookup.owner()).contains(new LoginIdentityService.LoginIdentityOwner(
                UserRole.DRIVER,
                11L,
                "driver@example.com"
        ));
        verify(repository).findById("driver@example.com");
    }

    @Test
    void shouldAllowOnlyTheCurrentOwnerToReuseAnEmail() {
        LoginIdentity identity = identity(UserRole.PASSENGER, 7L);
        when(repository.findById("owner@example.com")).thenReturn(Optional.of(identity));

        assertThat(service.requireAvailableFor("OWNER@example.com", UserRole.PASSENGER, 7L))
                .isEqualTo("owner@example.com");
        assertThatThrownBy(() -> service.requireAvailableFor(
                "owner@example.com",
                UserRole.DRIVER,
                9L
        )).isInstanceOf(LoginIdentityConflictException.class);
    }

    @Test
    void shouldRejectAnExistingOwnerForNewAccountWithoutDisclosingRole() {
        LoginIdentity identity = mock(LoginIdentity.class);
        when(repository.findById("taken@example.com")).thenReturn(Optional.of(identity));

        assertThatThrownBy(() -> service.requireAvailable("taken@example.com"))
                .isInstanceOfSatisfying(LoginIdentityConflictException.class, exception -> {
                    assertThat(exception.getStatusCode().value()).isEqualTo(409);
                    assertThat(exception.getApiCode()).isEqualTo("EMAIL_ALREADY_IN_USE");
                    assertThat(exception.getReason()).isEqualTo("O e-mail informado já está em uso.");
                    assertThat(exception.getReason()).doesNotContain("COMPANY_ADMIN", "3");
                });
    }

    @Test
    void shouldTranslateOnlyKnownEmailConstraints() {
        for (String constraint : List.of(
                "pk_login_identity_email",
                "uk_company_admin_email",
                "uk_passageiro_email",
                "uk_guardian_company_email",
                "uk_motorista_email")) {
            DataIntegrityViolationException conflict = integrityViolation(constraint);

            assertThatThrownBy(() -> service.executeIdentityWrite(() -> {
                throw conflict;
            })).as(constraint).isInstanceOf(LoginIdentityConflictException.class);
        }
    }

    @Test
    void shouldReturnTheIdentityWriteResult() {
        assertThat(service.executeIdentityWrite(() -> "saved")).isEqualTo("saved");
    }

    @Test
    void shouldPreserveUnrelatedIntegrityFailures() {
        DataIntegrityViolationException unrelated = integrityViolation("uk_company_admin_company");

        assertThatThrownBy(() -> service.executeIdentityWrite(() -> {
            throw unrelated;
        })).isSameAs(unrelated);
    }

    @Test
    void shouldFailClosedOnDuplicateOwnerRegistryDrift() {
        DataIntegrityViolationException ownerDrift = integrityViolation("uk_login_identity_owner");

        assertThatThrownBy(() -> service.executeIdentityWrite(() -> {
            throw ownerDrift;
        })).isSameAs(ownerDrift);
    }

    @Test
    void shouldNotTrustConstraintNamesParsedFromFreeFormMessages() {
        DataIntegrityViolationException messageOnly = new DataIntegrityViolationException(
                "duplicate constraint \"pk_login_identity_email\""
        );

        assertThatThrownBy(() -> service.executeIdentityWrite(() -> {
            throw messageOnly;
        })).isSameAs(messageOnly);
    }

    @Test
    void shouldRecognizeAConstraintWithoutTranslatingIt() {
        DataIntegrityViolationException failure = integrityViolation("uk_company_admin_company");

        assertThat(service.hasConstraint(failure, "uk_company_admin_company")).isTrue();
        assertThat(service.hasConstraint(failure, "pk_login_identity_email")).isFalse();
    }

    private LoginIdentity identity(UserRole role, Long userId) {
        LoginIdentity identity = mock(LoginIdentity.class);
        when(identity.getUserRole()).thenReturn(role);
        when(identity.getUserId()).thenReturn(userId);
        return identity;
    }

    private LoginIdentity identityWithEmail(UserRole role, Long userId, String email) {
        LoginIdentity identity = identity(role, userId);
        when(identity.getNormalizedEmail()).thenReturn(email);
        return identity;
    }

    private DataIntegrityViolationException integrityViolation(String constraintName) {
        ConstraintViolationException hibernate = new ConstraintViolationException(
                "constraint failure",
                new SQLException("duplicate key", "23505"),
                "insert",
                constraintName
        );
        return new DataIntegrityViolationException("write failed", hibernate);
    }
}
