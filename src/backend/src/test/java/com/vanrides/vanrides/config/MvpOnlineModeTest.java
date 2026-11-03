package com.vanrides.vanrides.config;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class MvpOnlineModeTest {
    private void validate(org.springframework.mock.env.MockEnvironment env) {
        var guard=new PublicRuntimeConfigurationGuard();guard.setEnvironment(env);guard.postProcessBeanFactory(null);
    }
    @Test void onlineIsAnExplicitPublicModeWithNormalProductSettings() {
        var env=TeamApiTestEnvironment.validOnline();validate(env);
        var settings=new com.vanrides.vanrides.whatsapp.config.WaSettings(env);
        assertThat(settings.mvpOnline()).isTrue();assertThat(settings.teamApiOnly()).isFalse();assertThat(settings.newStartsEnabled()).isTrue();
        assertThat(settings.cohortOnly()).isTrue();assertThat(settings.approachEnabled()).isFalse();
        assertThat(PublicRuntimeConfiguration.from(env).isProdLikeEnvironment()).isTrue();
    }
    @Test void sandboxAndOnlineAreMutuallyExclusive() {
        assertThatThrownBy(()->validate(TeamApiTestEnvironment.validOnline().withProperty("vanrides.wa.team-api-only","true")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("WA_TEAM_API_ONLY");
    }
    @ParameterizedTest @ValueSource(strings={"local","dev","prod","homologacao","unknown",""})
    void onlineNeverRelaxesOtherProfiles(String profile) {
        var env=TeamApiTestEnvironment.validOnline();env.setActiveProfiles(profile.isEmpty()?new String[]{}:new String[]{profile});
        assertThatThrownBy(()->validate(env)).isInstanceOf(IllegalStateException.class).hasMessageContaining("SPRING_PROFILES_ACTIVE");
    }
    @Test void mixedProfilesRemainForbidden() {
        for(String other:List.of("local","dev","prod")) {
            var env=TeamApiTestEnvironment.validOnline();env.setActiveProfiles("homolog",other);
            assertThatThrownBy(()->validate(env)).isInstanceOf(IllegalStateException.class);
        }
    }
    @ParameterizedTest @ValueSource(strings={"TRUE","1","yes","online",""})
    void unknownOnlineFlagFailsClosed(String value) {
        assertThatThrownBy(()->validate(TeamApiTestEnvironment.validOnline().withProperty("vanrides.wa.mvp-online",value)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("vanrides.wa.mvp-online");
    }
    @Test void publicSecurityExternalProviderAndCohortLimitsRemainMandatory() {
        for(var entry:Map.ofEntries(Map.entry("vanrides.auth.jwt-secret","change-me"),Map.entry("vanrides.auth.basic-enabled","true"),
            Map.entry("vanrides.rate-limit.enabled","false"),Map.entry("vanrides.email.provider","log"),
            Map.entry("vanrides.wa.test-cohort-only","false"),Map.entry("vanrides.wa.environment","vanrides-team-hml"),
            Map.entry("vanrides.wa.new-starts-enabled","false"),Map.entry("vanrides.wa.approach-enabled","true"),
            Map.entry("vanrides.wa.approach-reviewed-routine-ids","00000000-0000-4000-8000-000000000001"),
            Map.entry("vanrides.maps-platform.enabled","true"),Map.entry("vanrides.push.provider","external"),
            Map.entry("vanrides.routing.provider","google-directions"),Map.entry("google.api.key","PRIVATE_VALUE"),
            Map.entry("vanrides.email.resend.api-key","PRIVATE_VALUE"),Map.entry("OPENAI_API_KEY","PRIVATE_VALUE"),
            Map.entry("management.endpoints.web.exposure.include","*"),Map.entry("management.endpoint.health.show-components","always")).entrySet())
            assertThatThrownBy(()->validate(TeamApiTestEnvironment.validOnline().withProperty(entry.getKey(),entry.getValue())))
                .as(entry.getKey()).isInstanceOf(IllegalStateException.class).hasMessageNotContaining(entry.getValue());
    }
    @Test void startAndApiOriginsMustBeTheSamePublicHttpsOrigin() {
        for(String origin:List.of("","http://example.invalid","https://localhost","https://different.invalid","https://example.invalid/path"))
            assertThatThrownBy(()->validate(TeamApiTestEnvironment.validOnline().withProperty("vanrides.wa.start-link-base-url",origin)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("WA_START_LINK_BASE_URL");
    }
}
