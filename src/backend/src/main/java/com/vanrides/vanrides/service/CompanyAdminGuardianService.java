package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminGuardianSummaryResponse;
import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminPassageiroLinkedSummaryResponse;
import com.vanrides.vanrides.dto.AdminResponsavelCreateRequest;
import com.vanrides.vanrides.dto.AdminResponsavelDetailResponse;
import com.vanrides.vanrides.dto.AdminResponsavelSummaryResponse;
import com.vanrides.vanrides.dto.AdminResponsavelUpdateRequest;
import com.vanrides.vanrides.dto.GuardianPasswordChangeRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.GuardianPassageiro;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.GuardianPassageiroRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyAdminGuardianService {

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private GuardianPassageiroRepository guardianPassageiroRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private LoginIdentityService loginIdentityService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Value("${vanrides.qa.auto-verify-email-domains:}")
    private String qaAutoVerifyEmailDomains;

    public List<AdminResponsavelSummaryResponse> listarResponsaveis(Long companyId) {
        return guardianRepository.findAllByCompanyIdOrderByNomeAsc(companyId)
                .stream()
                .map(guardian -> new AdminResponsavelSummaryResponse(
                        guardian.getId(),
                        guardian.getNome(),
                        guardian.getEmail(),
                        guardian.getTelefone(),
                        guardianPassageiroRepository.countByGuardianIdAndCompanyId(guardian.getId(), companyId),
                        isAccessConfigured(guardian),
                        guardian.isEmailVerified(),
                        guardian.getDataNascimento(),
                        guardian.getFotoPerfilUrl()
                ))
                .toList();
    }

    public Optional<AdminResponsavelDetailResponse> buscarDetalhe(Long guardianId, Long companyId) {
        return guardianRepository.findByIdAndCompanyId(guardianId, companyId)
                .map(guardian -> toDetail(guardian, companyId));
    }

    @Transactional
    public AdminResponsavelDetailResponse criarResponsavel(Long companyId, AdminResponsavelCreateRequest request) {
        String email = loginIdentityService.requireAvailable(request.getEmail());

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));

        Guardian guardian = new Guardian();
        guardian.setCompany(company);
        guardian.setNome(request.getNome().trim());
        guardian.setEmail(email);
        guardian.setTelefone(request.getTelefone().trim());
        guardian.setDataNascimento(request.getDataNascimento());
        applyPasswordIfPresent(guardian, request.getSenha());

        Guardian saved = saveGuardian(guardian);
        if (isAccessConfigured(saved)) {
            if (QaEmailAutoVerification.shouldAutoVerify(email, qaAutoVerifyEmailDomains)) {
                QaEmailAutoVerification.markVerified(saved);
            } else {
                emailVerificationService.markPendingAndRevokeSessions(UserRole.GUARDIAN, saved.getId());
                emailVerificationService.sendVerificationIfAllowed(UserRole.GUARDIAN, saved.getId(), saved.getEmail());
            }
        }
        return toDetail(saved, companyId);
    }

    @Transactional
    public AdminResponsavelDetailResponse atualizarResponsavel(Long guardianId,
                                                              Long companyId,
                                                              AdminResponsavelUpdateRequest request) {
        Guardian guardian = guardianRepository.findByIdAndCompanyId(guardianId, companyId)
                .orElseThrow(this::notFoundGuardian);
        boolean hadAccessConfigured = isAccessConfigured(guardian);

        String currentEmail = loginIdentityService.requireOwnedBy(
                guardian.getEmail(),
                UserRole.GUARDIAN,
                guardianId
        );
        String email = loginIdentityService.requireAvailableFor(
                request.getEmail(),
                UserRole.GUARDIAN,
                guardianId
        );
        boolean emailChanged = !currentEmail.equals(email);

        guardian.setNome(request.getNome().trim());
        guardian.setEmail(email);
        guardian.setTelefone(request.getTelefone().trim());
        guardian.setDataNascimento(request.getDataNascimento());
        applyPasswordIfPresent(guardian, request.getSenha());
        if (emailChanged) {
            markEmailPending(guardian);
        }

        Guardian saved = saveGuardian(guardian);
        boolean shouldStartVerification = isAccessConfigured(saved) && (emailChanged || !saved.isEmailVerified() || !hadAccessConfigured);
        if (shouldStartVerification) {
            if (QaEmailAutoVerification.shouldAutoVerify(email, qaAutoVerifyEmailDomains)) {
                QaEmailAutoVerification.markVerified(saved);
            } else {
                emailVerificationService.markPendingAndRevokeSessions(UserRole.GUARDIAN, saved.getId());
                emailVerificationService.sendVerificationIfAllowed(UserRole.GUARDIAN, saved.getId(), saved.getEmail());
            }
        }
        return toDetail(saved, companyId);
    }

    public void atualizarMinhaSenha(Long guardianId,
                                    Long companyId,
                                    GuardianPasswordChangeRequest request) {
        Guardian guardian = guardianRepository.findByIdAndCompanyId(guardianId, companyId)
                .orElseThrow(this::notFoundGuardian);

        if (!isAccessConfigured(guardian)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O responsável ainda não possui acesso configurado.");
        }

        if (!BCrypt.verifyer().verify(request.getSenhaAtual().toCharArray(), guardian.getSenha().toCharArray()).verified) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha atual inválida.");
        }

        guardian.setSenha(hashPassword(request.getNovaSenha()));
        guardianRepository.save(guardian);
    }

    @Transactional
    public void updatePendingGuardianEmail(Long guardianId, String newEmail) {
        Guardian guardian = guardianRepository.findById(guardianId)
                .orElseThrow(this::notFoundGuardian);
        loginIdentityService.requireOwnedBy(guardian.getEmail(), UserRole.GUARDIAN, guardianId);
        String normalizedEmail = loginIdentityService.requireAvailableFor(
                newEmail,
                UserRole.GUARDIAN,
                guardianId
        );
        guardian.setEmail(normalizedEmail);
        markEmailPending(guardian);
        saveGuardian(guardian);
    }

    @Transactional
    public void removerResponsavel(Long guardianId, Long companyId) {
        Guardian guardian = guardianRepository.findByIdAndCompanyId(guardianId, companyId)
                .orElseThrow(this::notFoundGuardian);

        long linkedPassengers = guardianPassageiroRepository.countByGuardianIdAndCompanyId(guardianId, companyId);
        if (linkedPassengers > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "O responsável possui passageiros vinculados. Remova os vínculos antes de excluir."
            );
        }

        loginIdentityService.requireOwnedBy(guardian.getEmail(), UserRole.GUARDIAN, guardianId);
        emailVerificationService.revokeAccountArtifacts(UserRole.GUARDIAN, guardianId);
        guardianRepository.delete(guardian);
        guardianRepository.flush();
    }

    private Guardian saveGuardian(Guardian guardian) {
        return loginIdentityService.executeIdentityWrite(
                () -> guardianRepository.saveAndFlush(guardian));
    }

    private void markEmailPending(Guardian guardian) {
        EmailVerificationState.markPending(guardian);
        guardian.setEmailVerificationLastSentAt(null);
    }

    private AdminResponsavelDetailResponse toDetail(Guardian guardian, Long companyId) {
        List<AdminPassageiroLinkedSummaryResponse> passageiros = guardianPassageiroRepository
                .findAllWithPassageiroByGuardianIdAndCompanyId(guardian.getId(), companyId)
                .stream()
                .map(GuardianPassageiro::getPassageiro)
                .map(this::toPassageiroLinkedSummary)
                .toList();

        return new AdminResponsavelDetailResponse(
                guardian.getId(),
                guardian.getNome(),
                guardian.getEmail(),
                guardian.getTelefone(),
                isAccessConfigured(guardian),
                passageiros,
                guardian.isEmailVerified(),
                guardian.getDataNascimento(),
                guardian.getFotoPerfilUrl()
        );
    }

    private AdminPassageiroLinkedSummaryResponse toPassageiroLinkedSummary(Passageiro passageiro) {
        return new AdminPassageiroLinkedSummaryResponse(
                passageiro.getId(),
                passageiro.getNome(),
                passageiro.getEmail(),
                passageiro.isMenorDeIdade(),
                toMotoristaSummary(passageiro.getMotorista()),
                passageiro.getDataNascimento(),
                passageiro.getFotoPerfilUrl()
        );
    }

    private AdminMotoristaSummaryResponse toMotoristaSummary(Motorista motorista) {
        if (motorista == null) {
            return null;
        }
        return new AdminMotoristaSummaryResponse(
                motorista.getId(),
                motorista.getNome(),
                motorista.getEmail(),
                motorista.getTelefone(),
                motorista.isEmailVerified(),
                motorista.getDataNascimento(),
                motorista.getFotoPerfilUrl()
        );
    }

    private ResponseStatusException notFoundGuardian() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável não encontrado na empresa atual.");
    }

    private void applyPasswordIfPresent(Guardian guardian, String rawPassword) {
        if (rawPassword == null) {
            return;
        }

        if (rawPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha inválida para o responsável.");
        }

        if (rawPassword.trim().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha do responsável deve ter pelo menos 8 caracteres.");
        }

        guardian.setSenha(hashPassword(rawPassword));
    }

    private boolean isAccessConfigured(Guardian guardian) {
        return guardian.getSenha() != null && !guardian.getSenha().isBlank();
    }

    private String hashPassword(String rawPassword) {
        return BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());
    }
}
