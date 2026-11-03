package com.vanrides.vanrides.config;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class TeamApiModeTest {
    private void validate(org.springframework.mock.env.MockEnvironment env) {
        var guard=new PublicRuntimeConfigurationGuard();guard.setEnvironment(env);guard.postProcessBeanFactory(null);
    }
    @Test void exclusiveHomologAllowsDisabledEmailWhileRetainingPublicAndProdLikeGuards() {
        var env=TeamApiTestEnvironment.valid();validate(env);
        var config=PublicRuntimeConfiguration.from(env);assertThat(config.isPublicEnvironment()).isTrue();assertThat(config.isProdLikeEnvironment()).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"local","dev","prod","staging","homologacao","homolg","test",""})
    void noOtherProfileCanUseTeamMode(String profile) {
        var env=TeamApiTestEnvironment.valid();env.setActiveProfiles(profile.isEmpty()?new String[]{}:new String[]{profile});
        assertThatThrownBy(()->validate(env)).isInstanceOf(IllegalStateException.class).hasMessageContaining("SPRING_PROFILES_ACTIVE");
    }
    @Test void mixedProfilesNeverAcquireTheHomologException() {
        for(String other:List.of("local","dev","prod","test")) {
            var env=TeamApiTestEnvironment.valid();env.setActiveProfiles("homolog",other);
            assertThatThrownBy(()->validate(env)).isInstanceOf(IllegalStateException.class);
        }
    }
    @ParameterizedTest @ValueSource(strings={"disabled","disabeld","resned","smtp","none"})
    void disabledOrUnknownProvidersCannotFallBackToLoggingOutsideTheMode(String provider) {
        for(String profile:List.of("local","homolog","unknown","prod")) {
            var env=TeamApiTestEnvironment.valid().withProperty("vanrides.wa.team-api-only","false").withProperty("vanrides.email.provider",provider);env.setActiveProfiles(profile);
            assertThatThrownBy(()->validate(env)).isInstanceOf(IllegalStateException.class).hasMessageContaining("VANRIDES_EMAIL_PROVIDER");
        }
    }
    @ParameterizedTest @ValueSource(strings={"TRUE","1","yes","team-api",""})
    void unknownModeValuesFailClosed(String value) {
        assertThatThrownBy(()->validate(TeamApiTestEnvironment.valid().withProperty("vanrides.wa.team-api-only",value)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("vanrides.wa.team-api-only");
    }
    @Test void publicHardeningIsNotWeakenedByDisabledEmail() {
        for(var value:Map.of("vanrides.auth.jwt-secret","change-me","vanrides.auth.basic-enabled","true",
                "vanrides.rate-limit.enabled","false","server.http2.enabled","true").entrySet())
            assertThatThrownBy(()->validate(TeamApiTestEnvironment.valid().withProperty(value.getKey(),value.getValue()))).isInstanceOf(IllegalStateException.class);
    }
    @Test void externalOrAmbiguousConfigurationCannotStartTheTeamApi() {
        var forbidden=Map.ofEntries(Map.entry("vanrides.wa.environment","dev"),Map.entry("vanrides.wa.enabled","false"),
            Map.entry("vanrides.wa.new-starts-enabled","true"),Map.entry("vanrides.wa.approach-enabled","true"),Map.entry("vanrides.wa.test-cohort-only","false"),
            Map.entry("vanrides.maps-platform.enabled","true"),Map.entry("vanrides.routing.provider","google-directions"),Map.entry("vanrides.push.provider","external"),
            Map.entry("vanrides.email.provider","log"),Map.entry("vanrides.email.resend.api-key","PRIVATE_TEST_VALUE"),Map.entry("OPENAI_API_KEY","PRIVATE_TEST_VALUE"),
            Map.entry("vanrides.qa.auto-verify-email-domains","example.invalid"),Map.entry("VANRIDES_QA_AUTO_VERIFY_BOOTSTRAP_DOMAINS","example.invalid"),
            Map.entry("management.endpoints.web.exposure.include","*"),Map.entry("management.endpoint.health.show-details","always"));
        for(var value:forbidden.entrySet()) assertThatThrownBy(()->validate(TeamApiTestEnvironment.valid().withProperty(value.getKey(),value.getValue())))
            .as(value.getKey()).isInstanceOf(IllegalStateException.class).hasMessageNotContaining(value.getValue());
    }
    @ParameterizedTest @ValueSource(strings={"http://example.invalid","https://localhost","https://127.0.0.2","https://example.invalid/","https://user:private@example.invalid","https://example.invalid?private=value","https://example.invalid:8443"})
    void teamOriginMustBeAPublicHttpsOriginWithoutRedirectOrCredentials(String origin) {
        assertThatThrownBy(()->validate(TeamApiTestEnvironment.valid().withProperty("vanrides.wa.public-base-url",origin)))
            .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(origin);
    }
}
