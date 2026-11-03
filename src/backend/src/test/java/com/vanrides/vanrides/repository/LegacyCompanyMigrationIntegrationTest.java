package com.vanrides.vanrides.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LegacyCompanyMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vanrides_legacy_test")
            .withUsername("vanrides_legacy")
            .withPassword("vanrides_legacy");

    @BeforeEach
    void resetDatabase() {
        LoginIdentityPostgresTestSupport.clean(postgres);
    }

    @Test
    void shouldAssignDefaultCompanyToLegacyDataWhenMigratingFromV1ToV2() throws Exception {
        flyway()
                .target("1")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword())) {
            Long motoristaId = insertLegacyMotorista(connection);
            Long passageiroComMotoristaId = insertLegacyPassageiro(connection, "passageiro.legado@vanrides.dev", motoristaId);
            Long passageiroSemMotoristaId = insertLegacyPassageiro(connection, "passageiro.sem.motorista@vanrides.dev", null);

            flyway()
                    .load()
                    .migrate();

            Long defaultCompanyId = queryCompanyIdBySlug(connection, "default");
            Long motoristaCompanyId = queryCompanyIdByTableAndId(connection, "motorista", motoristaId);
            Long passageiroComMotoristaCompanyId = queryCompanyIdByTableAndId(connection, "passageiro", passageiroComMotoristaId);
            Long passageiroSemMotoristaCompanyId = queryCompanyIdByTableAndId(connection, "passageiro", passageiroSemMotoristaId);

            assertThat(defaultCompanyId).isNotNull();
            assertThat(motoristaCompanyId).isEqualTo(defaultCompanyId);
            assertThat(passageiroComMotoristaCompanyId).isEqualTo(defaultCompanyId);
            assertThat(passageiroSemMotoristaCompanyId).isEqualTo(defaultCompanyId);
        }
    }

    @Test
    void shouldBackfillLegacyRouteBaseIntoRecurringServiceAndOperationalTrip() throws Exception {
        flyway()
                .target("15")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword())) {
            Long defaultCompanyId = queryCompanyIdBySlug(connection, "default");
            Long motoristaId = insertCompanyMotorista(connection, defaultCompanyId, "motorista.rota.legada@vanrides.dev");
            Long passageiroId = insertCompanyPassageiro(connection, defaultCompanyId, motoristaId, "passageiro.rota.legada@vanrides.dev");
            Long rotaBaseId = insertLegacyRotaBase(connection, defaultCompanyId, motoristaId);
            insertLegacyRotaBaseParada(connection, rotaBaseId, passageiroId);
            Long viagemId = insertLegacyViagem(connection, defaultCompanyId, rotaBaseId, motoristaId);
            insertLegacyViagemParada(connection, viagemId, passageiroId);

            flyway()
                    .load()
                    .migrate();

            Long servicoId = querySingleLong(connection,
                    "SELECT id FROM servico_recorrente WHERE rota_base_id = ?",
                    rotaBaseId);

            assertThat(servicoId).isNotNull();
            assertThat(querySingleLong(connection,
                    "SELECT motorista_padrao_id FROM servico_recorrente WHERE id = ?",
                    servicoId)).isEqualTo(motoristaId);
            assertThat(querySingleString(connection,
                    "SELECT nome FROM servico_recorrente WHERE id = ?",
                    servicoId)).isEqualTo("Linha Centro Manhã");
            assertThat(querySingleString(connection,
                    "SELECT tipo_operacao FROM servico_recorrente WHERE id = ?",
                    servicoId)).isEqualTo("IDA");
            assertThat(querySingleString(connection,
                    "SELECT horario_partida::text FROM servico_recorrente WHERE id = ?",
                    servicoId)).startsWith("07:30:00");
            assertThat(querySingleString(connection,
                    "SELECT destino_final_nome FROM servico_recorrente WHERE id = ?",
                    servicoId)).isEqualTo("Escola Central");

            assertThat(querySingleLong(connection,
                    "SELECT passageiro_id FROM servico_passageiro WHERE servico_recorrente_id = ?",
                    servicoId)).isEqualTo(passageiroId);
            assertThat(querySingleLong(connection,
                    "SELECT ordem_padrao FROM servico_passageiro WHERE servico_recorrente_id = ?",
                    servicoId)).isEqualTo(1L);

            assertThat(querySingleLong(connection,
                    "SELECT servico_recorrente_id FROM viagem WHERE id = ?",
                    viagemId)).isEqualTo(servicoId);
            assertThat(querySingleLong(connection,
                    "SELECT rota_base_id FROM viagem WHERE id = ?",
                    viagemId)).isEqualTo(rotaBaseId);
            assertThat(querySingleString(connection,
                    "SELECT servico_nome_snapshot FROM viagem WHERE id = ?",
                    viagemId)).isEqualTo("Linha Centro Manhã");
            assertThat(querySingleString(connection,
                    "SELECT tipo_operacao_snapshot FROM viagem WHERE id = ?",
                    viagemId)).isEqualTo("IDA");
            assertThat(querySingleString(connection,
                    "SELECT destino_final_nome_snapshot FROM viagem WHERE id = ?",
                    viagemId)).isEqualTo("Escola Central");
        }
    }

    private Long insertLegacyMotorista(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO motorista (nome, email, telefone, senha)
                VALUES (?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "Motorista Legado");
            statement.setString(2, "motorista.legado@vanrides.dev");
            statement.setString(3, "51999990100");
            statement.setString(4, LoginIdentityPostgresTestSupport.TEST_BCRYPT_HASH);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private Long insertCompanyMotorista(Connection connection, Long companyId, String email) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO motorista (nome, email, telefone, senha, company_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "Motorista Rota Legada");
            statement.setString(2, email);
            statement.setString(3, "51999990200");
            statement.setString(4, LoginIdentityPostgresTestSupport.TEST_BCRYPT_HASH);
            statement.setLong(5, companyId);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private Long insertLegacyPassageiro(Connection connection, String email, Long motoristaId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO passageiro
                    (nome, email, telefone, senha, endereco, bairro, cidade, estado, cep, motorista_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "Passageiro Legado");
            statement.setString(2, email);
            statement.setString(3, "51999990101");
            statement.setString(4, LoginIdentityPostgresTestSupport.TEST_BCRYPT_HASH);
            statement.setString(5, "Rua Legado");
            statement.setString(6, "Centro");
            statement.setString(7, "Porto Alegre");
            statement.setString(8, "RS");
            statement.setString(9, "90000-000");
            if (motoristaId == null) {
                statement.setObject(10, null);
            } else {
                statement.setLong(10, motoristaId);
            }
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private Long insertCompanyPassageiro(Connection connection, Long companyId, Long motoristaId, String email) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO passageiro
                    (nome, email, telefone, senha, endereco, bairro, cidade, estado, cep, motorista_id, company_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "Passageiro Rota Legada");
            statement.setString(2, email);
            statement.setString(3, "51999990201");
            statement.setString(4, LoginIdentityPostgresTestSupport.TEST_BCRYPT_HASH);
            statement.setString(5, "Rua Rota Legada");
            statement.setString(6, "Centro");
            statement.setString(7, "Porto Alegre");
            statement.setString(8, "RS");
            statement.setString(9, "90000-010");
            statement.setLong(10, motoristaId);
            statement.setLong(11, companyId);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private Long insertLegacyRotaBase(Connection connection, Long companyId, Long motoristaId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO rota_base
                    (company_id, motorista_id, nome, ativo, geracao_automatica_ativa, dias_operacao,
                     horario_partida, destino_final_nome, destino_final_endereco,
                     destino_final_latitude, destino_final_longitude, ativa)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, companyId);
            statement.setLong(2, motoristaId);
            statement.setString(3, "Linha Centro Manhã");
            statement.setBoolean(4, true);
            statement.setBoolean(5, true);
            statement.setString(6, "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY");
            statement.setObject(7, java.time.LocalTime.of(7, 30));
            statement.setString(8, "Escola Central");
            statement.setString(9, "Av. Central, 100");
            statement.setDouble(10, -30.0);
            statement.setDouble(11, -51.0);
            statement.setBoolean(12, true);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void insertLegacyRotaBaseParada(Connection connection, Long rotaBaseId, Long passageiroId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO rota_base_parada (rota_base_id, passageiro_id, ordem)
                VALUES (?, ?, ?)
                """)) {
            statement.setLong(1, rotaBaseId);
            statement.setLong(2, passageiroId);
            statement.setInt(3, 1);
            statement.executeUpdate();
        }
    }

    private Long insertLegacyViagem(Connection connection, Long companyId, Long rotaBaseId, Long motoristaId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO viagem
                    (company_id, rota_base_id, motorista_id, data_operacao, status, rota_versao, horario_partida)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, companyId);
            statement.setLong(2, rotaBaseId);
            statement.setLong(3, motoristaId);
            statement.setObject(4, java.time.LocalDate.of(2026, 5, 25));
            statement.setString(5, "PLANNED");
            statement.setInt(6, 1);
            statement.setObject(7, java.time.LocalTime.of(7, 30));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void insertLegacyViagemParada(Connection connection, Long viagemId, Long passageiroId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO viagem_parada (viagem_id, passageiro_id, ordem, status)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setLong(1, viagemId);
            statement.setLong(2, passageiroId);
            statement.setInt(3, 1);
            statement.setString(4, "PENDING");
            statement.executeUpdate();
        }
    }

    private Long queryCompanyIdBySlug(Connection connection, String slug) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM company WHERE slug = ?")) {
            statement.setString(1, slug);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private Long queryCompanyIdByTableAndId(Connection connection, String tableName, Long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT company_id FROM " + tableName + " WHERE id = ?")) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private Long querySingleLong(Connection connection, String sql, Long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private String querySingleString(Connection connection, String sql, Long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(false)
                .locations("classpath:db/migration");
    }
}
