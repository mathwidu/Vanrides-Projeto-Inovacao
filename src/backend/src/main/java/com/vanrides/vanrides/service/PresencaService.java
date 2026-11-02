package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.Presenca;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.PresencaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class PresencaService {

    @Autowired
    private PresencaRepository presencaRepository;

    @Autowired
    private PassageiroRepository passageiroRepository;

    public Presenca salvarPresenca(Presenca presenca) {
        return presencaRepository.save(presenca);
    }

    public Presenca salvarPresenca(Presenca presenca, Long companyId) {
        if (presenca.getPassageiro() == null || presenca.getPassageiro().getId() == null) {
            throw new IllegalArgumentException("Passageiro é obrigatório.");
        }

        Passageiro passageiro = passageiroRepository.findByIdAndCompanyId(presenca.getPassageiro().getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Passageiro não encontrado na empresa atual."));

        presenca.setPassageiro(passageiro);
        return presencaRepository.save(presenca);
    }

    public List<Presenca> listarTodasPresencas() {
        return presencaRepository.findAll();
    }

    public List<Presenca> listarTodasPresencas(Long companyId) {
        return presencaRepository.findAllByCompanyId(companyId);
    }

    public List<Presenca> listarPresencasPorPassageiro(Long passageiroId) {
        return presencaRepository.findByPassageiroId(passageiroId);
    }

    public List<Presenca> listarPresencasPorPassageiro(Long passageiroId, Long companyId) {
        return presencaRepository.findByPassageiroIdAndCompanyId(passageiroId, companyId);
    }

    public List<Presenca> listarPresencasPorPassageiros(List<Long> passageiroIds, Long companyId) {
        if (passageiroIds == null || passageiroIds.isEmpty()) {
            return List.of();
        }
        return presencaRepository.findByPassageiroIdInAndCompanyId(passageiroIds, companyId);
    }

    public List<Presenca> listarPresencasPorData(LocalDate dataPresenca) {
        return presencaRepository.findByDataPresenca(dataPresenca);
    }

    public List<Presenca> listarPresencasPorData(LocalDate dataPresenca, Long companyId) {
        return presencaRepository.findByDataPresencaAndCompanyId(dataPresenca, companyId);
    }

    public List<Presenca> listarPresencasPorMotoristaEData(Long motoristaId, LocalDate data) {
        return presencaRepository.findByMotoristaIdAndData(motoristaId, data);
    }

    public List<Presenca> listarPresencasPorMotoristaEData(Long motoristaId, LocalDate data, Long companyId) {
        return presencaRepository.findByMotoristaIdAndDataAndCompanyId(motoristaId, data, companyId);
    }

    public Optional<Presenca> buscarPresencaPorId(Long id) {
        return presencaRepository.findById(id);
    }

    public Optional<Presenca> buscarPresencaPorId(Long id, Long companyId) {
        return presencaRepository.findByIdAndCompanyId(id, companyId);
    }

    public void deletarPresenca(Long id) {
        presencaRepository.deleteById(id);
    }

    public boolean jaExistePresenca(Long passageiroId, LocalDate dataPresenca) {
        return presencaRepository.existsByPassageiroIdAndDataPresenca(passageiroId, dataPresenca);
    }

    public boolean jaExistePresenca(Long passageiroId, LocalDate dataPresenca, Long companyId) {
        return presencaRepository.existsByPassageiroIdAndDataPresencaAndCompanyId(passageiroId, dataPresenca, companyId);
    }
}
