package com.vanrides.vanrides.whatsapp.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaTransportTestDatabaseIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_transport_reset");

    @Test
    void nextFixtureRemovesBothSchemasDataAndRestoresSeedsSequencesClockAndMigratedGuards() {
        var database = new WaTransportTestDatabase(POSTGRES);
        var first = database.fixture();
        var history = first.jdbc.queryForList("SELECT * FROM public.flyway_schema_history ORDER BY installed_rank");
        var sequences = sequences(first.jdbc);
        Long identityTable = first.jdbc.queryForObject("SELECT 'wa.identity'::regclass::oid::bigint", Long.class);
        assertThat(first.jdbc.queryForObject("SELECT max(version::integer) FROM public.flyway_schema_history", Integer.class))
                .isEqualTo(38);
        Long company = first.jdbc.queryForObject("""
                INSERT INTO public.company (nome,slug,created_at,updated_at)
                VALUES ('Synthetic reset company','synthetic-reset',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING id
                """, Long.class);
        assertThat(company).isEqualTo(2L);
        first.jdbc.update("""
                INSERT INTO public.motorista (nome,email,telefone,senha,company_id)
                VALUES ('Synthetic reset driver','BEFORE@EXAMPLE.INVALID','synthetic','synthetic-hash',?)
                """, company);
        assertThat(first.jdbc.queryForObject("SELECT normalized_email FROM public.login_identity", String.class))
                .isEqualTo("before@example.invalid");
        first.jdbc.update("UPDATE public.company SET nome='Changed seed' WHERE slug='default'");
        var actor = first.identity();
        first.alias(actor, "PN", "00000000000@s.whatsapp.net");
        first.event(actor, WaTransportTestSupport.json("{}"));
        first.time.advance(300);

        var next = database.fixture();
        assertThat(next.clock.now()).isEqualTo(WaTransportTestSupport.NOW);
        assertThat(next.jdbc.queryForObject("SELECT count(*) FROM wa.identity", Long.class)).isZero();
        assertThat(next.jdbc.queryForObject("SELECT count(*) FROM wa.identity_alias", Long.class)).isZero();
        assertThat(next.jdbc.queryForObject("SELECT count(*) FROM wa.integration_inbox", Long.class)).isZero();
        assertThat(next.jdbc.queryForObject("SELECT count(*) FROM public.motorista", Long.class)).isZero();
        assertThat(next.jdbc.queryForObject("SELECT count(*) FROM public.login_identity", Long.class)).isZero();
        assertThat(next.jdbc.queryForObject("SELECT count(*) FROM public.company", Long.class)).isEqualTo(1L);
        assertThat(next.jdbc.queryForObject("SELECT nome FROM public.company WHERE id=1 AND slug='default'", String.class))
                .isEqualTo("VanRides Default");
        assertThat(sequences(next.jdbc)).isEqualTo(sequences);
        assertThat(next.jdbc.queryForList("SELECT * FROM public.flyway_schema_history ORDER BY installed_rank"))
                .isEqualTo(history);
        assertThat(next.jdbc.queryForObject("SELECT 'wa.identity'::regclass::oid::bigint", Long.class)).isEqualTo(identityTable);
        var nextActor = next.identity();
        next.alias(nextActor, "PN", "00000000000@s.whatsapp.net");
        var nextEvent = next.event(nextActor, WaTransportTestSupport.json("{}"));
        assertThat(next.jdbc.queryForObject("SELECT received_order FROM wa.integration_inbox WHERE id=?", Long.class, nextEvent))
                .isEqualTo(1L);
        assertThat(next.jdbc.queryForObject("""
                INSERT INTO public.company (nome,slug,created_at,updated_at)
                VALUES ('Next synthetic company','next-reset',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) RETURNING id
                """, Long.class)).isEqualTo(2L);
        next.jdbc.update("""
                INSERT INTO public.motorista (nome,email,telefone,senha,company_id)
                VALUES ('Next synthetic driver','AFTER@EXAMPLE.INVALID','synthetic','synthetic-hash',1)
                """);
        assertThat(next.jdbc.queryForObject("SELECT normalized_email FROM public.login_identity", String.class))
                .isEqualTo("after@example.invalid");
    }

    @Test
    void unexpectedTableOrTriggerRefusesResetBeforeDeletingAnyScenarioData() {
        var database = new WaTransportTestDatabase(POSTGRES);
        var fixture = database.fixture();
        fixture.identity();
        fixture.jdbc.execute("CREATE TABLE wa.unreviewed_reset_data (id integer PRIMARY KEY)");
        try {
            fixture.jdbc.update("INSERT INTO wa.unreviewed_reset_data VALUES (1)");
            assertThatThrownBy(database::fixture).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("inventory changed");
            assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM wa.identity", Long.class)).isEqualTo(1L);
            assertThat(fixture.jdbc.queryForObject("SELECT id FROM wa.unreviewed_reset_data", Integer.class)).isEqualTo(1);
        } finally {
            fixture.jdbc.execute("DROP TABLE wa.unreviewed_reset_data");
        }
        try {
            fixture.jdbc.execute("""
                    CREATE FUNCTION wa.unreviewed_reset_trigger() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN RETURN NEW; END; $$
                    """);
            fixture.jdbc.execute("""
                    CREATE TRIGGER unreviewed_reset_trigger BEFORE INSERT ON wa.identity
                    FOR EACH ROW EXECUTE FUNCTION wa.unreviewed_reset_trigger()
                    """);
            assertThatThrownBy(database::fixture).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("inventory changed");
            assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM wa.identity", Long.class)).isEqualTo(1L);
        } finally {
            fixture.jdbc.execute("DROP TRIGGER IF EXISTS unreviewed_reset_trigger ON wa.identity");
            fixture.jdbc.execute("DROP FUNCTION IF EXISTS wa.unreviewed_reset_trigger()");
        }
        assertThat(database.fixture().jdbc.queryForObject("SELECT count(*) FROM wa.identity", Long.class)).isZero();
    }

    private static Map<String, String> sequences(JdbcTemplate jdbc) {
        Map<String, String> states = new LinkedHashMap<>();
        for (String sequence : jdbc.queryForList("""
                SELECT format('%I.%I',schemaname,sequencename) FROM pg_sequences
                WHERE schemaname IN ('public','wa') ORDER BY schemaname,sequencename
                """, String.class)) {
            states.put(sequence, jdbc.queryForObject("SELECT last_value::text || ':' || is_called::text FROM " + sequence, String.class));
        }
        return states;
    }
}
