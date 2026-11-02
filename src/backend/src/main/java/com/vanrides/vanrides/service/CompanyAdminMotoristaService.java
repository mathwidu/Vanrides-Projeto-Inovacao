package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminMotoristaCreateRequest;
import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminMotoristaUpdateRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
public class CompanyAdminMotoristaService {

    private static final Set<String> DRIVER_DEPENDENCY_CONSTRAINTS = Set.of(
            "fk_passageiro_motorista",
            "fk_localizacao_motorista",
            "fk_rota_base_motorista",
            "fk_viagem_motorista",
            "fk_driver_company_membership_motorista",
            "fk_driver_company_invite_motorista",
            "fk_driver_company_link_request_motorista",
            "fk_servico_recorrente_motorista_padrao"
    );

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private LoginIdentityService loginIdentityService;

    @Autowired
    private MotoristaService motoristaService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private DriverCompanyLinker driverCompanyLinker;

    @Autowired
    private ViagemRepository viagemRepository;

    @Value("${vanrides.qa.auto-verify-email-domains:}")
    private String qaAutoVerifyEmailDomains;

    public List<AdminMotoristaSummaryResponse> listarMotoristas(Long companyId) {
        return motoristaRepository.findAllByCompanyIdOrderByNomeAsc(companyId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public AdminMotoristaSummaryResponse criarMotorista(Long companyId, AdminMotoristaCreateRequest request) {
        String email = loginIdentityService.requireAvailable(request.getEmail());
        String senha = request.getSenha() != null ? request.getSenha().trim() : "";

        if (senha.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Senha é obrigatória para criar um motorista novo.");
        }

        if (!senha.isBlank() && senha.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha do motorista deve ter pelo menos 8 caracteres.");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));

        Motorista motorista = new Motorista();
        motorista.setCompany(company);
        motorista.setNome(request.getNome().trim());
        motorista.setEmail(email);
        motorista.setTelefone(request.getTelefone().trim());
        motorista.setDataNascimento(request.getDataNascimento());

        boolean autoVerifyForQa = QaEmailAutoVerification.shouldAutoVerify(email, qaAutoVerifyEmailDomains);
        Motorista saved = motoristaService.salvarNovoMotorista(motorista, senha);
        if (autoVerifyForQa) {
            QaEmailAutoVerification.markVerified(saved);
        } else if (!saved.isEmailVerified()) {
            emailVerificationService.markPendingAndRevokeSessions(com.vanrides.vanrides.auth.UserRole.DRIVER, saved.getId());
            emailVerificationService.sendVerificationIfAllowed(com.vanrides.vanrides.auth.UserRole.DRIVER, saved.getId(), saved.getEmail());
        }
        driverCompanyLinker.link(saved, company, "ADMIN_CREATE", "Vínculo criado pelo painel administrativo.");
        return toSummary(saved);
    }

    @Transactional
    public AdminMotoristaSummaryResponse editarMotorista(Long companyId, Long id, AdminMotoristaUpdateRequest request) {
        Motorista motorista = motoristaRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado na empresa atual."));

        String currentEmail = loginIdentityService.requireOwnedBy(
                motorista.getEmail(),
                UserRole.DRIVER,
                motorista.getId()
        );
        String normalizedEmail = loginIdentityService.requireAvailableFor(
                request.getEmail(),
                UserRole.DRIVER,
                motorista.getId()
        );
        boolean emailChanged = !normalizedEmail.equals(currentEmail);
        boolean autoVerifyForQa = emailChanged && QaEmailAutoVerification.shouldAutoVerify(normalizedEmail, qaAutoVerifyEmailDomains);
        if (emailChanged) {
            EmailVerificationState.markPending(motorista);
        }

        motorista.setNome(request.getNome().trim());
        motorista.setEmail(normalizedEmail);
        motorista.setTelefone(request.getTelefone().trim());
        motorista.setDataNascimento(request.getDataNascimento());

        Motorista saved = loginIdentityService.executeIdentityWrite(
                () -> motoristaRepository.saveAndFlush(motorista)
        );
        if (emailChanged) {
            if (autoVerifyForQa) {
                QaEmailAutoVerification.markVerified(saved);
            } else {
                emailVerificationService.markPendingAndRevokeSessions(UserRole.DRIVER, saved.getId());
                emailVerificationService.sendVerificationIfAllowed(UserRole.DRIVER, saved.getId(), saved.getEmail());
            }
        }
        return toSummary(saved);
    }

    @Transactional
    public void deletarMotorista(Long companyId, Long id) {
        Motorista motorista = motoristaRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado na empresa atual."));

        if (viagemRepository.existsByMotoristaIdAndStatusAndCompanyId(id, ViagemStatus.IN_PROGRESS, companyId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível remover: há uma viagem em andamento com este motorista.");
        }

        loginIdentityService.requireOwnedBy(motorista.getEmail(), UserRole.DRIVER, id);
        emailVerificationService.revokeAccountArtifacts(UserRole.DRIVER, id);
        try {
            motoristaRepository.delete(motorista);
            motoristaRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (DRIVER_DEPENDENCY_CONSTRAINTS.stream()
                    .anyMatch(name -> loginIdentityService.hasConstraint(exception, name))) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Não é possível remover o motorista: existem vínculos existentes.",
                        exception
                );
            }
            throw exception;
        }
    }

    private AdminMotoristaSummaryResponse toSummary(Motorista motorista) {
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

}
