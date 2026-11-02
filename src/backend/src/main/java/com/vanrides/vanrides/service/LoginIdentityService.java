package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.model.LoginIdentity;
import com.vanrides.vanrides.repository.LoginIdentityRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class LoginIdentityService {

    private static final Set<String> EMAIL_CONSTRAINTS = Set.of(
            "pk_login_identity_email",
            "uk_company_admin_email",
            "uk_passageiro_email",
            "uk_guardian_company_email",
            "uk_motorista_email"
    );

    private final LoginIdentityRepository repository;
    private final EmailAddressNormalizer normalizer;

    public LoginIdentityService(
            LoginIdentityRepository repository,
            EmailAddressNormalizer normalizer) {
        this.repository = repository;
        this.normalizer = normalizer;
    }

    public Optional<LoginIdentityOwner> findOwner(String rawEmail) {
        return normalizer.tryNormalize(rawEmail)
                .flatMap(repository::findById)
                .map(this::toOwner);
    }

    public LoginIdentityLookup lookupRequired(String rawEmail) {
        String normalizedEmail = normalizer.normalizeRequired(rawEmail);
        Optional<LoginIdentityOwner> owner = repository.findById(normalizedEmail)
                .map(this::toOwner);
        return new LoginIdentityLookup(normalizedEmail, owner);
    }

    public String requireAvailable(String rawEmail) {
        LoginIdentityLookup lookup = lookupRequired(rawEmail);
        if (lookup.owner().isPresent()) {
            throw new LoginIdentityConflictException();
        }
        return lookup.normalizedEmail();
    }

    public String requireAvailableFor(String rawEmail, UserRole role, Long userId) {
        requireOwnerCoordinates(role, userId);
        String normalizedEmail = normalizer.normalizeRequired(rawEmail);
        repository.findById(normalizedEmail).ifPresent(identity -> {
            if (identity.getUserRole() != role || !identity.getUserId().equals(userId)) {
                throw new LoginIdentityConflictException();
            }
        });
        return normalizedEmail;
    }

    public String requireOwnedBy(String rawEmail, UserRole role, Long userId) {
        requireOwnerCoordinates(role, userId);
        String normalizedEmail = normalizer.normalizeRequired(rawEmail);
        if (!isOwnedByNormalized(normalizedEmail, role, userId)) {
            throw new IllegalStateException("Identidade global de login inconsistente.");
        }
        return normalizedEmail;
    }

    public boolean isOwnedBy(String rawEmail, UserRole role, Long userId) {
        if (role == null || userId == null) {
            return false;
        }
        return normalizer.tryNormalize(rawEmail)
                .map(email -> isOwnedByNormalized(email, role, userId))
                .orElse(false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> lockExactOwnerForDeviceRegistration(
            String rawEmail,
            UserRole role,
            Long userId) {
        return lockExactOwner(rawEmail, role, userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> lockExactOwnerForEmailVerification(
            String rawEmail,
            UserRole role,
            Long userId) {
        return lockExactOwner(rawEmail, role, userId);
    }

    private Optional<String> lockExactOwner(
            String rawEmail,
            UserRole role,
            Long userId) {
        if (role == null || userId == null) {
            return Optional.empty();
        }

        return normalizer.tryNormalize(rawEmail)
                .flatMap(normalizedEmail -> repository.findExactOwnerForKeyShare(
                                normalizedEmail,
                                role.claimValue(),
                                userId
                        )
                        .map(ignored -> normalizedEmail));
    }

    public <T> T executeIdentityWrite(Supplier<T> write) {
        try {
            return write.get();
        } catch (DataIntegrityViolationException exception) {
            if (EMAIL_CONSTRAINTS.stream().anyMatch(name -> hasConstraint(exception, name))) {
                throw new LoginIdentityConflictException();
            }
            throw exception;
        }
    }

    public boolean hasConstraint(DataIntegrityViolationException exception, String constraintName) {
        if (exception == null || constraintName == null || constraintName.isBlank()) {
            return false;
        }

        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && constraintName.equals(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private LoginIdentityOwner toOwner(LoginIdentity identity) {
        return new LoginIdentityOwner(
                identity.getUserRole(),
                identity.getUserId(),
                identity.getNormalizedEmail()
        );
    }

    private boolean isOwnedByNormalized(String normalizedEmail, UserRole role, Long userId) {
        return repository.findById(normalizedEmail)
                .filter(identity -> identity.getUserRole() == role)
                .filter(identity -> identity.getUserId().equals(userId))
                .isPresent();
    }

    private void requireOwnerCoordinates(UserRole role, Long userId) {
        if (role == null || userId == null) {
            throw new IllegalArgumentException("Papel e ID da identidade são obrigatórios.");
        }
    }

    public record LoginIdentityOwner(
            UserRole role,
            Long userId,
            String normalizedEmail) {
    }

    public record LoginIdentityLookup(
            String normalizedEmail,
            Optional<LoginIdentityOwner> owner) {
    }
}
