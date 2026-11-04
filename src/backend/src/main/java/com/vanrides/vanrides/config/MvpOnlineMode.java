package com.vanrides.vanrides.config;

import org.springframework.core.env.Environment;

/** Explicit functional MVP: normal product actors and credentials, within an authorized cohort. */
public final class MvpOnlineMode {
    public static final String ENVIRONMENT="vanrides-mvp-hml";
    private MvpOnlineMode() { }
    public static boolean enabled(Environment env) { return PublishedMvpConfiguration.flag(env,"vanrides.wa.mvp-online",false); }
    public static void validate(Environment env) {
        if(!enabled(env)) return;
        PublishedMvpConfiguration.require(!TeamApiMode.enabled(env),"WA_MVP_ONLINE / WA_TEAM_API_ONLY");
        PublishedMvpConfiguration.validate(env);
        PublishedMvpConfiguration.require(ENVIRONMENT.equals(env.getProperty("vanrides.wa.environment","")),"WA_ENVIRONMENT");
        PublishedMvpConfiguration.require(PublishedMvpConfiguration.flag(env,"vanrides.wa.new-starts-enabled",false),"WA_NEW_STARTS_ENABLED");
        PublishedMvpConfiguration.require(!PublishedMvpConfiguration.flag(env,"vanrides.wa.approach-enabled",false),"WA_APPROACH_ENABLED");
        PublishedMvpConfiguration.require(env.getProperty("vanrides.wa.approach-reviewed-routine-ids","").isBlank(),"WA_APPROACH_REVIEWED_ROUTINE_IDS");
        PublishedMvpConfiguration.publicOrigin(env,"vanrides.wa.start-link-base-url","WA_START_LINK_BASE_URL");
        PublishedMvpConfiguration.require(env.getProperty("vanrides.wa.public-base-url","").equals(env.getProperty("vanrides.wa.start-link-base-url","")),
            "WA_START_LINK_BASE_URL / WA_PUBLIC_BASE_URL");
    }
}
