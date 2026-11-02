package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.service.LoginUserDirectoryService.LoginUserSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PendingEmailChangeService {

    @Autowired
    private AuthService authService;

    @Autowired
    private CompanyAdminService companyAdminService;

    @Autowired
    private PassageiroService passageiroService;

    @Autowired
    private MotoristaService motoristaService;

    @Autowired
    private CompanyAdminGuardianService companyAdminGuardianService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private LoginUserDirectoryService loginUserDirectoryService;

    @Autowired
    private LoginIdentityService loginIdentityService;

    @Transactional
    public String changePendingEmail(String email, String senha, String newEmail) {
        if (email == null || email.isBlank() || senha == null || senha.isBlank() || newEmail == null || newEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email atual, senha e novo e-mail são obrigatórios.");
        }

        AuthenticatedUser authenticatedUser = authService.authenticate(email, senha);
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        }

        LoginUserSnapshot snapshot = loginUserDirectoryService.requireByRoleAndId(authenticatedUser.getRoleEnum(), authenticatedUser.getUserId());
        if (snapshot.emailVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta conta já foi verificada e não pode usar esta operação.");
        }

        UserRole role = authenticatedUser.getRoleEnum();
        Long userId = authenticatedUser.getUserId();
        String normalizedNewEmail = loginIdentityService.requireAvailableFor(newEmail, role, userId);

        switch (role) {
            case COMPANY_ADMIN -> companyAdminService.updatePendingAdminEmail(userId, normalizedNewEmail);
            case PASSENGER -> passageiroService.updatePendingPassengerEmail(userId, normalizedNewEmail);
            case DRIVER -> motoristaService.updatePendingDriverEmail(userId, normalizedNewEmail);
            case GUARDIAN -> companyAdminGuardianService.updatePendingGuardianEmail(userId, normalizedNewEmail);
        }

        emailVerificationService.markPendingAndRevokeSessions(role, userId);
        emailVerificationService.sendVerificationIfAllowed(role, userId, normalizedNewEmail);
        return normalizedNewEmail;
    }
}
