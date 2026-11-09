package com.vanrides.vanrides.config;

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
        require(!MvpOnlineMode.enabled(env),"WA_TEAM_API_ONLY / WA_MVP_ONLINE");
        PublishedMvpConfiguration.validate(env);
        require(exclusiveHomolog(env),"SPRING_PROFILES_ACTIVE");
        require("disabled".equals(env.getProperty("vanrides.email.provider","log")),"VANRIDES_EMAIL_PROVIDER");
        require(ENVIRONMENT.equals(env.getProperty("vanrides.wa.environment","")),"WA_ENVIRONMENT");
        require(!flag(env,"vanrides.wa.new-starts-enabled",true),"WA_NEW_STARTS_ENABLED");
        require(!flag(env,"vanrides.wa.approach-enabled",false),"WA_APPROACH_ENABLED");
        for(String key: new String[]{"vanrides.wa.approach-reviewed-routine-ids","vanrides.wa.start-link-base-url"})
            require(env.getProperty(key,"").isBlank(),key);
    }
    private static boolean flag(Environment env,String key,boolean fallback) {
        String value=env.getProperty(key,Boolean.toString(fallback));
        require(Set.of("true","false").contains(value),key);return Boolean.parseBoolean(value);
    }
    private static void require(boolean condition,String key) { if(!condition) throw invalid(key); }
    private static IllegalStateException invalid(String key) { return new IllegalStateException("TEAM_API_CONFIGURATION_INVALID: "+key); }
}
