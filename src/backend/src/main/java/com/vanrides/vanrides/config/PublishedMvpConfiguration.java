package com.vanrides.vanrides.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import org.springframework.core.env.Environment;

/** Shared publication limits; neither mode relaxes the existing public-runtime guard. */
final class PublishedMvpConfiguration {
    private PublishedMvpConfiguration() { }
    static boolean flag(Environment env,String key,boolean fallback) {
        String value=env.getProperty(key,Boolean.toString(fallback));
        require(Set.of("true","false").contains(value),key);return Boolean.parseBoolean(value);
    }
    static void validate(Environment env) {
        require(Arrays.equals(env.getActiveProfiles(),new String[]{"homolog"}),"SPRING_PROFILES_ACTIVE");
        require("disabled".equals(env.getProperty("vanrides.email.provider","log")),"VANRIDES_EMAIL_PROVIDER");
        require(flag(env,"vanrides.wa.enabled",false),"WA_MVP_ENABLED");
        require(flag(env,"vanrides.wa.test-cohort-only",true),"WA_TEST_COHORT_ONLY");
        require(!flag(env,"vanrides.maps-platform.enabled",false),"VANRIDES_MAPS_PLATFORM_ENABLED");
        require("static".equals(env.getProperty("vanrides.routing.provider","static")),"VANRIDES_ROUTING_PROVIDER");
        require("none".equals(env.getProperty("vanrides.push.provider","none")),"VANRIDES_PUSH_PROVIDER");
        for(String key:new String[]{"vanrides.qa.auto-verify-email-domains","vanrides.qa.auto-verify-bootstrap-domains",
                "VANRIDES_QA_AUTO_VERIFY_BOOTSTRAP_DOMAINS","vanrides.email.resend.api-key",
                "vanrides.email.verification.public-base-url","google.api.key","spring.mail.host",
                "spring.mail.username","spring.mail.password","OPENAI_API_KEY"})
            require(env.getProperty(key,"").isBlank(),key);
        require("health".equals(env.getProperty("management.endpoints.web.exposure.include","health")),"MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE");
        require("never".equals(env.getProperty("management.endpoint.health.show-details","never")),"MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS");
        require("never".equals(env.getProperty("management.endpoint.health.show-components","never")),"MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS");
        publicOrigin(env,"vanrides.wa.public-base-url","WA_PUBLIC_BASE_URL");
    }
    static void publicOrigin(Environment env,String key,String name) {
        try {
            URI origin=URI.create(env.getProperty(key,""));String host=origin.getHost();
            require("https".equals(origin.getScheme()) && host!=null && host.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)+")
                && host.substring(host.lastIndexOf('.')+1).matches(".*[a-z].*") && !Set.of("localhost","localhost.","0.0.0.0","[::1]").contains(host)
                && !host.startsWith("127.") && !host.endsWith(".localhost") && !host.endsWith(".localhost.")
                && origin.getUserInfo()==null && origin.getRawQuery()==null && origin.getRawFragment()==null
                && origin.getRawPath().isEmpty() && (origin.getPort()==-1 || origin.getPort()==443),name);
        } catch(IllegalArgumentException invalid) { throw invalid(name); }
    }
    static void require(boolean condition,String key) { if(!condition) throw invalid(key); }
    private static IllegalStateException invalid(String key) { return new IllegalStateException("PUBLISHED_MVP_CONFIGURATION_INVALID: "+key); }
}
