package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class WaStartOfferMigrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_start_offer_upgrade");
    @Test void upgradePreservesTheLegacyPendingIntentAndItsSinglePendingConstraint() {
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        var old=Flyway.configure().dataSource(source).locations("classpath:db/migration").target("46").cleanDisabled(false).load();
        old.clean(); old.migrate(); var jdbc=new JdbcTemplate(source);
        UUID driver=UUID.randomUUID(),device=UUID.randomUUID(),routine=UUID.randomUUID(),occurrence=UUID.randomUUID(),intent=UUID.randomUUID();
        jdbc.update("INSERT INTO wa.identity(id) VALUES (?)",driver);
        jdbc.update("INSERT INTO wa.profile(identity_id,role,status,display_name,driver_enabled) VALUES (?,'DRIVER','ACTIVE','Motorista sintético',true)",driver);
        jdbc.update("INSERT INTO wa.driver_authorization(driver_id,generation) VALUES (?,1)",driver);
        jdbc.update("INSERT INTO wa.driver_device(id,driver_id,jkt,public_jwk,generation,environment,platform,app_version) VALUES (?,?,repeat('A',43),'{}',1,'offer-upgrade','ANDROID','synthetic')",device,driver);
        jdbc.update("INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_latitude,destination_longitude,state,policy_version) VALUES (?,?,'Trajeto legado','OUTBOUND',ARRAY[2]::smallint[],'15:00','America/Sao_Paulo',0,0,'ACTIVE','wa-mvp-v1')",routine,driver);
        jdbc.update("INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at) VALUES (?,?,?,'2030-01-01',TIMESTAMPTZ '2030-01-01T18:00:00Z')",occurrence,routine,driver);
        jdbc.update("INSERT INTO wa.start_intent(id,driver_id,occurrence_id,device_id,token_hash,environment,expires_at,created_at,expected_version,roster_digest) VALUES (?,?,?,?,repeat('1',64),'offer-upgrade',TIMESTAMPTZ '2030-01-01T12:05:00Z',TIMESTAMPTZ '2030-01-01T12:00:00Z',7,repeat('2',64))",intent,driver,occurrence,device);
        String before=jdbc.queryForObject("SELECT to_jsonb(i)::text FROM wa.start_intent i WHERE id=?",String.class,intent);

        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("47").load().migrate();

        assertEquals(before,jdbc.queryForObject("SELECT (to_jsonb(i)-'offer_id'-'offer_position'-'delivery_outbox_id')::text FROM wa.start_intent i WHERE id=?",String.class,intent));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM wa.start_offer",Integer.class));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO wa.start_intent(id,driver_id,occurrence_id,device_id,token_hash,environment,expires_at,created_at) VALUES (?,?,?,?,repeat('3',64),'offer-upgrade',TIMESTAMPTZ '2030-01-01T12:05:00Z',TIMESTAMPTZ '2030-01-01T12:00:00Z')",UUID.randomUUID(),driver,occurrence,device));
    }
}
