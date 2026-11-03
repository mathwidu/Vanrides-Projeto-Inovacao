package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.PassageiroServicoDisponivelResponse;
import com.vanrides.vanrides.dto.PresencaServicoRequest;
import com.vanrides.vanrides.dto.PresencaServicoResponse;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.PresencaServico;
import com.vanrides.vanrides.model.PresencaServicoOrigem;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PresencaServicoService {

    @Autowired
    private PresencaServicoRepository presencaServicoRepository;

    @Autowired
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Autowired
    private ServicoPassageiroRepository servicoPassageiroRepository;

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Transactional(readOnly = true)
    public List<PassageiroServicoDisponivelResponse> listarServicosDisponiveis(Long companyId,
                                                                               Long passageiroId,
                                                                               LocalDate dataOperacao) {
        if (dataOperacao == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de operação é obrigatória.");
        }
        resolvePassageiro(companyId, passageiroId);

        Map<Long, PresencaServico> presencasPorServico = presencaServicoRepository
                .findByPassageiroIdAndCompanyIdAndDataOperacao(passageiroId, companyId, dataOperacao)
                .stream()
                .collect(Collectors.toMap(
                        presenca -> presenca.getServicoRecorrente().getId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));

        return servicoPassageiroRepository.findAllAtivosWithServicoByPassageiroIdAndCompanyId(passageiroId, companyId)
                .stream()
                .filter(servicoPassageiro -> servicoPassageiro.ativoNaData(dataOperacao))
                .map(ServicoPassageiro::getServicoRecorrente)
                .filter(servico -> servico.operaNaData(dataOperacao))
                .map(servico -> toServicoDisponivelResponse(servico, dataOperacao, presencasPorServico.get(servico.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PresencaServicoResponse> listarPresencasPorPassageiro(Long companyId,
                                                                      Long passageiroId,
                                                                      LocalDate dataInicio,
                                                                      LocalDate dataFim) {
        resolvePassageiro(companyId, passageiroId);
        if (dataInicio == null || dataFim == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período de consulta é obrigatório.");
        }
        if (dataFim.isBefore(dataInicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data final não pode ser anterior à data inicial.");
        }

        return presencaServicoRepository
                .findByPassageiroIdAndCompanyIdAndDataOperacaoBetweenOrderByDataOperacaoAsc(
                        passageiroId,
                        companyId,
                        dataInicio,
                        dataFim
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PresencaServicoResponse criarPresenca(Long companyId,
                                                 PresencaServicoRequest request,
                                                 PresencaServicoOrigem origem) {
        validateCreateRequest(request);
        PresencaServicoStatus status = parseStatus(request.getStatus(), origem);
        ServicoRecorrente servico = resolveServico(companyId, request.getServicoId());
        Passageiro passageiro = resolvePassageiro(companyId, request.getPassageiroId());
        ServicoPassageiro elegibilidade = resolveElegibilidade(companyId, servico.getId(), passageiro.getId());

        validateServicoOperavel(servico, elegibilidade, request.getDataOperacao());

        Optional<PresencaServico> existing = presencaServicoRepository
                .findByServicoRecorrenteIdAndPassageiroIdAndCompanyIdAndDataOperacao(
                        servico.getId(),
                        passageiro.getId(),
                        companyId,
                        request.getDataOperacao()
                );
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe presença para este serviço, passageiro e data.");
        }

        PresencaServico presenca = new PresencaServico();
        presenca.setCompany(servico.getCompany());
        presenca.setServicoRecorrente(servico);
        presenca.setPassageiro(passageiro);
        presenca.setDataOperacao(request.getDataOperacao());
        presenca.setStatus(status);
        presenca.setOrigem(origem);

        return toResponse(presencaServicoRepository.save(presenca));
    }

    @Transactional
    public PresencaServicoResponse atualizarStatus(Long companyId,
                                                   Long presencaId,
                                                   String rawStatus,
                                                   PresencaServicoOrigem origem) {
        PresencaServico presenca = presencaServicoRepository.findByIdAndCompanyIdWithDetails(presencaId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Presença por serviço não encontrada na empresa atual."));
        presenca.setStatus(parseStatus(rawStatus, origem));
        presenca.setOrigem(origem);
        return toResponse(presencaServicoRepository.save(presenca));
    }

    @Transactional
    public void deletarPresenca(Long companyId, Long presencaId) {
        PresencaServico presenca = presencaServicoRepository.findByIdAndCompanyIdWithDetails(presencaId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Presença por serviço não encontrada na empresa atual."));
        presencaServicoRepository.delete(presenca);
    }

    @Transactional(readOnly = true)
    public Optional<Long> buscarPassageiroIdDaPresenca(Long companyId, Long presencaId) {
        return presencaServicoRepository.findByIdAndCompanyIdWithDetails(presencaId, companyId)
                .map(presenca -> presenca.getPassageiro().getId());
    }

    private void validateCreateRequest(PresencaServicoRequest request) {
        if (request == null
                || request.getServicoId() == null
                || request.getPassageiroId() == null
                || request.getDataOperacao() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serviço, passageiro e data são obrigatórios.");
        }
    }

    private ServicoRecorrente resolveServico(Long companyId, Long servicoId) {
        return servicoRecorrenteRepository.findByIdAndCompanyId(servicoId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço recorrente não encontrado na empresa atual."));
    }

    private Passageiro resolvePassageiro(Long companyId, Long passageiroId) {
        return passageiroRepository.findByIdAndCompanyId(passageiroId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado na empresa atual."));
    }

    private ServicoPassageiro resolveElegibilidade(Long companyId, Long servicoId, Long passageiroId) {
        return servicoPassageiroRepository
                .findByServicoRecorrenteIdAndPassageiroIdAndCompanyIdAndAtivoTrue(servicoId, passageiroId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não está elegível para este serviço."));
    }

    private void validateServicoOperavel(ServicoRecorrente servico,
                                         ServicoPassageiro elegibilidade,
                                         LocalDate dataOperacao) {
        if (!servico.operaNaData(dataOperacao)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serviço não opera na data informada.");
        }
        if (!elegibilidade.ativoNaData(dataOperacao)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passageiro não está ativo neste serviço na data informada.");
        }
    }

    private PresencaServicoStatus parseStatus(String rawStatus, PresencaServicoOrigem origem) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status da presença é obrigatório.");
        }
        PresencaServicoStatus status;
        try {
            status = PresencaServicoStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de presença inválido: " + rawStatus);
        }
        if (status == PresencaServicoStatus.UNKNOWN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status UNKNOWN não pode ser salvo como decisão explícita.");
        }
        if (status == PresencaServicoStatus.CANCELED_BY_COMPANY && origem != PresencaServicoOrigem.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas a empresa pode cancelar presença por serviço.");
        }
        return status;
    }

    private PassageiroServicoDisponivelResponse toServicoDisponivelResponse(ServicoRecorrente servico,
                                                                           LocalDate dataOperacao,
                                                                           PresencaServico presenca) {
        return new PassageiroServicoDisponivelResponse(
                servico.getId(),
                servico.getNome(),
                servico.getTipoOperacao(),
                dataOperacao,
                servico.getHorarioPartida(),
                servico.getDestinoFinalNome(),
                servico.getDestinoFinalEndereco(),
                servico.getPoliticaPresenca(),
                toMotoristaSummary(servico.getMotoristaPadrao()),
                presenca == null ? null : presenca.getId(),
                presenca == null ? PresencaServicoStatus.UNKNOWN : presenca.getStatus()
        );
    }

    private PresencaServicoResponse toResponse(PresencaServico presenca) {
        return new PresencaServicoResponse(
                presenca.getId(),
                presenca.getServicoRecorrente().getId(),
                presenca.getServicoRecorrente().getNome(),
                presenca.getServicoRecorrente().getTipoOperacao(),
                presenca.getServicoRecorrente().getHorarioPartida(),
                presenca.getPassageiro().getId(),
                presenca.getPassageiro().getNome(),
                presenca.getDataOperacao(),
                presenca.getStatus(),
                presenca.getOrigem()
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
