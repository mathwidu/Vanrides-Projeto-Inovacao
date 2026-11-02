package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.GuardianPassageiro;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

abstract class PassengerLocationReadRepositoryContract {

    private static final Set<ViagemStatus> READABLE_TRIP_STATUSES = Set.of(
            ViagemStatus.PLANNED,
            ViagemStatus.IN_PROGRESS,
            ViagemStatus.COMPLETED
    );

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private ViagemRepository viagemRepository;

    @Autowired
    private ViagemParadaRepository viagemParadaRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private GuardianPassageiroRepository guardianPassageiroRepository;

    @Test
    void shouldAuthorizePassengerTripDriverOnlyForTodayNonCanceledAndSameCompany() {
        Company company = companyRepository.findBySlug("default").orElseThrow();
        Company otherCompany = persistCompany("Security Trip Other", "security-trip-other");
        LocalDate today = LocalDate.of(2026, 7, 16);
        Motorista targetDriver = persistMotorista(company, "security-trip-target");
        Motorista wrongDriver = persistMotorista(company, "security-trip-wrong");
        Motorista crossTenantDriver = persistMotorista(otherCompany, "security-trip-cross-driver");
        Passageiro passenger = persistPassageiro(company, wrongDriver, "security-trip-passenger");
        Passageiro directLinkOnly = persistPassageiro(
                company,
                targetDriver,
                "security-trip-direct-link-only"
        );
        Passageiro wrongPassenger = persistPassageiro(company, wrongDriver, "security-trip-wrong-passenger");
        Passageiro crossTenantPassenger = persistPassageiro(
                otherCompany,
                crossTenantDriver,
                "security-trip-cross-passenger"
        );

        assertThat(hasTripLink(directLinkOnly, targetDriver, company, today))
                .as("legacy passenger.motorista link must never grant GPS")
                .isFalse();

        Viagem validTrip = persistTrip(company, targetDriver, today, ViagemStatus.PLANNED);
        persistStop(validTrip, passenger);
        assertThat(hasTripLink(passenger, targetDriver, company, today)).isTrue();
        assertThat(hasTripLink(wrongPassenger, targetDriver, company, today)).isFalse();
        assertThat(hasTripLink(passenger, wrongDriver, company, today)).isFalse();

        EnumSet<ViagemStatus> readable = EnumSet.of(
                ViagemStatus.PLANNED,
                ViagemStatus.IN_PROGRESS,
                ViagemStatus.COMPLETED
        );
        for (ViagemStatus status : ViagemStatus.values()) {
            validTrip.setStatus(status);
            viagemRepository.saveAndFlush(validTrip);
            assertThat(hasTripLink(passenger, targetDriver, company, today))
                    .as("status %s", status)
                    .isEqualTo(readable.contains(status));
        }

        persistStop(persistTrip(company, targetDriver, today.minusDays(1), ViagemStatus.PLANNED), passenger);
        assertThat(hasTripLink(passenger, targetDriver, company, today)).isFalse();

        persistStop(persistTrip(company, targetDriver, today, ViagemStatus.PLANNED), crossTenantPassenger);
        assertThat(hasTripLink(crossTenantPassenger, targetDriver, company, today)).isFalse();

        persistStop(persistTrip(otherCompany, targetDriver, today, ViagemStatus.PLANNED), passenger);
        assertThat(hasTripLink(passenger, targetDriver, company, today)).isFalse();

        persistStop(persistTrip(company, crossTenantDriver, today, ViagemStatus.PLANNED), passenger);
        assertThat(hasTripLink(passenger, crossTenantDriver, company, today)).isFalse();
    }

