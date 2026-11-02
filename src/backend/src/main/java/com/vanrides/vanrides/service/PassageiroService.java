package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AdminPassageiroUpdateRequest;
import com.vanrides.vanrides.dto.PublicPassengerRegistrationRequest;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class PassageiroService {

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private PassageiroPersistencePreparer persistencePreparer;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private LoginIdentityService loginIdentityService;

    @Value("${vanrides.qa.auto-verify-email-domains:}")
    private String qaAutoVerifyEmailDomains;

    @Transactional
    public Passageiro salvarNovoPassageiro(Passageiro passageiro, String rawPassword) {
        if (passageiro.getId() != null) {
            throw new IllegalArgumentException(
                    "salvarNovoPassageiro aceita apenas passageiros ainda não persistidos."
            );
        }

        if (passageiro.getEmail() != null) {
            passageiro.setEmail(loginIdentityService.requireAvailable(passageiro.getEmail()));
        }

        persistencePreparer.prepareNew(passageiro, rawPassword);
        return loginIdentityService.executeIdentityWrite(
                () -> passageiroRepository.saveAndFlush(passageiro)
        );
    }

    @Transactional
    public Passageiro atualizarPassageiroAdministrativo(
            Long passageiroId,
            Long companyId,
            AdminPassageiroUpdateRequest request) {
        Passageiro passageiro = passageiroRepository.findByIdAndCompanyId(passageiroId, companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Passageiro não encontrado na empresa atual."
                ));
        String currentEmail = loginIdentityService.requireOwnedBy(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                passageiro.getId()
        );
        String destinationEmail = loginIdentityService.requireAvailableFor(
                request.getEmail(),
                UserRole.PASSENGER,
                passageiro.getId()
        );

        passageiro.setNome(request.getNome().trim());
        passageiro.setTelefone(request.getTelefone().trim());
        passageiro.setEndereco(request.getEndereco().trim());
        passageiro.setBairro(request.getBairro().trim());
        passageiro.setCidade(request.getCidade().trim());
        passageiro.setEstado(request.getEstado().trim());
        passageiro.setCep(request.getCep().trim());
        passageiro.setDataNascimento(request.getDataNascimento());
        passageiro.setMenorDeIdade(request.isMenorDeIdade());
        passageiro.setLatitude(request.getLatitude());
        passageiro.setLongitude(request.getLongitude());
        passageiro.setEmail(destinationEmail);

        if (request.getMotoristaId() != null) {
            passageiro.setMotorista(motoristaRepository
                    .findByIdAndCompanyId(request.getMotoristaId(), companyId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Motorista não encontrado na empresa atual."
                    )));
        }

        if (!currentEmail.equals(destinationEmail)) {
            EmailVerificationState.markPending(passageiro);
        }

        String rawPassword = request.getSenha() == null || request.getSenha().isBlank()
                ? null
                : request.getSenha().trim();
        persistencePreparer.prepareExisting(passageiro, rawPassword);
        return loginIdentityService.executeIdentityWrite(
                () -> passageiroRepository.saveAndFlush(passageiro)
        );
    }

    @Transactional
    public Passageiro registrarPassageiroPublico(PublicPassengerRegistrationRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email é obrigatório.");
        }

        String normalizedEmail = loginIdentityService.requireAvailable(request.email());

        Passageiro target = new Passageiro();
        target.setNome(request.nome());
        target.setEmail(normalizedEmail);
        target.setTelefone(request.telefone());
        target.setEndereco(request.endereco());
        target.setBairro(request.bairro());
        target.setCidade(request.cidade());
        target.setEstado(request.estado());
        target.setCep(request.cep());
        target.setLatitude(request.latitude());
        target.setLongitude(request.longitude());
        target.setDataNascimento(request.dataNascimento());
        target.setMenorDeIdade(false);
        // Cadastro público cria somente identidade. Motorista e empresa são
        // vínculos governados pelos fluxos de onboarding/admin e nunca podem
        // ser definidos pelo payload anônimo.
        target.setMotorista(null);
        target.setCompany(null);
        target.setFotoPerfilUrl(null);

        Passageiro saved = salvarNovoPassageiro(target, request.senha());
        if (QaEmailAutoVerification.shouldAutoVerify(saved.getEmail(), qaAutoVerifyEmailDomains)) {
            QaEmailAutoVerification.markVerified(saved);
        } else {
            emailVerificationService.markPendingAndRevokeSessions(UserRole.PASSENGER, saved.getId());
            emailVerificationService.sendVerificationIfAllowed(UserRole.PASSENGER, saved.getId(), saved.getEmail());
        }
        return saved;
    }

    // ✅ Listar todos os passageiros
    public List<Passageiro> listarTodosPassageiros() {
        return passageiroRepository.findAll();
    }

    public List<Passageiro> listarTodosPassageiros(Long companyId) {
        return passageiroRepository.findAllByCompanyId(companyId);
    }

    public List<Passageiro> listarPassageirosPorIds(List<Long> passageiroIds, Long companyId) {
        if (passageiroIds == null || passageiroIds.isEmpty()) {
            return List.of();
        }
        return passageiroRepository.findAllByIdInAndCompanyId(passageiroIds, companyId);
    }

    // ✅ Buscar passageiro por ID
    public Optional<Passageiro> buscarPassageiroPorId(Long id) {
        return passageiroRepository.findById(id);
    }

    public Optional<Passageiro> buscarPassageiroPorId(Long id, Long companyId) {
        return passageiroRepository.findByIdAndCompanyId(id, companyId);
    }

    @Transactional
    public Passageiro reatribuirMotorista(Long passageiroId, Long companyId, Long motoristaId) {
        Passageiro passageiro = requirePassengerFromCompany(passageiroId, companyId);
        loginIdentityService.requireOwnedBy(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                passageiro.getId()
        );
        passageiro.setMotorista(motoristaRepository.findByIdAndCompanyId(motoristaId, companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Motorista não encontrado na empresa atual."
                )));
        persistencePreparer.prepareExisting(passageiro, null);
        return loginIdentityService.executeIdentityWrite(
                () -> passageiroRepository.saveAndFlush(passageiro)
        );
    }

    @Transactional
    public Passageiro removerMotorista(Long passageiroId, Long companyId) {
        Passageiro passageiro = requirePassengerFromCompany(passageiroId, companyId);
        loginIdentityService.requireOwnedBy(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                passageiro.getId()
        );
        passageiro.setMotorista(null);
        persistencePreparer.prepareExisting(passageiro, null);
        return loginIdentityService.executeIdentityWrite(
                () -> passageiroRepository.saveAndFlush(passageiro)
        );
    }

    @Transactional
    public Optional<Passageiro> atualizarPassageiroProtegido(
            Long id,
            Long companyId,
            Passageiro request) {
        Optional<Passageiro> existing = passageiroRepository.findByIdAndCompanyId(id, companyId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Passageiro passageiro = existing.get();
        String currentEmail = loginIdentityService.requireOwnedBy(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                passageiro.getId()
        );
        String destinationEmail = loginIdentityService.requireAvailableFor(
                request.getEmail(),
                UserRole.PASSENGER,
                passageiro.getId()
        );
        boolean emailChanged = !currentEmail.equals(destinationEmail);

        passageiro.setNome(request.getNome());
        passageiro.setEmail(destinationEmail);
        passageiro.setTelefone(request.getTelefone());
        passageiro.setEndereco(request.getEndereco());
        passageiro.setBairro(request.getBairro());
        passageiro.setCidade(request.getCidade());
        passageiro.setEstado(request.getEstado());
        passageiro.setCep(request.getCep());
        passageiro.setDataNascimento(request.getDataNascimento());
        passageiro.setLatitude(request.getLatitude());
        passageiro.setLongitude(request.getLongitude());
        String rawPassword = request.getSenha() == null || request.getSenha().isBlank()
                ? null
                : request.getSenha();
        if (emailChanged) {
            EmailVerificationState.markPending(passageiro);
        }

        persistencePreparer.prepareExisting(passageiro, rawPassword);
        Passageiro saved = loginIdentityService.executeIdentityWrite(
                () -> passageiroRepository.saveAndFlush(passageiro)
        );
        if (emailChanged) {
            emailVerificationService.markPendingAndRevokeSessions(UserRole.PASSENGER, saved.getId());
            emailVerificationService.sendVerificationIfAllowed(
                    UserRole.PASSENGER,
                    saved.getId(),
                    saved.getEmail()
            );
        }
        return Optional.of(saved);
    }

    // ✅ Deletar passageiro
    @Transactional
    public void deletarPassageiro(Long id, Long companyId) {
        int deleted = passageiroRepository.deleteByIdAndCompanyIdAtomically(id, companyId);
        if (deleted == 1) {
            return;
        }
        if (deleted == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Passageiro não encontrado na empresa atual."
            );
        }
        throw new IllegalStateException("Delete tenant-scoped removeu mais de um passageiro.");
    }

    // ✅ Buscar por e-mail
    public Passageiro buscarPorEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return loginIdentityService.findOwner(email)
                .filter(owner -> owner.role() == UserRole.PASSENGER)
                .flatMap(owner -> passageiroRepository.findById(owner.userId())
                        .filter(passageiro -> owner.normalizedEmail().equals(passageiro.getEmail())))
                .orElse(null);
    }

    @Transactional
    public void updatePendingPassengerEmail(Long passageiroId, String newEmail) {
        Passageiro passageiro = passageiroRepository.findById(passageiroId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado."));
        loginIdentityService.requireOwnedBy(
                passageiro.getEmail(),
                UserRole.PASSENGER,
                passageiroId
        );
        String normalizedEmail = loginIdentityService.requireAvailableFor(
                newEmail,
                UserRole.PASSENGER,
                passageiroId
        );
        passageiro.setEmail(normalizedEmail);
        EmailVerificationState.markPending(passageiro);
        loginIdentityService.executeIdentityWrite(() -> passageiroRepository.saveAndFlush(passageiro));
    }

    private Passageiro requirePassengerFromCompany(Long passageiroId, Long companyId) {
        return passageiroRepository.findByIdAndCompanyId(passageiroId, companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Passageiro não encontrado na empresa atual."
                ));
    }
}
