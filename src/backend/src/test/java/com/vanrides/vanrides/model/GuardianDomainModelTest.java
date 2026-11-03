package com.vanrides.vanrides.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuardianDomainModelTest {

    @Test
    void shouldDefaultPassengerToAdultUntilExplicitlyMarkedAsMinor() {
        Passageiro passageiro = new Passageiro();

        assertThat(passageiro.isMenorDeIdade()).isFalse();

        passageiro.setMenorDeIdade(true);

        assertThat(passageiro.isMenorDeIdade()).isTrue();
    }

    @Test
    void shouldAllowExplicitGuardianPassengerLinkInMemory() {
        Company company = new Company("VanRides Default", "default");
        Guardian guardian = new Guardian();
        guardian.setCompany(company);
        guardian.setNome("Responsavel Teste");
        guardian.setEmail("guardian@vanrides.dev");
        guardian.setTelefone("51999990000");

        Passageiro passageiro = new Passageiro();
        passageiro.setCompany(company);
        passageiro.setNome("Passageiro Menor");
        passageiro.setMenorDeIdade(true);

        GuardianPassageiro link = new GuardianPassageiro();
        link.setCompany(company);
        link.setGuardian(guardian);
        link.setPassageiro(passageiro);

        assertThat(link.getGuardian()).isSameAs(guardian);
        assertThat(link.getPassageiro()).isSameAs(passageiro);
        assertThat(link.getCompany()).isSameAs(company);
        assertThat(link.getPassageiro().isMenorDeIdade()).isTrue();
    }
}
