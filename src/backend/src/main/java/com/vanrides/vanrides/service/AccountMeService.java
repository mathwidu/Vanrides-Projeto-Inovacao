package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AccountMeResponse;
import com.vanrides.vanrides.dto.AccountPasswordChangeRequest;
import com.vanrides.vanrides.dto.AccountProfileUpdateRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.CompanyAdmin;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.function.Consumer;

@Service
public class AccountMeService {

    private static final int PASSWORD_MIN_LENGTH = 8;

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private CompanyAdminRepository companyAdminRepository;

    public AccountMeResponse resolve(UserRole role, Long userId) {
        requireContext(role, userId);

        return switch (role) {
            case PASSENGER -> passageiroRepository.findById(userId)
                    .map(this::fromPassenger)
                    .orElseThrow(this::accountNotFound);
            case DRIVER -> motoristaRepository.findById(userId)
                    .map(this::fromDriver)
                    .orElseThrow(this::accountNotFound);
            case GUARDIAN -> guardianRepository.findById(userId)
                    .map(this::fromGuardian)
                    .orElseThrow(this::accountNotFound);
            case COMPANY_ADMIN -> companyAdminRepository.findById(userId)
                    .map(this::fromCompanyAdmin)
                    .orElseThrow(this::accountNotFound);
        };
    }

    @Transactional
    public AccountMeResponse updateProfile(UserRole role, Long userId, AccountProfileUpdateRequest request) {
        requireContext(role, userId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados de perfil obrigatórios.");
        }

        return switch (role) {
            case PASSENGER -> {
                Passageiro passageiro = passageiroRepository.findById(userId).orElseThrow(this::accountNotFound);
                applyProfileUpdate(passageiro, request);
                yield fromPassenger(passageiroRepository.save(passageiro));
            }
            case DRIVER -> {
                Motorista motorista = motoristaRepository.findById(userId).orElseThrow(this::accountNotFound);
                applyProfileUpdate(motorista, request);
                yield fromDriver(motoristaRepository.save(motorista));
            }
            case GUARDIAN -> {
                Guardian guardian = guardianRepository.findById(userId).orElseThrow(this::accountNotFound);
                applyProfileUpdate(guardian, request);
                yield fromGuardian(guardianRepository.save(guardian));
            }
            case COMPANY_ADMIN -> {
                CompanyAdmin admin = companyAdminRepository.findById(userId).orElseThrow(this::accountNotFound);
                applyProfileUpdate(admin, request);
                yield fromCompanyAdmin(companyAdminRepository.save(admin));
            }
        };
    }

    @Transactional
    public void changePassword(UserRole role, Long userId, AccountPasswordChangeRequest request) {
        requireContext(role, userId);
        validatePasswordChangeRequest(request);

        switch (role) {
            case PASSENGER -> {
                Passageiro passageiro = passageiroRepository.findById(userId).orElseThrow(this::accountNotFound);
                changeStoredPassword(passageiro.getSenha(), request, passageiro::setSenha);
                passageiroRepository.save(passageiro);
            }
            case DRIVER -> {
                Motorista motorista = motoristaRepository.findById(userId).orElseThrow(this::accountNotFound);
                changeStoredPassword(motorista.getSenha(), request, motorista::setSenha);
                motoristaRepository.save(motorista);
            }
            case GUARDIAN -> {
                Guardian guardian = guardianRepository.findById(userId).orElseThrow(this::accountNotFound);
                changeStoredPassword(guardian.getSenha(), request, guardian::setSenha);
                guardianRepository.save(guardian);
            }
            case COMPANY_ADMIN -> {
                CompanyAdmin admin = companyAdminRepository.findById(userId).orElseThrow(this::accountNotFound);
                changeStoredPassword(admin.getSenha(), request, admin::setSenha);
                companyAdminRepository.save(admin);
            }
        }
    }

    private void applyProfileUpdate(Passageiro passageiro, AccountProfileUpdateRequest request) {
        if (request.nome() != null) {
            passageiro.setNome(normalizeRequired(request.nome(), "nome"));
        }
        if (request.telefone() != null) {
            passageiro.setTelefone(normalizeRequired(request.telefone(), "telefone"));
        }
        if (request.dataNascimento() != null) {
            LocalDate dataNascimento = validateBirthDate(request.dataNascimento());
            passageiro.setDataNascimento(dataNascimento);
            passageiro.setMenorDeIdade(isMinor(dataNascimento));
        }
        if (request.fotoPerfilUrl() != null) {
            passageiro.setFotoPerfilUrl(AccountProfilePhotoPolicy.validateAndNormalize(request.fotoPerfilUrl()));
        }
    }

    private void applyProfileUpdate(Motorista motorista, AccountProfileUpdateRequest request) {
        if (request.nome() != null) {
            motorista.setNome(normalizeRequired(request.nome(), "nome"));
        }
        if (request.telefone() != null) {
            motorista.setTelefone(normalizeRequired(request.telefone(), "telefone"));
        }
        if (request.dataNascimento() != null) {
            motorista.setDataNascimento(validateBirthDate(request.dataNascimento()));
        }
        if (request.fotoPerfilUrl() != null) {
            motorista.setFotoPerfilUrl(AccountProfilePhotoPolicy.validateAndNormalize(request.fotoPerfilUrl()));
        }
    }

