package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.model.CompanyAdmin;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class LoginUserDirectoryService {

    private final CompanyAdminRepository companyAdminRepository;
    private final PassageiroRepository passageiroRepository;
    private final GuardianRepository guardianRepository;
    private final MotoristaRepository motoristaRepository;
    private final LoginIdentityService loginIdentityService;
    private final EntityManager entityManager;

    public LoginUserDirectoryService(
            CompanyAdminRepository companyAdminRepository,
            PassageiroRepository passageiroRepository,
            GuardianRepository guardianRepository,
            MotoristaRepository motoristaRepository,
            LoginIdentityService loginIdentityService,
            EntityManager entityManager) {
        this.companyAdminRepository = companyAdminRepository;
        this.passageiroRepository = passageiroRepository;
        this.guardianRepository = guardianRepository;
        this.motoristaRepository = motoristaRepository;
        this.loginIdentityService = loginIdentityService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public LoginUserSnapshot findByEmail(String email) {
        return loginIdentityService.findOwner(email)
                .flatMap(this::findSnapshotForOwner)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public LoginCredentialSnapshot findCredentialsByEmail(String email) {
        return loginIdentityService.findOwner(email)
                .flatMap(this::findCredentialsForOwner)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public LoginUserSnapshot requireByRoleAndId(UserRole role, Long userId) {
        return findByRoleAndId(role, userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário autenticável não encontrado."));
    }

    @Transactional(readOnly = true)
    public Optional<LoginUserSnapshot> findByRoleAndId(UserRole role, Long userId) {
        if (role == null || userId == null) {
            return Optional.empty();
        }

        return loadSnapshot(role, userId)
                .filter(snapshot -> loginIdentityService.isOwnedBy(
                        snapshot.email(),
                        snapshot.role(),
                        snapshot.userId()
                ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LoginUserSnapshot> lockAccountByRoleAndId(UserRole role, Long userId) {
        if (role == null || userId == null) {
            return Optional.empty();
        }

        return switch (role) {
            case COMPANY_ADMIN -> findLocked(CompanyAdmin.class, userId).map(this::toSnapshot);
            case PASSENGER -> findLocked(Passageiro.class, userId).map(this::toSnapshot);
            case GUARDIAN -> findLocked(Guardian.class, userId).map(this::toSnapshot);
            case DRIVER -> findLocked(Motorista.class, userId).map(this::toSnapshot);
        };
    }

    @Transactional
    public void markPending(UserRole role, Long userId) {
        requireByRoleAndId(role, userId);
        switch (role) {
            case COMPANY_ADMIN -> companyAdminRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markPending(account);
                companyAdminRepository.save(account);
            });
            case PASSENGER -> passageiroRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markPending(account);
                passageiroRepository.save(account);
            });
            case GUARDIAN -> guardianRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markPending(account);
                guardianRepository.save(account);
            });
            case DRIVER -> motoristaRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markPending(account);
                motoristaRepository.save(account);
            });
        }
    }

    @Transactional
    public void recordVerificationEmailSent(UserRole role, Long userId, OffsetDateTime sentAt) {
        requireByRoleAndId(role, userId);
        switch (role) {
            case COMPANY_ADMIN -> companyAdminRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markEmailSent(account, sentAt);
                companyAdminRepository.save(account);
            });
            case PASSENGER -> passageiroRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markEmailSent(account, sentAt);
                passageiroRepository.save(account);
            });
            case GUARDIAN -> guardianRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markEmailSent(account, sentAt);
                guardianRepository.save(account);
            });
            case DRIVER -> motoristaRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markEmailSent(account, sentAt);
                motoristaRepository.save(account);
            });
        }
    }

    @Transactional
    public void markVerified(UserRole role, Long userId, OffsetDateTime verifiedAt) {
        requireByRoleAndId(role, userId);
        switch (role) {
            case COMPANY_ADMIN -> companyAdminRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markVerified(account, verifiedAt);
                companyAdminRepository.save(account);
            });
            case PASSENGER -> passageiroRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markVerified(account, verifiedAt);
                passageiroRepository.save(account);
            });
            case GUARDIAN -> guardianRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markVerified(account, verifiedAt);
                guardianRepository.save(account);
            });
            case DRIVER -> motoristaRepository.findById(userId).ifPresent(account -> {
                EmailVerificationState.markVerified(account, verifiedAt);
                motoristaRepository.save(account);
            });
        }
    }

    public boolean isAccessConfigured(Guardian guardian) {
        return guardian.getSenha() != null && !guardian.getSenha().isBlank();
    }

    private Optional<LoginUserSnapshot> findSnapshotForOwner(LoginIdentityService.LoginIdentityOwner owner) {
        return loadSnapshot(owner.role(), owner.userId())
                .filter(snapshot -> owner.normalizedEmail().equals(snapshot.email()));
    }

    private Optional<LoginCredentialSnapshot> findCredentialsForOwner(LoginIdentityService.LoginIdentityOwner owner) {
        return loadCredentials(owner.role(), owner.userId())
                .filter(snapshot -> owner.normalizedEmail().equals(snapshot.email()));
    }

    private Optional<LoginUserSnapshot> loadSnapshot(UserRole role, Long userId) {
        return switch (role) {
            case COMPANY_ADMIN -> companyAdminRepository.findById(userId).map(this::toSnapshot);
            case PASSENGER -> passageiroRepository.findById(userId).map(this::toSnapshot);
            case GUARDIAN -> guardianRepository.findById(userId).map(this::toSnapshot);
            case DRIVER -> motoristaRepository.findById(userId).map(this::toSnapshot);
        };
    }

    private Optional<LoginCredentialSnapshot> loadCredentials(UserRole role, Long userId) {
        return switch (role) {
            case COMPANY_ADMIN -> companyAdminRepository.findById(userId).map(this::toCredentials);
            case PASSENGER -> passageiroRepository.findById(userId).map(this::toCredentials);
            case GUARDIAN -> guardianRepository.findById(userId).map(this::toCredentials);
            case DRIVER -> motoristaRepository.findById(userId).map(this::toCredentials);
        };
    }

    private <T> Optional<T> findLocked(Class<T> accountType, Long userId) {
        return Optional.ofNullable(entityManager.find(
                accountType,
                userId,
                LockModeType.PESSIMISTIC_WRITE
        ));
    }

    private LoginUserSnapshot toSnapshot(CompanyAdmin account) {
        return snapshot(account.getId(), UserRole.COMPANY_ADMIN, account.getEmail(), companyId(account.getCompany()),
                account.isEmailVerified(), true, account.getEmailVerificationLastSentAt());
    }

    private LoginUserSnapshot toSnapshot(Passageiro account) {
        return snapshot(account.getId(), UserRole.PASSENGER, account.getEmail(), companyId(account.getCompany()),
                account.isEmailVerified(), true, account.getEmailVerificationLastSentAt());
    }

    private LoginUserSnapshot toSnapshot(Guardian account) {
        return snapshot(account.getId(), UserRole.GUARDIAN, account.getEmail(), companyId(account.getCompany()),
                account.isEmailVerified(), isAccessConfigured(account), account.getEmailVerificationLastSentAt());
    }

    private LoginUserSnapshot toSnapshot(Motorista account) {
        return snapshot(account.getId(), UserRole.DRIVER, account.getEmail(), companyId(account.getCompany()),
                account.isEmailVerified(), true, account.getEmailVerificationLastSentAt());
    }

    private LoginUserSnapshot snapshot(
            Long userId,
            UserRole role,
            String email,
            Long companyId,
            boolean emailVerified,
            boolean accessConfigured,
            OffsetDateTime lastSentAt) {
        return new LoginUserSnapshot(userId, role, email, companyId, emailVerified, accessConfigured, lastSentAt);
    }

    private LoginCredentialSnapshot toCredentials(CompanyAdmin account) {
        return credentials(account.getId(), UserRole.COMPANY_ADMIN, account.getEmail(), account.getSenha(),
                account.getNome(), null, companyId(account.getCompany()), true);
    }

    private LoginCredentialSnapshot toCredentials(Passageiro account) {
        Long motoristaId = account.getMotorista() != null ? account.getMotorista().getId() : null;
        return credentials(account.getId(), UserRole.PASSENGER, account.getEmail(), account.getSenha(),
                account.getNome(), motoristaId, companyId(account.getCompany()), true);
    }

    private LoginCredentialSnapshot toCredentials(Guardian account) {
        return credentials(account.getId(), UserRole.GUARDIAN, account.getEmail(), account.getSenha(),
                account.getNome(), null, companyId(account.getCompany()), isAccessConfigured(account));
    }

    private LoginCredentialSnapshot toCredentials(Motorista account) {
        return credentials(account.getId(), UserRole.DRIVER, account.getEmail(), account.getSenha(),
                account.getNome(), null, companyId(account.getCompany()), true);
    }

    private LoginCredentialSnapshot credentials(
            Long userId,
            UserRole role,
            String email,
            String passwordHash,
            String name,
            Long motoristaId,
            Long companyId,
            boolean accessConfigured) {
        return new LoginCredentialSnapshot(
                userId,
                role,
                email,
                passwordHash,
                name,
                motoristaId,
                companyId,
                accessConfigured
        );
    }

    private Long companyId(com.vanrides.vanrides.model.Company company) {
        return company != null ? company.getId() : null;
    }

    public record LoginUserSnapshot(
            Long userId,
            UserRole role,
            String email,
            Long companyId,
            boolean emailVerified,
            boolean accessConfigured,
            OffsetDateTime emailVerificationLastSentAt) {
    }

    public record LoginCredentialSnapshot(
            Long userId,
            UserRole role,
            String email,
            String passwordHash,
            String name,
            Long motoristaId,
            Long companyId,
            boolean accessConfigured) {
    }
}
