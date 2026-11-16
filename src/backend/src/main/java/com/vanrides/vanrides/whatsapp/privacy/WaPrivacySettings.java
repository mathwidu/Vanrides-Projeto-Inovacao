package com.vanrides.vanrides.whatsapp.privacy;

import com.vanrides.vanrides.config.MvpOnlineMode;
import java.nio.file.Path;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Raw environment flags; enabling the application never implicitly enables data erasure. */
@Component
public final class WaPrivacySettings {
    private final boolean enabled,apply,allowReal;
    private final Path journalDirectory;
    public WaPrivacySettings(Environment environment){
        enabled=flag(environment,"ENABLED");apply=flag(environment,"APPLY");allowReal=flag(environment,"ALLOW_REAL");
        if(flag(environment,"RESTORE_MODE"))throw invalid("RESTORE_MODE_UNSUPPORTED");
        if(apply && !enabled)throw invalid("APPLY_REQUIRES_ENABLED");
        if(allowReal && (!MvpOnlineMode.enabled(environment)
            || !MvpOnlineMode.ENVIRONMENT.equals(environment.getProperty("vanrides.wa.environment",""))
            || !strictFlag(environment,"vanrides.wa.test-cohort-only")
            || !strictFlag(environment,"VANRIDES_WA_TEAM_WEB_ENABLED")))throw invalid("REAL_SCOPE_REQUIRED");
        Path path=null;
        if(apply){
            try{
                String raw=environment.getProperty("WA_PRIVACY_JOURNAL_DIR","");
                if(raw.isBlank())throw invalid("JOURNAL_REQUIRED");
                path=Path.of(raw);if(!path.isAbsolute())throw invalid("JOURNAL_REQUIRED");path=path.normalize();
            }catch(RuntimeException failure){throw invalid("JOURNAL_REQUIRED");}
        }
        journalDirectory=path;
    }
    public boolean enabled(){return enabled;}
    public boolean apply(){return apply;}
    public boolean dryRun(){return !apply;}
    public boolean syntheticOnly(){return !allowReal;}
    public Path journalDirectory(){return journalDirectory;}
    public WaPrivacyPolicy policy(){return new WaPrivacyPolicy(enabled,syntheticOnly());}
    private static boolean flag(Environment environment,String name){return strictFlag(environment,"WA_PRIVACY_"+name);}
    private static boolean strictFlag(Environment environment,String name){
        return switch(environment.getProperty(name,"false")){case "true"->true;case "false"->false;default->throw invalid("BOOLEAN_FLAG");};
    }
    private static IllegalStateException invalid(String reason){return new IllegalStateException("WA_PRIVACY_CONFIGURATION_INVALID:"+reason);}
}