    private void applyProfileUpdate(Guardian guardian, AccountProfileUpdateRequest request) {
        if (request.nome() != null) {
            guardian.setNome(normalizeRequired(request.nome(), "nome"));
        }
        if (request.telefone() != null) {
            guardian.setTelefone(normalizeRequired(request.telefone(), "telefone"));
        }
        if (request.dataNascimento() != null) {
            guardian.setDataNascimento(validateBirthDate(request.dataNascimento()));
        }
        if (request.fotoPerfilUrl() != null) {
            guardian.setFotoPerfilUrl(AccountProfilePhotoPolicy.validateAndNormalize(request.fotoPerfilUrl()));
        }
    }

    private void applyProfileUpdate(CompanyAdmin admin, AccountProfileUpdateRequest request) {
        if (request.nome() != null) {
            admin.setNome(normalizeRequired(request.nome(), "nome"));
        }
        if (request.telefone() != null) {
            admin.setTelefone(normalizeRequired(request.telefone(), "telefone"));
        }
        if (request.dataNascimento() != null) {
            admin.setDataNascimento(validateBirthDate(request.dataNascimento()));
        }
        if (request.fotoPerfilUrl() != null) {
            admin.setFotoPerfilUrl(AccountProfilePhotoPolicy.validateAndNormalize(request.fotoPerfilUrl()));
        }
    }

    private void validatePasswordChangeRequest(AccountPasswordChangeRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados de senha obrigatórios.");
        }
        if (!hasText(request.getSenhaAtual())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha atual obrigatória.");
        }
        if (!hasText(request.getNovaSenha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nova senha obrigatória.");
        }
        if (request.getNovaSenha().length() < PASSWORD_MIN_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nova senha deve ter pelo menos 8 caracteres.");
        }
    }

    private void changeStoredPassword(String currentHash, AccountPasswordChangeRequest request, Consumer<String> passwordSetter) {
        if (!hasText(currentHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conta não possui senha local configurada.");
        }
        if (!BCrypt.verifyer().verify(request.getSenhaAtual().toCharArray(), currentHash.toCharArray()).verified) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha atual inválida.");
        }
        passwordSetter.accept(BCrypt.withDefaults().hashToString(12, request.getNovaSenha().toCharArray()));
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " não pode ficar vazio.");
        }
        return normalized;
    }

    private LocalDate validateBirthDate(LocalDate dataNascimento) {
        if (dataNascimento.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de nascimento não pode ser futura.");
        }
        return dataNascimento;
    }

    private boolean isMinor(LocalDate dataNascimento) {
        return Period.between(dataNascimento, LocalDate.now()).getYears() < 18;
    }

    private void requireContext(UserRole role, Long userId) {
        if (role == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de usuário ausente");
        }
    }

    private AccountMeResponse fromPassenger(Passageiro passageiro) {
        Long motoristaId = passageiro.getMotorista() != null ? passageiro.getMotorista().getId() : null;
        return new AccountMeResponse(
                passageiro.getId(),
                "Passageiro",
                UserRole.PASSENGER.claimValue(),
                passageiro.getNome(),
                passageiro.getEmail(),
                passageiro.getTelefone(),
                passageiro.isEmailVerified(),
                company(passageiro.getCompany()),
                passageiro.getId(),
                motoristaId,
                null,
                null,
                passageiro.getDataNascimento(),
                passageiro.getFotoPerfilUrl(),
                passageiro.isMenorDeIdade()
        );
    }

    private AccountMeResponse fromDriver(Motorista motorista) {
        return new AccountMeResponse(
                motorista.getId(),
                "Motorista",
                UserRole.DRIVER.claimValue(),
                motorista.getNome(),
                motorista.getEmail(),
                motorista.getTelefone(),
                motorista.getDataNascimento(),
                motorista.getFotoPerfilUrl(),
                motorista.isEmailVerified(),
                company(motorista.getCompany()),
                null,
                motorista.getId(),
                null,
                null
        );
    }

    private AccountMeResponse fromGuardian(Guardian guardian) {
        return new AccountMeResponse(
                guardian.getId(),
                "Responsavel",
                UserRole.GUARDIAN.claimValue(),
                guardian.getNome(),
                guardian.getEmail(),
                guardian.getTelefone(),
                guardian.getDataNascimento(),
                guardian.getFotoPerfilUrl(),
                guardian.isEmailVerified(),
                company(guardian.getCompany()),
                null,
                null,
                guardian.getId(),
                null
        );
    }

    private AccountMeResponse fromCompanyAdmin(CompanyAdmin admin) {
        return new AccountMeResponse(
                admin.getId(),
                "Admin",
                UserRole.COMPANY_ADMIN.claimValue(),
                admin.getNome(),
                admin.getEmail(),
                admin.getTelefone(),
                admin.getDataNascimento(),
                admin.getFotoPerfilUrl(),
                admin.isEmailVerified(),
                company(admin.getCompany()),
                null,
                null,
                null,
                admin.getId()
        );
    }

    private AccountMeResponse.EmpresaResponse company(Company company) {
        if (company == null) {
            return null;
        }
        return new AccountMeResponse.EmpresaResponse(company.getId(), company.getNome(), company.getSlug());
    }

    private ResponseStatusException accountNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta autenticada não encontrada.");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
