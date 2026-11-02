package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.CompanyAdminBootstrapRequest;
import com.vanrides.vanrides.dto.CompanyAdminResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyAdmin;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyAdminService {

    @Autowired
    private CompanyAdminRepository companyAdminRepository;

    @Autowired
    private LoginIdentityService loginIdentityService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Value("${vanrides.qa.auto-verify-email-domains:}")
    private String qaAutoVerifyEmailDomains;

    @Transactional
    public CompanyAdminResponse bootstrapFirstAdmin(CompanyAdminBootstrapRequest request) {
        if (request.getCompanyName() == null || request.getCompanyName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nome da empresa é obrigatório para o primeiro cadastro."
            );
        }
        Company company = companyService.createNewForPublicBootstrap(request.getCompanyName());

        CompanyAdmin existingAdmin = companyAdminRepository.findByCompanyId(company.getId()).orElse(null);
        if (existingAdmin != null) {
            throw companyAlreadyHasAdmin();
        }

        String email = loginIdentityService.requireAvailable(request.getEmail());

        CompanyAdmin admin = new CompanyAdmin();
        admin.setCompany(company);
        admin.setNome(request.getNome().trim());
        admin.setEmail(email);
        admin.setTelefone(request.getTelefone().trim());
        admin.setSenha(hashPassword(request.getSenha()));
        boolean autoVerifyForQa = QaEmailAutoVerification.shouldAutoVerify(email, qaAutoVerifyEmailDomains);
        if (autoVerifyForQa) {
            QaEmailAutoVerification.markVerified(admin);
        }

        CompanyAdmin saved;
        try {
            saved = loginIdentityService.executeIdentityWrite(
                    () -> companyAdminRepository.saveAndFlush(admin));
        } catch (DataIntegrityViolationException exception) {
            if (loginIdentityService.hasConstraint(exception, "uk_company_admin_company")) {
                throw companyAlreadyHasAdmin();
            }
            throw exception;
        }
        if (!autoVerifyForQa) {
            emailVerificationService.markPendingAndRevokeSessions(UserRole.COMPANY_ADMIN, saved.getId());
            emailVerificationService.sendVerificationIfAllowed(UserRole.COMPANY_ADMIN, saved.getId(), saved.getEmail());
        }
        return new CompanyAdminResponse(
                saved.getId(),
                company.getId(),
                company.getSlug(),
                saved.getNome(),
                saved.getEmail(),
                saved.getTelefone(),
                saved.isEmailVerified()
        );
    }

    public CompanyAdmin findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return loginIdentityService.findOwner(email)
                .filter(owner -> owner.role() == UserRole.COMPANY_ADMIN)
                .flatMap(owner -> companyAdminRepository.findById(owner.userId())
                        .filter(admin -> owner.normalizedEmail().equals(admin.getEmail())))
                .orElse(null);
    }

    @Transactional
    public void updatePendingAdminEmail(Long adminId, String newEmail) {
        CompanyAdmin admin = companyAdminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrador não encontrado."));
        loginIdentityService.requireOwnedBy(admin.getEmail(), UserRole.COMPANY_ADMIN, adminId);
        String normalizedEmail = loginIdentityService.requireAvailableFor(
                newEmail,
                UserRole.COMPANY_ADMIN,
                adminId
        );
        admin.setEmail(normalizedEmail);
        EmailVerificationState.markPending(admin);
        loginIdentityService.executeIdentityWrite(
                () -> companyAdminRepository.saveAndFlush(admin));
    }

    private String hashPassword(String rawPassword) {
        return BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());
    }

    private ResponseStatusException companyAlreadyHasAdmin() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Esta empresa já possui um administrador."
        );
    }
}
