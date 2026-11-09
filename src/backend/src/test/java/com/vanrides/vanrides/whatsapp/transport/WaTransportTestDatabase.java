package com.vanrides.vanrides.whatsapp.transport;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Explicit per-class opt-in; callers must finish each scenario before requesting its next fixture. */
public final class WaTransportTestDatabase {
    // The sole seed on an empty database, copied from V2__company_base.sql.
    private static final String COMPANY_SEED = """
            INSERT INTO public.company (nome, slug, created_at, updated_at)
            VALUES ('VanRides Default', 'default', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;
    private static final String HISTORY = "SELECT * FROM public.flyway_schema_history ORDER BY installed_rank";
    private final PostgreSQLContainer<?> postgres;
    private DataSource source;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private List<String> tables;
    private List<Map<String, Object>> migrationHistory;
    private List<Map<String, Object>> triggerInventory;
    private String containerId;

    public WaTransportTestDatabase(PostgreSQLContainer<?> postgres) {
        this.postgres = postgres;
    }

    public DataSource nextDataSource() {
        return fixture().jdbc.getDataSource();
    }

    synchronized WaTransportTestSupport fixture() {
        if (!postgres.isRunning()) throw new IllegalStateException("The owned PostgreSQL container must be running");
        if (source == null) {
            initialize();
        } else {
            if (!containerId.equals(postgres.getContainerId())) {
                throw new IllegalStateException("The migrated PostgreSQL container was replaced");
            }
            transaction.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                jdbc.execute("SET LOCAL statement_timeout = '10s'");
                if (!tables.equals(tables(jdbc)) || !migrationHistory.equals(jdbc.queryForList(HISTORY))
                        || !triggerInventory.equals(triggers(jdbc))) {
                    throw new IllegalStateException("Test database inventory changed; refusing to truncate");
                }
                // All FK peers are named together. RESTRICT deliberately rejects dependencies outside this inventory.
                jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY RESTRICT");
                // Consume the company identity normally: next id and is_called match an empty Flyway migration.
                jdbc.update(COMPANY_SEED);
            });
        }
        return new WaTransportTestSupport(source, true);
    }

    private void initialize() {
        DataSource prepared = WaTransportTestSupport.rebuild(postgres);
        var preparedJdbc = new JdbcTemplate(prepared);
        List<String> preparedTables = tables(preparedJdbc);
        if (!preparedTables.contains("public.company") || !preparedTables.contains("wa.identity")) {
            throw new IllegalStateException("Required migrated test tables are missing");
        }
        // New migration seeds require an explicit reset recipe instead of silently discarding them.
        for (String table : preparedTables) {
            long expected = table.equals("public.company") ? 1 : 0;
            if (preparedJdbc.queryForObject("SELECT count(*) FROM " + table, Long.class) != expected) {
                throw new IllegalStateException("Unexpected migration seed in " + table);
            }
        }
        if (preparedJdbc.queryForObject("""
                SELECT count(*) FROM public.company
                WHERE id=1 AND nome='VanRides Default' AND slug='default'
                """, Long.class) != 1L) {
            throw new IllegalStateException("The default company migration seed changed");
        }
        if (preparedJdbc.queryForObject("""
                SELECT count(*) FROM pg_trigger t
                JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname IN ('public','wa') AND NOT t.tgisinternal AND (t.tgtype::integer & 32) <> 0
                """, Long.class) != 0L) {
            throw new IllegalStateException("TRUNCATE triggers need an explicit reset review");
        }
        tables = preparedTables;
        migrationHistory = preparedJdbc.queryForList(HISTORY);
        triggerInventory = triggers(preparedJdbc);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(prepared));
        jdbc = preparedJdbc;
        containerId = postgres.getContainerId();
        source = prepared;
    }

    private static List<String> tables(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT format('%I.%I', n.nspname, c.relname)
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname IN ('public','wa') AND c.relkind IN ('r','p')
                  AND NOT (n.nspname='public' AND c.relname='flyway_schema_history')
                ORDER BY n.nspname,c.relname
                """, String.class);
    }

    private static List<Map<String, Object>> triggers(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT n.nspname AS schema_name,c.relname AS table_name,t.tgname AS trigger_name,
                       t.tgenabled::text AS enabled,pg_get_triggerdef(t.oid) AS definition
                FROM pg_trigger t
                JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname IN ('public','wa') AND NOT t.tgisinternal
                ORDER BY n.nspname,c.relname,t.tgname
                """);
    }
}
