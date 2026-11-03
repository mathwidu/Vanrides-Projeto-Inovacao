package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminGuardianSummaryResponse;
import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminPassageiroCreateRequest;
import com.vanrides.vanrides.dto.AdminPassageiroDetailResponse;
import com.vanrides.vanrides.dto.AdminPassageiroServicoResponse;
import com.vanrides.vanrides.dto.AdminPassageiroServicosUpdateRequest;
import com.vanrides.vanrides.dto.AdminPassageiroSummaryResponse;
import com.vanrides.vanrides.dto.AdminPassageiroUpdateRequest;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.GuardianPassageiro;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.GuardianPassageiroRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CompanyAdminPassageiroService {
    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private GuardianPassageiroRepository guardianPassageiroRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private LoginIdentityService loginIdentityService;

    @Autowired
    private PassageiroService passageiroService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private PassengerServiceEligibilityManagementService serviceEligibilityManagementService;

    @Autowired
    private ServicoPassageiroRepository servicoPassageiroRepository;

    @Autowired
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Value("${vanrides.qa.auto-verify-email-domains:}")
    private String qaAutoVerifyEmailDomains;

    public List<AdminPassageiroSummaryResponse> listarPassageiros(Long companyId) {
        return passageiroRepository.findAllByCompanyIdOrderByNomeAsc(companyId)
                .stream()
                .map(passageiro -> toSummary(passageiro, companyId))
                .toList();
    }

    public Optional<AdminPassageiroDetailResponse> buscarDetalhe(Long passageiroId, Long companyId) {
        return passageiroRepository.findByIdAndCompanyId(passageiroId, companyId)
                .map(passageiro -> toDetail(passageiro, companyId));
    }

    @Transactional(readOnly = true)
    public List<AdminPassageiroServicoResponse> listarServicosDoPassageiro(Long passageiroId, Long companyId) {
        Passageiro passageiro = passageiroRepository.findByIdAndCompanyId(passageiroId, companyId)
                .orElseThrow(() -> notFoundPassageiro());
        return listarServicosDoPassageiroInternal(passageiro, companyId);
    }

    @Transactional
    public AdminPassageiroDetailResponse criarPassageiro(Long companyId, AdminPassageiroCreateRequest request) {
        String normalizedEmail = loginIdentityService.requireAvailable(request.getEmail());
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));
        List<Long> servicoIds = null;
        if (request.getServicoIds() != null) {
            servicoIds = serviceEligibilityManagementService.validateServiceIds(companyId, request.getServicoIds());
        }
        Passageiro passageiro = new Passageiro();
        passageiro.setCompany(company);
        passageiro.setNome(request.getNome().trim());
        passageiro.setEmail(normalizedEmail);
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

        if (request.getMotoristaId() != null) {
            passageiro.setMotorista(resolveMotorista(request.getMotoristaId(), companyId));
        }

        boolean autoVerifyForQa = QaEmailAutoVerification.shouldAutoVerify(normalizedEmail, qaAutoVerifyEmailDomains);
        Passageiro saved = passageiroService.salvarNovoPassageiro(
                passageiro,
                request.getSenha().trim()
        );
        if (servicoIds != null) {
            serviceEligibilityManagementService.replaceServiceEligibilities(saved, companyId, servicoIds);
        }
        if (autoVerifyForQa) {
            QaEmailAutoVerification.markVerified(saved);
        } else {
            emailVerificationService.markPendingAndRevokeSessions(com.vanrides.vanrides.auth.UserRole.PASSENGER, saved.getId());
            emailVerificationService.sendVerificationIfAllowed(com.vanrides.vanrides.auth.UserRole.PASSENGER, saved.getId(), saved.getEmail());
        }
        return toDetail(saved, companyId);
    }

    @Transactional
    public AdminPassageiroDetailResponse atualizarPassageiro(Long passageiroId,
                                                             Long companyId,
                                                             AdminPassageiroUpdateRequest request) {
        Passageiro passageiro = passageiroRepository.findByIdAndCompanyId(passageiroId, companyId)
                .orElseThrow(() -> notFoundPassageiro());
        String previousEmail = passageiro.getEmail();

        Passageiro saved = passageiroService.atualizarPassageiroAdministrativo(
                passageiroId,
                companyId,
                request
        );
        boolean emailChanged = !previousEmail.equals(saved.getEmail());
        if (emailChanged) {
            if (QaEmailAutoVerification.shouldAutoVerify(saved.getEmail(), qaAutoVerifyEmailDomains)) {
                QaEmailAutoVerification.markVerified(saved);
            } else {
                emailVerificationService.markPendingAndRevokeSessions(com.vanrides.vanrides.auth.UserRole.PASSENGER, saved.getId());
                emailVerificationService.sendVerificationIfAllowed(com.vanrides.vanrides.auth.UserRole.PASSENGER, saved.getId(), saved.getEmail());
            }
        }
        return toDetail(saved, companyId);
    }

    public AdminPassageiroDetailResponse reatribuirMotorista(Long passageiroId,
                                                             Long companyId,
                                                             Long motoristaId) {
        Passageiro saved = passageiroService.reatribuirMotorista(
                passageiroId,
                companyId,
                motoristaId
        );
        return toDetail(saved, companyId);
    }

    @Transactional
    public List<AdminPassageiroServicoResponse> substituirServicosDoPassageiro(Long passageiroId,
                                                                               Long companyId,
                                                                               AdminPassageiroServicosUpdateRequest request) {
        if (request == null || request.getServicoIds() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista de serviços é obrigatória.");
        }

        Passageiro passageiro = passageiroRepository.findByIdAndCompanyId(passageiroId, companyId)
                .orElseThrow(() -> notFoundPassageiro());
        serviceEligibilityManagementService.replaceServiceEligibilities(passageiro, companyId, request.getServicoIds());

        return listarServicosDoPassageiroInternal(passageiro, companyId);
    }

    private ResponseStatusException notFoundPassageiro() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado na empresa atual.");
    }

    private Motorista resolveMotorista(Long motoristaId, Long companyId) {
        return motoristaRepository.findByIdAndCompanyId(motoristaId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado na empresa atual."));
    }

    private List<AdminPassageiroServicoResponse> listarServicosDoPassageiroInternal(Passageiro passageiro, Long companyId) {
        Map<Long, ServicoPassageiro> activeByServicoId = new LinkedHashMap<>();
        for (ServicoPassageiro vinculo : servicoPassageiroRepository
                .findAllAtivosWithServicoByPassageiroIdAndCompanyId(passageiro.getId(), companyId)) {
            activeByServicoId.put(vinculo.getServicoRecorrente().getId(), vinculo);
        }

        return servicoRecorrenteRepository.findAllAtivosWithMotoristaByCompanyIdOrderByNomeAsc(companyId)
                .stream()
                .map(servico -> toPassageiroServicoResponse(servico, activeByServicoId.get(servico.getId())))
                .toList();
    }

    private AdminPassageiroSummaryResponse toSummary(Passageiro passageiro, Long companyId) {
        long responsavelCount = guardianPassageiroRepository.countByPassageiroIdAndCompanyId(passageiro.getId(), companyId);
        return new AdminPassageiroSummaryResponse(
                passageiro.getId(),
                passageiro.getNome(),
                passageiro.getEmail(),
                passageiro.getTelefone(),
                passageiro.isMenorDeIdade(),
                toMotoristaSummary(passageiro.getMotorista()),
                responsavelCount,
                passageiro.isEmailVerified(), passageiro.getDataNascimento(), passageiro.getFotoPerfilUrl()
        );
    }

    private AdminPassageiroDetailResponse toDetail(Passageiro passageiro, Long companyId) {
        List<AdminGuardianSummaryResponse> responsaveis = guardianPassageiroRepository
                .findAllWithGuardianByPassageiroIdAndCompanyId(passageiro.getId(), companyId)
                .stream()
                .map(GuardianPassageiro::getGuardian)
                .map(guardian -> new AdminGuardianSummaryResponse(
                        guardian.getId(),
                        guardian.getNome(),
                        guardian.getEmail(),
                        guardian.getTelefone(), guardian.getDataNascimento(), guardian.getFotoPerfilUrl()
                ))
                .toList();

        return new AdminPassageiroDetailResponse(
                passageiro.getId(),
                passageiro.getNome(),
                passageiro.getEmail(),
                passageiro.getTelefone(),
                passageiro.getEndereco(),
                passageiro.getBairro(),
                passageiro.getCidade(),
                passageiro.getEstado(),
                passageiro.getCep(),
                passageiro.getLatitude(),
                passageiro.getLongitude(),
                passageiro.isMenorDeIdade(),
                toMotoristaSummary(passageiro.getMotorista()),
                responsaveis,
                passageiro.isEmailVerified(), passageiro.getDataNascimento(), passageiro.getFotoPerfilUrl()
        );
    }

    private AdminPassageiroServicoResponse toPassageiroServicoResponse(ServicoRecorrente servico,
                                                                       ServicoPassageiro vinculo) {
        return new AdminPassageiroServicoResponse(
                servico.getId(),
                servico.getNome(),
                servico.getTipoOperacao(),
                servico.getHorarioPartida(),
                toMotoristaSummary(servico.getMotoristaPadrao()),
                vinculo != null && vinculo.isAtivo(),
                vinculo == null ? null : vinculo.getId(),
                vinculo == null ? null : vinculo.getOrdemPadrao(),
                vinculo == null ? null : vinculo.getDataInicio(),
                vinculo == null ? null : vinculo.getDataFim()
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
                motorista.getTelefone(), motorista.isEmailVerified(),
                motorista.getDataNascimento(), motorista.getFotoPerfilUrl());
    }
}
