package com.vanrides.vanrides.whatsapp.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Objects;
import java.util.UUID;

import static com.vanrides.vanrides.whatsapp.persistence.WhatsappPostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class WhatsappMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = postgres("vanrides_wa_migration");

    @BeforeEach
    void resetThroughV25() throws Exception {
        resetTo(postgres, "25");
    }

    @Test
    void shouldMigrateAnEmptyDatabaseWithoutBackfillingLegacyAccounts() throws Exception {
        resetTo(postgres, "26");
        try (Connection connection = open(postgres)) {
            assertThat(string(connection, """
                    SELECT version FROM public.flyway_schema_history
                    WHERE success ORDER BY installed_rank DESC LIMIT 1
                    """)).isEqualTo("26");
            assertThat(number(connection, """
                    SELECT count(*) FROM information_schema.tables WHERE table_schema='wa' AND table_type='BASE TABLE'
                    """)).isEqualTo(34);
            assertThat(number(connection, "SELECT count(*) FROM wa.identity")).isZero();
            assertThat(number(connection, "SELECT count(*) FROM public.motorista")).isZero();
            assertThat(number(connection, "SELECT count(*) FROM public.passageiro")).isZero();
            assertThat(number(connection, "SELECT count(*) FROM public.login_identity")).isZero();
            assertThat(number(connection, """
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema='wa' AND data_type='timestamp without time zone'
                    """)).isZero();
            assertThat(number(connection, """
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema='wa' AND column_name IN ('version','generation','lease_generation')
                    AND data_type <> 'bigint'
                    """)).isZero();
        }
    }

    @Test
    void shouldUpgradeARepresentativeV25FixtureWithoutChangingItsObjectsOrData() throws Exception {
        try (Connection connection = open(postgres)) {
            String passwordHash = "$2a$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u";
            update(connection, """
                    INSERT INTO public.motorista(nome,email,telefone,senha,company_id)
                    SELECT 'Synthetic legacy driver','driver.fixture@example.invalid','synthetic',?,id
                    FROM public.company WHERE slug='default'
                    """, passwordHash);
            update(connection, """
                    INSERT INTO public.passageiro(nome,email,telefone,senha,endereco,bairro,cidade,estado,cep,
                        company_id,motorista_id)
                    SELECT 'Synthetic legacy passenger','passenger.fixture@example.invalid','synthetic',?,
                        'Synthetic','Synthetic','Synthetic','XX','00000',company_id,id FROM public.motorista
                    """, passwordHash);
            update(connection, """
                    INSERT INTO public.presenca(passageiro_id,data_presenca,confirmado)
                    SELECT id,DATE '2030-01-01',TRUE FROM public.passageiro
                    """);
            String beforeObjects = publicObjectFingerprint(connection);
            String beforeAccounts = accountFingerprint(connection);
            String beforeSearchPath = string(connection, "SHOW search_path");

            migrateLatest(postgres);

            assertThat(publicObjectFingerprint(connection)).isEqualTo(beforeObjects);
            assertThat(accountFingerprint(connection)).isEqualTo(beforeAccounts);
            assertThat(string(connection, "SHOW search_path")).isEqualTo(beforeSearchPath);
            assertThat(number(connection, "SELECT count(*) FROM public.presenca WHERE confirmado")).isEqualTo(1);
            assertThat(number(connection, "SELECT count(*) FROM wa.identity")).isZero();
            assertThat(number(connection, """
                    SELECT count(*) FROM pg_constraint c
                    JOIN pg_class source ON source.oid=c.conrelid
                    JOIN pg_namespace sn ON sn.oid=source.relnamespace
                    JOIN pg_class target ON target.oid=c.confrelid
                    JOIN pg_namespace tn ON tn.oid=target.relnamespace
                    WHERE c.contype='f' AND sn.nspname='wa' AND tn.nspname <> 'wa'
                    """)).isZero();
        }
    }

    @Test
    void shouldRollbackTheWholeAdditiveMigrationTransaction() throws Exception {
        String migration;
        try (var source = Objects.requireNonNull(getClass().getResourceAsStream(
                "/db/migration/V26__whatsapp_foundation.sql"))) {
            migration = new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = open(postgres)) {
            String before = publicObjectFingerprint(connection);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute(migration);
            }
            identity(connection, null);
            connection.rollback();
            connection.setAutoCommit(true);

            assertThat(number(connection, "SELECT count(*) FROM pg_namespace WHERE nspname='wa'")).isZero();
            assertThat(publicObjectFingerprint(connection)).isEqualTo(before);
            assertThat(string(connection, """
                    SELECT version FROM public.flyway_schema_history WHERE success
                    ORDER BY installed_rank DESC LIMIT 1
                    """)).isEqualTo("25");
            migrateLatest(postgres);
            assertThat(number(connection, "SELECT count(*) FROM wa.identity")).isZero();
        }
    }

    @Test
    void shouldKeepOpaqueIdentitiesBeforeProfilesAndCohortDeletionClosedByDefault() throws Exception {
        migrateLatest(postgres);
        try (Connection connection = open(postgres)) {
            UUID cohort = UUID.randomUUID();
            update(connection, "INSERT INTO wa.test_cohort(id,environment) VALUES (?,'test')", cohort);
            UUID actor = identity(connection, null);
            update(connection, "UPDATE wa.identity SET test_cohort_id=? WHERE id=?", cohort, actor);
            update(connection, """
                    INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value)
                    VALUES (?,?,'SYNTHETIC',?,'LID','opaque-lid-without-phone')
                    """, UUID.randomUUID(), actor, INSTANCE);
            inbox(connection, actor);
            update(connection, "INSERT INTO wa.conversation_state(identity_id) VALUES (?)", actor);

            assertThat(number(connection, "SELECT count(*) FROM wa.profile WHERE identity_id=?", actor)).isZero();
            assertThat(number(connection, "SELECT count(*) FROM wa.test_cohort WHERE id=? AND NOT synthetic", cohort))
                    .isEqualTo(1);
            assertThat(string(connection, "SELECT to_regclass('identity')::text")).isNull();
            assertThat(string(connection, "SELECT to_regclass('wa.identity')::text")).isEqualTo("wa.identity");
            assertConstraint("23514", "ck_cohort_nonproduction", () -> update(connection,
                    "INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'production',TRUE)", UUID.randomUUID()));
        }
    }

    private static String publicObjectFingerprint(Connection connection) throws Exception {
        return string(connection, """
                SELECT string_agg(item,E'\n' ORDER BY item) FROM (
                    SELECT c.relkind::text || ':' || c.relname || ':' || a.attname || ':'
                        || format_type(a.atttypid,a.atttypmod) || ':' || a.attnotnull::text AS item
                    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                    JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
                    WHERE n.nspname='public'
                    UNION ALL
                    SELECT 'constraint:' || c.conname || ':' || pg_get_constraintdef(c.oid)
                    FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public'
                    UNION ALL
                    SELECT 'trigger:' || t.tgname || ':' || pg_get_triggerdef(t.oid)
                    FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
                    JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND NOT t.tgisinternal
                ) objects
                """);
    }

    private static String accountFingerprint(Connection connection) throws Exception {
        return string(connection, """
                SELECT string_agg(row_to_json(account)::text,E'\n' ORDER BY kind,id)
                FROM (
                    SELECT 'DRIVER' AS kind,id,nome,email,telefone,senha,company_id FROM public.motorista
                    UNION ALL
                    SELECT 'PASSENGER',id,nome,email,telefone,senha,company_id FROM public.passageiro
                ) account
                """);
    }
}
