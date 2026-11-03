package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AccountMeResponse;
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

@Service
public class AccountAvatarService {

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private CompanyAdminRepository companyAdminRepository;

    @Autowired
    private AccountMeService accountMeService;

    @Transactional
    public AccountMeResponse updateAvatar(UserRole role, Long userId, String avatarUrl) {
        requireContext(role, userId);
        String normalizedAvatarUrl = AccountProfilePhotoPolicy.validateAndNormalize(avatarUrl);
        if (normalizedAvatarUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Foto de perfil obrigatória.");
        }

        switch (role) {
            case PASSENGER -> updatePassenger(userId, normalizedAvatarUrl);
            case DRIVER -> updateDriver(userId, normalizedAvatarUrl);
            case GUARDIAN -> updateGuardian(userId, normalizedAvatarUrl);
            case COMPANY_ADMIN -> updateCompanyAdmin(userId, normalizedAvatarUrl);
        }
        return accountMeService.resolve(role, userId);
    }

    private void updatePassenger(Long userId, String avatarUrl) {
        Passageiro passageiro = passageiroRepository.findById(userId).orElseThrow(this::accountNotFound);
        passageiro.setFotoPerfilUrl(avatarUrl);
        passageiroRepository.save(passageiro);
    }

    private void updateDriver(Long userId, String avatarUrl) {
        Motorista motorista = motoristaRepository.findById(userId).orElseThrow(this::accountNotFound);
        motorista.setFotoPerfilUrl(avatarUrl);
        motoristaRepository.save(motorista);
    }

    private void updateGuardian(Long userId, String avatarUrl) {
        Guardian guardian = guardianRepository.findById(userId).orElseThrow(this::accountNotFound);
        guardian.setFotoPerfilUrl(avatarUrl);
        guardianRepository.save(guardian);
    }

    private void updateCompanyAdmin(Long userId, String avatarUrl) {
        CompanyAdmin admin = companyAdminRepository.findById(userId).orElseThrow(this::accountNotFound);
        admin.setFotoPerfilUrl(avatarUrl);
        companyAdminRepository.save(admin);
    }

    private void requireContext(UserRole role, Long userId) {
        if (role == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de usuário ausente.");
        }
    }

    private ResponseStatusException accountNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada.");
    }
}
