package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminGuardianSummaryResponse;
import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminPassageiroLinkedSummaryResponse;
import com.vanrides.vanrides.dto.AdminPassageiroMotoristaLinkResponse;
import com.vanrides.vanrides.dto.AdminResponsavelPassageiroLinkResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.GuardianPassageiro;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.GuardianPassageiroRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CompanyAdminVinculoService {

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private GuardianPassageiroRepository guardianPassageiroRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PassageiroService passageiroService;

    public List<AdminPassageiroMotoristaLinkResponse> listarVinculosPassageiroMotorista(Long companyId) {
        return passageiroRepository.findAllByCompanyIdOrderByNomeAsc(companyId)
                .stream()
                .map(passageiro -> new AdminPassageiroMotoristaLinkResponse(
                        passageiro.getId(),
                        passageiro.getNome(),
                        passageiro.isMenorDeIdade(),
                        toMotoristaSummary(passageiro.getMotorista())
                ))
                .toList();
    }

    public void removerVinculoPassageiroMotorista(Long passageiroId, Long companyId) {
        passageiroService.removerMotorista(passageiroId, companyId);
    }

    public List<AdminResponsavelPassageiroLinkResponse> listarVinculosResponsavelPassageiro(Long companyId) {
        return guardianPassageiroRepository.findAllWithGuardianAndPassageiroByCompanyId(companyId)
                .stream()
                .map(link -> new AdminResponsavelPassageiroLinkResponse(
                        new AdminGuardianSummaryResponse(
                                link.getGuardian().getId(),
                                link.getGuardian().getNome(),
                                link.getGuardian().getEmail(),
                                link.getGuardian().getTelefone(),
                                link.getGuardian().getDataNascimento(),
                                link.getGuardian().getFotoPerfilUrl()
                        ),
                        toPassageiroLinkedSummary(link.getPassageiro())
                ))
                .toList();
    }

    public AdminResponsavelPassageiroLinkResponse criarVinculoResponsavelPassageiro(Long guardianId,
                                                                                    Long passageiroId,
                                                                                    Long companyId) {
        Guardian guardian = guardianRepository.findByIdAndCompanyId(guardianId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável não encontrado na empresa atual."));
        Passageiro passageiro = passageiroRepository.findByIdAndCompanyId(passageiroId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado na empresa atual."));

        if (guardianPassageiroRepository.existsByGuardianIdAndPassageiroIdAndCompanyId(guardianId, passageiroId, companyId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O vínculo informado já existe.");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));

        GuardianPassageiro link = new GuardianPassageiro();
        link.setCompany(company);
        link.setGuardian(guardian);
        link.setPassageiro(passageiro);

        GuardianPassageiro saved = guardianPassageiroRepository.save(link);
        return new AdminResponsavelPassageiroLinkResponse(
                new AdminGuardianSummaryResponse(
                        saved.getGuardian().getId(),
                        saved.getGuardian().getNome(),
                        saved.getGuardian().getEmail(),
                        saved.getGuardian().getTelefone(),
                        saved.getGuardian().getDataNascimento(),
                        saved.getGuardian().getFotoPerfilUrl()
                ),
                toPassageiroLinkedSummary(saved.getPassageiro())
        );
    }

    public void removerVinculoResponsavelPassageiro(Long guardianId, Long passageiroId, Long companyId) {
        GuardianPassageiro link = guardianPassageiroRepository.findByGuardianIdAndPassageiroIdAndCompanyId(guardianId, passageiroId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vínculo não encontrado na empresa atual."));
        guardianPassageiroRepository.delete(link);
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
}
