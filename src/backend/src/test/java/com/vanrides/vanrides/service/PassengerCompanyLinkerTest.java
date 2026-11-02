package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PassengerCompanyLinkerTest {

    @Mock
    private PassageiroRepository passageiroRepository;

    @Test
    void assignsProvidedDriverWhenLinkingPassengerToCompany() {
        PassengerCompanyLinker linker = new PassengerCompanyLinker(passageiroRepository);
        Company company = company(66L);
        Motorista motorista = motorista(8L, company);
        Passageiro passageiro = passageiro(21L);

        linker.link(passageiro, company, motorista);

        assertThat(passageiro.getCompany()).isSameAs(company);
        assertThat(passageiro.getMotorista()).isSameAs(motorista);
        verify(passageiroRepository).save(passageiro);
    }

    @Test
    void preservesExistingDriverFromSameCompanyWhenNoDriverIsProvided() {
        PassengerCompanyLinker linker = new PassengerCompanyLinker(passageiroRepository);
        Company company = company(66L);
        Motorista currentDriver = motorista(8L, company);
        Passageiro passageiro = passageiro(22L);
        passageiro.setMotorista(currentDriver);

        linker.link(passageiro, company, null);

        assertThat(passageiro.getCompany()).isSameAs(company);
        assertThat(passageiro.getMotorista()).isSameAs(currentDriver);
        verify(passageiroRepository).save(passageiro);
    }

    @Test
    void clearsExistingDriverFromAnotherCompanyWhenNoDriverIsProvided() {
        PassengerCompanyLinker linker = new PassengerCompanyLinker(passageiroRepository);
        Company newCompany = company(66L);
        Company previousCompany = company(77L);
        Motorista previousDriver = motorista(9L, previousCompany);
        Passageiro passageiro = passageiro(23L);
        passageiro.setMotorista(previousDriver);

        linker.link(passageiro, newCompany, null);

        assertThat(passageiro.getCompany()).isSameAs(newCompany);
        assertThat(passageiro.getMotorista()).isNull();
        verify(passageiroRepository).save(passageiro);
    }

    private Passageiro passageiro(Long id) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Aluno " + id);
        passageiro.setEmail("aluno" + id + "@vanrides.dev");
        return passageiro;
    }

    private Company company(Long id) {
        Company company = new Company("Empresa " + id, "empresa-" + id);
        company.setId(id);
        return company;
    }

    private Motorista motorista(Long id, Company company) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome("Motorista " + id);
        motorista.setEmail("motorista" + id + "@vanrides.dev");
        motorista.setCompany(company);
        return motorista;
    }
}
