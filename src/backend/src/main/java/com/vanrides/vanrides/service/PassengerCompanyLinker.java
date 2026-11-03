package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.springframework.stereotype.Component;

@Component
class PassengerCompanyLinker {

    private final PassageiroRepository passageiroRepository;

    PassengerCompanyLinker(PassageiroRepository passageiroRepository) {
        this.passageiroRepository = passageiroRepository;
    }

    void link(Passageiro passageiro, Company company, Motorista motorista) {
        passageiro.setCompany(company);
        if (motorista != null) {
            passageiro.setMotorista(motorista);
        } else if (shouldClearCurrentDriver(passageiro, company)) {
            passageiro.setMotorista(null);
        }
        passageiroRepository.save(passageiro);
    }

    private boolean shouldClearCurrentDriver(Passageiro passageiro, Company company) {
        Motorista currentDriver = passageiro.getMotorista();
        return currentDriver != null
                && currentDriver.getCompany() != null
                && !company.getId().equals(currentDriver.getCompany().getId());
    }
}
