package com.vanrides.vanrides.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import org.springframework.core.env.Environment;

/** Explicit synthetic exercise; never inferred from a host, provider name or missing credentials. */
public final class TeamApiMode {
    public static final String ENVIRONMENT="vanrides-team-hml";
    private TeamApiMode() { }
    public static boolean enabled(Environment env) { return flag(env,"vanrides.wa.team-api-only",false); }
    public static boolean exclusiveHomolog(Environment env) { return Arrays.equals(env.getActiveProfiles(),new String[]{"homolog"}); }
    public static void validate(Environment env) {
        if(!enabled(env)) return;
        require(exclusiveHomolog(env),"SPRING_PROFILES_ACTIVE");
        require("disabled".equals(env.getProperty("vanrides.email.provider","log")),"VANRIDES_EMAIL_PROVIDER");
        require(ENVIRONMENT.equals(env.getProperty("vanrides.wa.environment","")),"WA_ENVIRONMENT");
        require(flag(env,"vanrides.wa.enabled",false),"WA_MVP_ENABLED");
        require(!flag(env,"vanrides.wa.new-starts-enabled",true),"WA_NEW_STARTS_ENABLED");
        require(!flag(env,"vanrides.wa.approach-enabled",false),"WA_APPROACH_ENABLED");
        require(flag(env,"vanrides.wa.test-cohort-only",true),"WA_TEST_COHORT_ONLY");
        require(!flag(env,"vanrides.maps-platform.enabled",false),"VANRIDES_MAPS_PLATFORM_ENABLED");
        require("static".equals(env.getProperty("vanrides.routing.provider","static")),"VANRIDES_ROUTING_PROVIDER");
        require("none".equals(env.getProperty("vanrides.push.provider","none")),"VANRIDES_PUSH_PROVIDER");
        for(String key: new String[]{"vanrides.wa.approach-reviewed-routine-ids","vanrides.wa.start-link-base-url",
                "vanrides.qa.auto-verify-email-domains","vanrides.qa.auto-verify-bootstrap-domains",
                "VANRIDES_QA_AUTO_VERIFY_BOOTSTRAP_DOMAINS","vanrides.email.resend.api-key",
                "vanrides.email.verification.public-base-url","google.api.key","spring.mail.host",
                "spring.mail.username","spring.mail.password","OPENAI_API_KEY"})
            require(env.getProperty(key,"").isBlank(),key);
        require("health".equals(env.getProperty("management.endpoints.web.exposure.include","health")),"MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE");
        require("never".equals(env.getProperty("management.endpoint.health.show-details","never")),"MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS");
        try {
            URI origin=URI.create(env.getProperty("vanrides.wa.public-base-url",""));
            String host=origin.getHost();
            require("https".equals(origin.getScheme()) && host!=null && host.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)+")
                && host.substring(host.lastIndexOf('.')+1).matches(".*[a-z].*") && !Set.of("localhost","localhost.","0.0.0.0","[::1]").contains(host)
                && !host.startsWith("127.") && !host.endsWith(".localhost") && !host.endsWith(".localhost.")
                && origin.getUserInfo()==null && origin.getRawQuery()==null && origin.getRawFragment()==null
                && origin.getRawPath().isEmpty() && (origin.getPort()==-1 || origin.getPort()==443),"WA_PUBLIC_BASE_URL");
        } catch(IllegalArgumentException invalid) { throw invalid("WA_PUBLIC_BASE_URL"); }
    }
    private static boolean flag(Environment env,String key,boolean fallback) {
        String value=env.getProperty(key,Boolean.toString(fallback));
        require(Set.of("true","false").contains(value),key);return Boolean.parseBoolean(value);
    }
    private static void require(boolean condition,String key) { if(!condition) throw invalid(key); }
    private static IllegalStateException invalid(String key) { return new IllegalStateException("TEAM_API_CONFIGURATION_INVALID: "+key); }
}
