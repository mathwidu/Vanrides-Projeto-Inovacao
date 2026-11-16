package com.vanrides.vanrides.config;

import org.springframework.core.env.Environment;
import java.util.UUID;

/** Explicit functional MVP: normal product actors and credentials, within an authorized cohort. */
public final class MvpOnlineMode {
    public static final String ENVIRONMENT="vanrides-mvp-hml";
    private MvpOnlineMode() { }
    public static boolean enabled(Environment env) { return PublishedMvpConfiguration.flag(env,"vanrides.wa.mvp-online",false); }
    public static boolean rehearsalRoutines(Environment env) {
        return PublishedMvpConfiguration.flag(env,"vanrides.wa.mvp-rehearsal-routines",false);
    }
    public static UUID rehearsalCohort(Environment env) {
        String value=env.getProperty("VANRIDES_WA_TEAM_WEB_COHORT_ID","");
        try {
            UUID id=UUID.fromString(value);
            if(id.toString().equals(value) && !id.equals(new UUID(0,0))) return id;
        } catch(IllegalArgumentException invalid) { /* Only a canonical nonzero configured cohort is accepted. */ }
        throw new IllegalStateException("PUBLISHED_MVP_CONFIGURATION_INVALID: VANRIDES_WA_TEAM_WEB_COHORT_ID");
    }
    public static void validate(Environment env) {
        boolean rehearsal=rehearsalRoutines(env);
        if(!enabled(env)) {
            PublishedMvpConfiguration.require(!rehearsal,"WA_MVP_REHEARSAL_ROUTINES / WA_MVP_ONLINE");
            return;
        }
        PublishedMvpConfiguration.require(!TeamApiMode.enabled(env),"WA_MVP_ONLINE / WA_TEAM_API_ONLY");
        PublishedMvpConfiguration.validate(env);
        PublishedMvpConfiguration.require(ENVIRONMENT.equals(env.getProperty("vanrides.wa.environment","")),"WA_ENVIRONMENT");
        // A paused deployment must still boot so authenticated finish/stop remain available.
        PublishedMvpConfiguration.flag(env,"vanrides.wa.new-starts-enabled",false);
        boolean approach=PublishedMvpConfiguration.flag(env,"vanrides.wa.approach-enabled",false);
        boolean autoFinish=PublishedMvpConfiguration.flag(env,"vanrides.wa.auto-finish-enabled",false);
        if(rehearsal) {
            PublishedMvpConfiguration.require(PublishedMvpConfiguration.flag(env,"VANRIDES_WA_TEAM_WEB_ENABLED",false),"VANRIDES_WA_TEAM_WEB_ENABLED");
            rehearsalCohort(env);
        } else {
            PublishedMvpConfiguration.require(!approach,"WA_APPROACH_ENABLED");
            PublishedMvpConfiguration.require(!autoFinish,"WA_AUTO_FINISH_ENABLED");
            PublishedMvpConfiguration.require(env.getProperty("vanrides.wa.approach-reviewed-routine-ids","").isBlank(),"WA_APPROACH_REVIEWED_ROUTINE_IDS");
        }
        PublishedMvpConfiguration.publicOrigin(env,"vanrides.wa.start-link-base-url","WA_START_LINK_BASE_URL");
        PublishedMvpConfiguration.require(env.getProperty("vanrides.wa.public-base-url","").equals(env.getProperty("vanrides.wa.start-link-base-url","")),
            "WA_START_LINK_BASE_URL / WA_PUBLIC_BASE_URL");
    }
}