    @Test
    void shouldAuthorizeGuardianTripDriverOnlyForReadableStatusDateAndTenant() {
        Company company = companyRepository.findBySlug("default").orElseThrow();
        Company otherCompany = persistCompany("Guardian Read Other", "guardian-read-other");
        LocalDate today = LocalDate.of(2026, 7, 16);
        Motorista targetDriver = persistMotorista(company, "guardian-read-target");
        Motorista pastOnlyDriver = persistMotorista(company, "guardian-read-past");
        Motorista crossTenantDriver = persistMotorista(otherCompany, "guardian-read-cross-driver");
        Passageiro linkedPassenger = persistPassageiro(company, targetDriver, "guardian-read-linked-passenger");
        Passageiro crossTenantPassenger = persistPassageiro(
                otherCompany,
                crossTenantDriver,
                "guardian-read-cross-passenger"
        );
        Guardian guardian = persistGuardian(company, "guardian-read-owner");
        Guardian crossTenantGuardian = persistGuardian(otherCompany, "guardian-read-cross-owner");
        persistGuardianLink(company, guardian, linkedPassenger);

        Viagem validTrip = persistTrip(company, targetDriver, today, ViagemStatus.PLANNED);
        persistStop(validTrip, linkedPassenger);
        EnumSet<ViagemStatus> readable = EnumSet.of(
                ViagemStatus.PLANNED,
                ViagemStatus.IN_PROGRESS,
                ViagemStatus.COMPLETED
        );
        for (ViagemStatus status : ViagemStatus.values()) {
            validTrip.setStatus(status);
            viagemRepository.saveAndFlush(validTrip);
            assertThat(hasGuardianTripLink(guardian, targetDriver, company, today))
                    .as("guardian status %s", status)
                    .isEqualTo(readable.contains(status));
        }

        persistStop(persistTrip(company, pastOnlyDriver, today.minusDays(1), ViagemStatus.PLANNED), linkedPassenger);
        assertThat(hasGuardianTripLink(guardian, pastOnlyDriver, company, today)).isFalse();

        persistStop(persistTrip(company, crossTenantDriver, today, ViagemStatus.PLANNED), linkedPassenger);
        assertThat(hasGuardianTripLink(guardian, crossTenantDriver, company, today)).isFalse();

        persistGuardianLink(company, guardian, crossTenantPassenger);
        persistStop(persistTrip(company, targetDriver, today, ViagemStatus.PLANNED), crossTenantPassenger);
        assertThat(hasGuardianTripLink(guardian, targetDriver, company, today)).isFalse();

        persistGuardianLink(company, crossTenantGuardian, linkedPassenger);
        assertThat(hasGuardianTripLink(crossTenantGuardian, targetDriver, company, today)).isFalse();
    }

    private boolean hasTripLink(Passageiro passenger,
                                Motorista motorista,
                                Company company,
                                LocalDate date) {
        return viagemRepository.existsByPassengerAndMotoristaOnDate(
                passenger.getId(), motorista.getId(), company.getId(), date, READABLE_TRIP_STATUSES);
    }

    private boolean hasGuardianTripLink(Guardian guardian,
                                        Motorista motorista,
                                        Company company,
                                        LocalDate date) {
        return viagemRepository.existsByGuardianLinkedPassengerAndMotoristaOnDate(
                guardian.getId(), motorista.getId(), company.getId(), date, READABLE_TRIP_STATUSES);
    }

    private Company persistCompany(String name, String slug) {
        return companyRepository.saveAndFlush(new Company(name, slug));
    }

    private Motorista persistMotorista(Company company, String suffix) {
        Motorista motorista = new Motorista();
        motorista.setNome("Motorista " + suffix);
        motorista.setEmail(suffix + "@vanrides.dev");
        motorista.setTelefone("5199" + Integer.toUnsignedString(suffix.hashCode()));
        motorista.setSenha("$2a$12$integration.hash");
        motorista.setCompany(company);
        return motoristaRepository.saveAndFlush(motorista);
    }

    private Passageiro persistPassageiro(Company company, Motorista motorista, String suffix) {
        Passageiro passageiro = new Passageiro();
        passageiro.setNome("Passageiro " + suffix);
        passageiro.setEmail(suffix + "@vanrides.dev");
        passageiro.setTelefone("5188" + Integer.toUnsignedString(suffix.hashCode()));
        passageiro.setSenha("$2a$12$integration.hash");
        passageiro.setEndereco("Rua " + suffix);
        passageiro.setBairro("Centro");
        passageiro.setCidade("Porto Alegre");
        passageiro.setEstado("RS");
        passageiro.setCep("90000-000");
        passageiro.setMotorista(motorista);
        passageiro.setCompany(company);
        return passageiroRepository.saveAndFlush(passageiro);
    }

    private Guardian persistGuardian(Company company, String suffix) {
        Guardian guardian = new Guardian();
        guardian.setCompany(company);
        guardian.setNome("Responsável " + suffix);
        guardian.setEmail(suffix + "@vanrides.dev");
        guardian.setTelefone("5177" + Integer.toUnsignedString(suffix.hashCode()));
        guardian.setSenha("$2a$12$integration.hash");
        return guardianRepository.saveAndFlush(guardian);
    }

    private void persistGuardianLink(Company company, Guardian guardian, Passageiro passageiro) {
        GuardianPassageiro link = new GuardianPassageiro();
        link.setCompany(company);
        link.setGuardian(guardian);
        link.setPassageiro(passageiro);
        guardianPassageiroRepository.saveAndFlush(link);
    }

    private Viagem persistTrip(Company company,
                                Motorista motorista,
                                LocalDate date,
                                ViagemStatus status) {
        Viagem viagem = new Viagem();
        viagem.setCompany(company);
        viagem.setMotorista(motorista);
        viagem.setDataOperacao(date);
        viagem.setStatus(status);
        return viagemRepository.saveAndFlush(viagem);
    }

    private void persistStop(Viagem viagem, Passageiro passageiro) {
        ViagemParada parada = new ViagemParada();
        parada.setViagem(viagem);
        parada.setPassageiro(passageiro);
        parada.setOrdem(1);
        viagemParadaRepository.saveAndFlush(parada);
    }
}
