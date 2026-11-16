package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class WaPrivacySettingsTest {
    @Test void defaultsDisableApplyAndKeepSyntheticOnly(){
        var settings=new WaPrivacySettings(new MockEnvironment());
        assertThat(settings.enabled()).isFalse();assertThat(settings.apply()).isFalse();assertThat(settings.dryRun()).isTrue();
        assertThat(settings.syntheticOnly()).isTrue();assertThat(settings.journalDirectory()).isNull();
    }
    @Test void enabledPreviewNeedsNeitherJournalNorMvpOnline(){
        var settings=new WaPrivacySettings(new MockEnvironment().withProperty("WA_PRIVACY_ENABLED","true")
            .withProperty("WA_PRIVACY_JOURNAL_DIR","not-an-absolute-path"));
        assertThat(settings.enabled()).isTrue();assertThat(settings.dryRun()).isTrue();assertThat(settings.journalDirectory()).isNull();
    }
    @Test void applyRequiresEnabledAndExplicitAbsoluteDirectory(){
        for(var env:new MockEnvironment[]{new MockEnvironment().withProperty("WA_PRIVACY_APPLY","true"),
            new MockEnvironment().withProperty("WA_PRIVACY_ENABLED","true").withProperty("WA_PRIVACY_APPLY","true"),
            new MockEnvironment().withProperty("WA_PRIVACY_ENABLED","true").withProperty("WA_PRIVACY_APPLY","true").withProperty("WA_PRIVACY_JOURNAL_DIR","relative")})
            assertThatThrownBy(()->new WaPrivacySettings(env)).isInstanceOf(IllegalStateException.class);
    }
    @Test void flagsDoNotCoerceMalformedValues(){
        for(String flag:new String[]{"ENABLED","APPLY","ALLOW_REAL","RESTORE_MODE"})
            for(String value:new String[]{"TRUE","1","yes",""," true"})
                assertThatThrownBy(()->new WaPrivacySettings(new MockEnvironment().withProperty("WA_PRIVACY_"+flag,value))).isInstanceOf(IllegalStateException.class);
    }
    @Test void restoreModeRemainsUnsupportedRatherThanBypassingStartup(){
        assertThatThrownBy(()->new WaPrivacySettings(new MockEnvironment().withProperty("WA_PRIVACY_RESTORE_MODE","true")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("RESTORE_MODE_UNSUPPORTED");
    }
    @Test void realDataRequiresAllFourMvpBoundaries(){
        for(String field:new String[]{"vanrides.wa.mvp-online","vanrides.wa.environment","vanrides.wa.test-cohort-only","VANRIDES_WA_TEAM_WEB_ENABLED"}){
            var env=real();env.setProperty(field,field.endsWith("environment")?"other-environment":"false");
            assertThatThrownBy(()->new WaPrivacySettings(env)).isInstanceOf(IllegalStateException.class).hasMessageContaining("REAL_SCOPE_REQUIRED");
        }
    }
    @Test void realScopeStillDefaultsToPreview(){
        var settings=new WaPrivacySettings(real());assertThat(settings.syntheticOnly()).isFalse();assertThat(settings.dryRun()).isTrue();
    }
    @Test void parsingDoesNotProvisionTheJournal(){
        Path path=Path.of(System.getProperty("java.io.tmpdir"),"synthetic-journal-not-provisioned-"+java.util.UUID.randomUUID());
        var settings=new WaPrivacySettings(new MockEnvironment().withProperty("WA_PRIVACY_ENABLED","true").withProperty("WA_PRIVACY_APPLY","true")
            .withProperty("WA_PRIVACY_JOURNAL_DIR",path.toString()));
        assertThat(settings.journalDirectory()).isEqualTo(path);assertThat(path).doesNotExist();
    }
    private static MockEnvironment real(){return new MockEnvironment().withProperty("WA_PRIVACY_ENABLED","true").withProperty("WA_PRIVACY_ALLOW_REAL","true")
        .withProperty("vanrides.wa.mvp-online","true").withProperty("vanrides.wa.environment","vanrides-mvp-hml")
        .withProperty("vanrides.wa.test-cohort-only","true").withProperty("VANRIDES_WA_TEAM_WEB_ENABLED","true");}
}
