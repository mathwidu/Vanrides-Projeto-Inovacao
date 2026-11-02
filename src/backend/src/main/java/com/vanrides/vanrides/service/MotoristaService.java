package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.PublicDriverRegistrationRequest;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.MotoristaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import at.favre.lib.crypto.bcrypt.BCrypt;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class MotoristaService {

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private LoginIdentityService loginIdentityService;

    @Value("${vanrides.qa.auto-verify-email-domains:}")
    private String qaAutoVerifyEmailDomains;

    @Transactional
    public Motorista salvarNovoMotorista(Motorista motorista, String rawPassword) {
        return persistirNovoMotorista(motorista, rawPassword, true);
    }

    private Motorista persistirNovoMotorista(
            Motorista motorista,
            String rawPassword,
            boolean assignDefaultCompany) {
        if (motorista.getId() != null) {
            throw new IllegalArgumentException(
                    "salvarNovoMotorista aceita apenas motoristas ainda não persistidos."
            );
        }
        motorista.setEmail(loginIdentityService.requireAvailable(motorista.getEmail()));

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha é obrigatória.");
        }
        motorista.setSenha(BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray()));

        if (assignDefaultCompany && motorista.getCompany() == null) {
            motorista.setCompany(companyService.getDefaultCompany());
        }

        validateBirthDate(motorista.getDataNascimento());

        return loginIdentityService.executeIdentityWrite(
                () -> motoristaRepository.saveAndFlush(motorista));
    }

    @Transactional
    public Motorista registrarMotoristaPublico(PublicDriverRegistrationRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email é obrigatório.");
        }

        Motorista target = new Motorista();
        target.setNome(request.nome());
        target.setEmail(request.email());
        target.setTelefone(request.telefone());
        target.setDataNascimento(request.dataNascimento());
        target.setCompany(null);
        target.setFotoPerfilUrl(null);

        Motorista saved = persistirNovoMotorista(target, request.senha(), false);
        if (QaEmailAutoVerification.shouldAutoVerify(saved.getEmail(), qaAutoVerifyEmailDomains)) {
            QaEmailAutoVerification.markVerified(saved);
        } else {
            emailVerificationService.markPendingAndRevokeSessions(UserRole.DRIVER, saved.getId());
            emailVerificationService.sendVerificationIfAllowed(UserRole.DRIVER, saved.getId(), saved.getEmail());
        }
        return saved;
    }

    // Lista todos os motoristas
    public List<Motorista> listarTodosMotoristas() {
        return motoristaRepository.findAll();
    }

    // Busca um motorista por ID
    public Optional<Motorista> buscarMotoristaPorId(Long id) {
        return motoristaRepository.findById(id);
    }

    public Optional<Motorista> buscarMotoristaPorId(Long id, Long companyId) {
        return motoristaRepository.findByIdAndCompanyId(id, companyId);
    }

    // Deleta um motorista pelo ID
    @Transactional
    public void deletarMotorista(Long id) {
        Motorista motorista = motoristaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado."));
        loginIdentityService.requireOwnedBy(motorista.getEmail(), UserRole.DRIVER, id);
        emailVerificationService.revokeAccountArtifacts(UserRole.DRIVER, id);
        motoristaRepository.delete(motorista);
        motoristaRepository.flush();
    }

    // Busca um motorista pelo e-mail
    public Motorista buscarPorEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return loginIdentityService.findOwner(email)
                .filter(owner -> owner.role() == UserRole.DRIVER)
                .flatMap(owner -> motoristaRepository.findById(owner.userId())
                        .filter(motorista -> owner.normalizedEmail().equals(motorista.getEmail())))
                .orElse(null);
    }

    @Transactional
    public void updatePendingDriverEmail(Long motoristaId, String newEmail) {
        Motorista motorista = motoristaRepository.findById(motoristaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado."));
        loginIdentityService.requireOwnedBy(motorista.getEmail(), UserRole.DRIVER, motoristaId);
        String normalizedEmail = loginIdentityService.requireAvailableFor(
                newEmail,
                UserRole.DRIVER,
                motoristaId
        );
        motorista.setEmail(normalizedEmail);
        EmailVerificationState.markPending(motorista);
        loginIdentityService.executeIdentityWrite(
                () -> motoristaRepository.saveAndFlush(motorista));
    }

    private void validateBirthDate(LocalDate dataNascimento) {
        if (dataNascimento != null && dataNascimento.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de nascimento não pode ser futura.");
        }
    }
}
