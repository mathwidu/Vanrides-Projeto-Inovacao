package com.vanrides.vanrides.config;

import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class MvpRehearsalConfigurationTest {
    private static final String COHORT="00000000-0000-4000-8000-000000000140";
    private MockEnvironment rehearsal() {
        return TeamApiTestEnvironment.validOnline().withProperty("vanrides.wa.mvp-rehearsal-routines","true")
            .withProperty("VANRIDES_WA_TEAM_WEB_ENABLED","true").withProperty("VANRIDES_WA_TEAM_WEB_COHORT_ID",COHORT)
            .withProperty("vanrides.wa.approach-enabled","true").withProperty("vanrides.wa.auto-finish-enabled","true");
    }
    @Test void optInAllowsRehearsalWithoutReturningTrueForEveryRoutine() {
        var env=rehearsal(); MvpOnlineMode.validate(env); var settings=new WaSettings(env);
        assertThat(settings.mvpRehearsalRoutines()).isTrue(); assertThat(settings.mvpRehearsalCohort()).isEqualTo(UUID.fromString(COHORT));
        assertThat(settings.approachEnabled()).isTrue(); assertThat(settings.autoFinishEnabled()).isTrue();
        assertThat(settings.approachReviewed(UUID.randomUUID())).isFalse();
    }
    @Test void newStartsCanBePausedInBothTheOrdinaryAndRehearsalOnlineModes() {
        for(var env:new MockEnvironment[]{TeamApiTestEnvironment.validOnline(),rehearsal()}) {
            env.withProperty("vanrides.wa.new-starts-enabled","false");
            MvpOnlineMode.validate(env); assertThat(new WaSettings(env).newStartsEnabled()).isFalse();
        }
    }
    @Test void omittedOptInKeepsApproachAndAutoFinishDisabled() {
        var env=TeamApiTestEnvironment.validOnline(); var settings=new WaSettings(env);
        assertThat(settings.mvpRehearsalRoutines()).isFalse(); assertThat(settings.mvpRehearsalCohort()).isNull();
        for(String key:new String[]{"vanrides.wa.approach-enabled","vanrides.wa.auto-finish-enabled"})
            assertThatThrownBy(()->MvpOnlineMode.validate(TeamApiTestEnvironment.validOnline().withProperty(key,"true")))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void optInCannotEscapeModeEnvironmentCohortOrTeamConstraints() {
        for(var entry:Map.ofEntries(Map.entry("vanrides.wa.mvp-online","false"),Map.entry("vanrides.wa.team-api-only","true"),
            Map.entry("vanrides.wa.environment","prod"),Map.entry("vanrides.wa.test-cohort-only","false"),
            Map.entry("VANRIDES_WA_TEAM_WEB_ENABLED","false"),Map.entry("VANRIDES_WA_TEAM_WEB_COHORT_ID",""),
            Map.entry("vanrides.wa.enabled","false")).entrySet())
            assertThatThrownBy(()->new WaSettings(rehearsal().withProperty(entry.getKey(),entry.getValue())))
                .as(entry.getKey()).isInstanceOf(IllegalStateException.class);
        for(String profile:new String[]{"local","dev","prod","homologacao"}) {
            var env=rehearsal(); env.setActiveProfiles(profile);
            assertThatThrownBy(()->new WaSettings(env)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void cohortMustBeCanonicalAndNonzeroAndFlagsRemainStrict() {
        for(String id:new String[]{"00000000-0000-0000-0000-000000000000","PRIVATE_VALUE","1-1-1-1-1","00000000-0000-4000-8000-00000000014A"})
            assertThatThrownBy(()->new WaSettings(rehearsal().withProperty("VANRIDES_WA_TEAM_WEB_COHORT_ID",id)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(id);
        for(String key:new String[]{"vanrides.wa.mvp-rehearsal-routines","vanrides.wa.new-starts-enabled"})
            for(String value:new String[]{"TRUE","yes","1",""})
                assertThatThrownBy(()->new WaSettings(rehearsal().withProperty(key,value)))
                    .isInstanceOf(key.endsWith("mvp-rehearsal-routines") ? IllegalStateException.class : IllegalArgumentException.class)
                    .hasMessageContaining("CONFIGURATION_INVALID");
    }
}
