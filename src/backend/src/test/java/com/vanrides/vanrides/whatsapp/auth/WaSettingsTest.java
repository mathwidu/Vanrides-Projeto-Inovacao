package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.whatsapp.config.WaSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static com.vanrides.vanrides.whatsapp.auth.WaAuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class WaSettingsTest {
    @Test void disabledDefaultsDoNotNeedSecretsOrChangeLegacyBoot() {
        var settings = new WaSettings(new MockEnvironment());
        assertThat(settings.enabled()).isFalse(); assertThat(settings.configured()).isFalse();
    }
    @Test void partialSecretsNeverFallBackEvenWithFeatureDisabled() {
        var environment = new MockEnvironment().withProperty("vanrides.wa.hash-key", key(1));
        assertThatThrownBy(() -> new WaSettings(environment)).isInstanceOf(IllegalStateException.class).hasMessage("WA_CONFIGURATION_INVALID");
    }
    @Test void rejectsUnsafeOriginsAmbiguousFlagsAndIdenticalKeys() {
        for (String url : new String[]{"http://example.com", "https://user:secret@example.com", "https://example.com/path", "https://example.com?secret=1"})
            assertThatThrownBy(() -> new WaSettings(environment().withProperty("vanrides.wa.public-base-url", url)))
                .isInstanceOf(IllegalStateException.class).hasMessage("WA_CONFIGURATION_INVALID");
        assertThatThrownBy(() -> new WaSettings(environment().withProperty("vanrides.wa.hash-key", key(22))))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WaSettings(environment().withProperty("vanrides.wa.enabled", "yes")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
