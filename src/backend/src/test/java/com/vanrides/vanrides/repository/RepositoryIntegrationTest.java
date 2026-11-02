package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.GuardianPassageiro;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.Presenca;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.RotaBaseParada;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RepositoryIntegrationTest extends PassengerLocationReadRepositoryContract {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vanrides_test")
            .withUsername("vanrides_test")
            .withPassword("vanrides_test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Em banco efemero de teste queremos aplicar a V1 completa,
        // nao criar baseline e pular a migration inicial.
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private GuardianPassageiroRepository guardianPassageiroRepository;

    @Autowired
    private PresencaRepository presencaRepository;

    @Autowired
    private RotaBaseRepository rotaBaseRepository;

    @Autowired
    private RotaBaseParadaRepository rotaBaseParadaRepository;

    @Autowired
    private ViagemRepository viagemRepository;

    @Autowired
    private ViagemParadaRepository viagemParadaRepository;

    @Test
    void shouldSeedDefaultCompanyInMigration() {
        Company defaultCompany = companyRepository.findBySlug("default").orElse(null);

        assertThat(defaultCompany).isNotNull();
        assertThat(defaultCompany.getNome()).isEqualTo("VanRides Default");
    }

    @Test
    void shouldPersistAndFindMotoristaByEmail() {
        Company defaultCompany = companyRepository.findBySlug("default").orElseThrow();
        Motorista motorista = new Motorista();
        motorista.setNome("Motorista IT");
        motorista.setEmail("motorista.it@vanrides.dev");
        motorista.setTelefone("51990000001");
        motorista.setSenha("$2a$12$integration.hash");
        motorista.setCompany(defaultCompany);

        motoristaRepository.saveAndFlush(motorista);

        Motorista encontrado = motoristaRepository.findByEmail("motorista.it@vanrides.dev");
        assertThat(encontrado).isNotNull();
        assertThat(encontrado.getId()).isNotNull();
        assertThat(encontrado.getNome()).isEqualTo("Motorista IT");
    }

    @Test
    void shouldPersistAndFindPassageiroByEmailWithMotoristaRelation() {
        Company defaultCompany = companyRepository.findBySlug("default").orElseThrow();
        Motorista motorista = new Motorista();
        motorista.setNome("Motorista Base");
        motorista.setEmail("motorista.base@vanrides.dev");
        motorista.setTelefone("51990000002");
        motorista.setSenha("$2a$12$integration.hash");
        motorista.setCompany(defaultCompany);
        motorista = motoristaRepository.saveAndFlush(motorista);

        Passageiro passageiro = new Passageiro();
        passageiro.setNome("Passageiro IT");
        passageiro.setEmail("passageiro.it@vanrides.dev");
        passageiro.setTelefone("51990000003");
        passageiro.setSenha("$2a$12$integration.hash");
        passageiro.setEndereco("Rua Integração");
        passageiro.setBairro("Centro");
        passageiro.setCidade("Porto Alegre");
        passageiro.setEstado("RS");
        passageiro.setCep("90000-000");
        passageiro.setMotorista(motorista);
        passageiro.setCompany(defaultCompany);

        passageiroRepository.saveAndFlush(passageiro);

        Passageiro encontrado = passageiroRepository.findByEmail("passageiro.it@vanrides.dev");
        assertThat(encontrado).isNotNull();
        assertThat(encontrado.getId()).isNotNull();
        assertThat(encontrado.getMotorista()).isNotNull();
        assertThat(encontrado.getMotorista().getId()).isEqualTo(motorista.getId());
        assertThat(encontrado.getCompany()).isNotNull();
        assertThat(encontrado.getCompany().getSlug()).isEqualTo("default");
    }

    @Test
    void shouldPersistAndQueryPresencaByPassageiroAndMotoristaAndDate() {
        Company defaultCompany = companyRepository.findBySlug("default").orElseThrow();
        Motorista motorista = new Motorista();
        motorista.setNome("Motorista Presença");
        motorista.setEmail("motorista.presenca@vanrides.dev");
        motorista.setTelefone("51990000004");
        motorista.setSenha("$2a$12$integration.hash");
        motorista.setCompany(defaultCompany);
        motorista = motoristaRepository.saveAndFlush(motorista);

        Passageiro passageiro = new Passageiro();
        passageiro.setNome("Passageiro Presença");
        passageiro.setEmail("passageiro.presenca@vanrides.dev");
        passageiro.setTelefone("51990000005");
        passageiro.setSenha("$2a$12$integration.hash");
        passageiro.setEndereco("Rua Presença");
        passageiro.setBairro("Centro");
        passageiro.setCidade("Porto Alegre");
        passageiro.setEstado("RS");
        passageiro.setCep("90000-001");
        passageiro.setMotorista(motorista);
        passageiro.setCompany(defaultCompany);
        passageiro = passageiroRepository.saveAndFlush(passageiro);

        LocalDate data = LocalDate.now();
        Presenca presenca = new Presenca();
        presenca.setPassageiro(passageiro);
        presenca.setDataPresenca(data);
        presenca.setConfirmado(true);
        presencaRepository.saveAndFlush(presenca);

        boolean existeNoDia = presencaRepository.existsByPassageiroIdAndDataPresenca(passageiro.getId(), data);
        List<Presenca> porPassageiro = presencaRepository.findByPassageiroId(passageiro.getId());
        List<Presenca> porMotoristaData = presencaRepository.findByMotoristaIdAndData(motorista.getId(), data);

        assertThat(existeNoDia).isTrue();
        assertThat(porPassageiro).hasSize(1);
        assertThat(porMotoristaData).hasSize(1);
        assertThat(porMotoristaData.get(0).isConfirmado()).isTrue();
    }

    @Test
    void shouldPersistMinorPassengerGuardianDomainWithinSameCompany() {
        Company defaultCompany = companyRepository.findBySlug("default").orElseThrow();

        Motorista motorista = new Motorista();
        motorista.setNome("Motorista Guardian");
        motorista.setEmail("motorista.guardian@vanrides.dev");
        motorista.setTelefone("51990000006");
        motorista.setSenha("$2a$12$integration.hash");
        motorista.setCompany(defaultCompany);
        motorista = motoristaRepository.saveAndFlush(motorista);

        Passageiro passageiro = new Passageiro();
        passageiro.setNome("Passageiro Menor");
        passageiro.setEmail("passageiro.menor@vanrides.dev");
        passageiro.setTelefone("51990000007");
        passageiro.setSenha("$2a$12$integration.hash");
        passageiro.setEndereco("Rua Guardian");
        passageiro.setBairro("Centro");
        passageiro.setCidade("Porto Alegre");
        passageiro.setEstado("RS");
        passageiro.setCep("90000-002");
        passageiro.setMotorista(motorista);
        passageiro.setCompany(defaultCompany);
        passageiro.setMenorDeIdade(true);
        passageiro = passageiroRepository.saveAndFlush(passageiro);

        Guardian guardian = new Guardian();
        guardian.setCompany(defaultCompany);
        guardian.setNome("Responsavel IT");
        guardian.setEmail("responsavel.it@vanrides.dev");
        guardian.setTelefone("51990000008");
        guardian = guardianRepository.saveAndFlush(guardian);

        GuardianPassageiro link = new GuardianPassageiro();
        link.setCompany(defaultCompany);
        link.setGuardian(guardian);
        link.setPassageiro(passageiro);
        guardianPassageiroRepository.saveAndFlush(link);

        Guardian guardianEncontrado = guardianRepository
                .findByEmailAndCompanyId("responsavel.it@vanrides.dev", defaultCompany.getId())
                .orElseThrow();
        List<GuardianPassageiro> linksDoGuardian = guardianPassageiroRepository
                .findAllByGuardianIdAndCompanyId(guardianEncontrado.getId(), defaultCompany.getId());

        assertThat(passageiroRepository.findByIdAndCompanyId(passageiro.getId(), defaultCompany.getId()))
                .isPresent()
                .get()
                .matches(Passageiro::isMenorDeIdade);
        assertThat(guardianEncontrado.getId()).isEqualTo(guardian.getId());
        assertThat(linksDoGuardian).hasSize(1);
        assertThat(guardianPassageiroRepository.existsByGuardianIdAndPassageiroIdAndCompanyId(
                guardian.getId(), passageiro.getId(), defaultCompany.getId())).isTrue();
        assertThat(linksDoGuardian.get(0).getPassageiro().getId()).isEqualTo(passageiro.getId());
    }

    @Test
    void shouldPersistTripDomainWithinSameCompany() {
        Company defaultCompany = companyRepository.findBySlug("default").orElseThrow();

        Motorista motorista = new Motorista();
        motorista.setNome("Motorista Viagem");
        motorista.setEmail("motorista.viagem@vanrides.dev");
        motorista.setTelefone("51990000009");
        motorista.setSenha("$2a$12$integration.hash");
        motorista.setCompany(defaultCompany);
        motorista = motoristaRepository.saveAndFlush(motorista);

        Passageiro passageiro = new Passageiro();
        passageiro.setNome("Passageiro Viagem");
        passageiro.setEmail("passageiro.viagem@vanrides.dev");
        passageiro.setTelefone("51990000010");
        passageiro.setSenha("$2a$12$integration.hash");
        passageiro.setEndereco("Rua Viagem");
        passageiro.setBairro("Centro");
        passageiro.setCidade("Porto Alegre");
        passageiro.setEstado("RS");
        passageiro.setCep("90000-003");
        passageiro.setMotorista(motorista);
        passageiro.setCompany(defaultCompany);
        passageiro = passageiroRepository.saveAndFlush(passageiro);

        RotaBase rotaBase = new RotaBase();
        rotaBase.setCompany(defaultCompany);
        rotaBase.setMotorista(motorista);
        rotaBase.setNome("Rota Integracao");
        rotaBase.setAtivo(true);
        rotaBase = rotaBaseRepository.saveAndFlush(rotaBase);

        RotaBaseParada rotaBaseParada = new RotaBaseParada();
        rotaBaseParada.setRotaBase(rotaBase);
        rotaBaseParada.setPassageiro(passageiro);
        rotaBaseParada.setOrdem(1);
        rotaBaseParadaRepository.saveAndFlush(rotaBaseParada);

        Viagem viagem = new Viagem();
        viagem.setCompany(defaultCompany);
        viagem.setRotaBase(rotaBase);
        viagem.setMotorista(motorista);
        viagem.setDataOperacao(LocalDate.now());
        viagem.setStatus(ViagemStatus.PLANNED);
        viagem = viagemRepository.saveAndFlush(viagem);

        ViagemParada viagemParada = new ViagemParada();
        viagemParada.setViagem(viagem);
        viagemParada.setPassageiro(passageiro);
        viagemParada.setOrdem(1);
        viagemParadaRepository.saveAndFlush(viagemParada);

        assertThat(rotaBaseRepository.findAllWithMotoristaByCompanyIdOrderByNomeAsc(defaultCompany.getId()))
                .hasSize(1);
        assertThat(rotaBaseParadaRepository.findAllWithPassageiroByRotaBaseIdOrderByOrdemAsc(rotaBase.getId()))
                .hasSize(1);
        assertThat(viagemRepository.findByMotoristaIdAndDataOperacaoAndCompanyId(
                motorista.getId(), viagem.getDataOperacao(), defaultCompany.getId())).isNotEmpty();
        assertThat(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagem.getId()))
                .hasSize(1);
    }

    @Test
    void shouldReturnEmptyFilteredTripListWhenCompanyHasNoTripsOnDate() {
        Company defaultCompany = companyRepository.findBySlug("default").orElseThrow();

        List<Viagem> viagens = viagemRepository.findAllByCompanyIdAndDataOperacaoOrderByIdDesc(
                defaultCompany.getId(),
                LocalDate.of(2026, 3, 12)
        );

        assertThat(viagens).isEmpty();
    }

}
