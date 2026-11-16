package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.config.MvpOnlineMode;
import com.vanrides.vanrides.config.TeamApiTestEnvironment;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.tracking.WaRoutineEligibility;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaRoutineEligibilityIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_routine_eligibility");
    private WaTrackingTestSupport t;
    private UUID cohort;
    private WaRoutineEligibility eligibility;
    @BeforeEach void setup() {
        t=new WaTrackingTestSupport(POSTGRES);
        cohort=t.h.jdbc.queryForObject("SELECT test_cohort_id FROM wa.identity WHERE id=?",UUID.class,t.f.driver);
        t.h.jdbc.update("UPDATE wa.test_cohort SET environment=?,synthetic=false WHERE id=?",MvpOnlineMode.ENVIRONMENT,cohort);
        t.h.jdbc.update("UPDATE wa.driver_device SET environment=? WHERE id=?",MvpOnlineMode.ENVIRONMENT,t.device);
        var env=TeamApiTestEnvironment.validOnline().withProperty("vanrides.wa.mvp-rehearsal-routines","true")
            .withProperty("VANRIDES_WA_TEAM_WEB_ENABLED","true").withProperty("VANRIDES_WA_TEAM_WEB_COHORT_ID",cohort.toString())
            .withProperty("vanrides.wa.approach-enabled","true").withProperty("vanrides.wa.auto-finish-enabled","true");
        eligibility=new WaRoutineEligibility(t.h.jdbc,new WaSettings(env));
    }
    private boolean eligible() { return eligibility.reviewed(t.f.item.routine()); }
    @Test void onlyTheConfiguredRealCohortCanUseTheOptInWithoutACopiedRoutineId() {
        assertTrue(eligible()); assertFalse(eligibility.reviewed(UUID.randomUUID()));
        UUID other=UUID.randomUUID(); t.h.jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,?,false)",other,MvpOnlineMode.ENVIRONMENT);
        t.h.jdbc.update("UPDATE wa.identity SET test_cohort_id=? WHERE id=?",other,t.f.driver); assertFalse(eligible());
    }
    @Test void syntheticCohortAndAnEnvironmentMismatchAreRejected() {
        t.h.jdbc.update("UPDATE wa.test_cohort SET synthetic=true WHERE id=?",cohort); assertFalse(eligible());
        t.h.jdbc.update("UPDATE wa.test_cohort SET synthetic=false,environment='transport-test' WHERE id=?",cohort); assertFalse(eligible());
        t.h.jdbc.update("UPDATE wa.test_cohort SET environment=? WHERE id=?",MvpOnlineMode.ENVIRONMENT,cohort);
        t.h.jdbc.update("UPDATE wa.driver_device SET environment='transport-test' WHERE id=?",t.device); assertFalse(eligible());
    }
    @Test void inactiveOrRevokedDriverCannotKeepRehearsalEligibility() {
        t.h.jdbc.update("UPDATE wa.profile SET status='SUSPENDED' WHERE identity_id=?",t.f.driver); assertFalse(eligible());
        t.h.jdbc.update("UPDATE wa.profile SET status='ACTIVE',driver_enabled=false WHERE identity_id=?",t.f.driver); assertFalse(eligible());
        t.h.jdbc.update("UPDATE wa.profile SET driver_enabled=true WHERE identity_id=?",t.f.driver);
        t.h.jdbc.update("UPDATE wa.driver_authorization SET generation=generation+1 WHERE driver_id=?",t.f.driver); assertFalse(eligible());
        t.h.jdbc.update("UPDATE wa.driver_authorization SET generation=1 WHERE driver_id=?",t.f.driver);
        t.h.jdbc.update("UPDATE wa.driver_device SET status='REVOKED',revoked_at=? WHERE id=?",Timestamp.from(t.h.clock.now()),t.device); assertFalse(eligible());
    }
    @Test void inactiveRoutineAndSupportPauseAreRevalidated() {
        t.h.jdbc.update("UPDATE wa.routine SET state='PAUSED' WHERE id=?",t.f.item.routine()); assertFalse(eligible());
        t.h.jdbc.update("UPDATE wa.routine SET state='ACTIVE' WHERE id=?",t.f.item.routine()); assertTrue(eligible());
        t.h.jdbc.update("INSERT INTO wa.conversation_state(identity_id,stage,context_json) VALUES (?,'PAUSED','{}') ON CONFLICT(identity_id) DO UPDATE SET stage='PAUSED'",t.f.driver);
        assertFalse(eligible());
    }
    @Test void outsideTheOptInTheExistingReviewedUuidListRetainsItsMeaning() {
        var env=TeamApiTestEnvironment.validOnline().withProperty("vanrides.wa.mvp-online","false")
            .withProperty("vanrides.wa.approach-reviewed-routine-ids",t.f.item.routine().toString());
        var legacy=new WaRoutineEligibility(t.h.jdbc,new WaSettings(env));
        assertTrue(legacy.reviewed(t.f.item.routine())); assertFalse(legacy.reviewed(UUID.randomUUID()));
        assertFalse(new WaRoutineEligibility(t.h.jdbc,t.h.settings).reviewed(t.f.item.routine()));
    }
}
