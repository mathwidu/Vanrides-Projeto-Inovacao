package com.vanrides.vanrides.whatsapp.tracking;

import com.vanrides.vanrides.whatsapp.config.WaSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class WaTrackingSettingsTest {
    @Test void landingRequiresAnHttpsOriginWithoutCredentialsPathOrInvalidPort() {
        for(String origin:new String[]{"http://vanrides.invalid","https://user:synthetic@vanrides.invalid","https://vanrides.invalid/path",
                "https://vanrides.invalid?next=https://other.invalid","https://vanrides.invalid#token","https://localhost:0","https://localhost:65536"}) {
            var env=new MockEnvironment().withProperty("vanrides.wa.start-link-base-url",origin);
            assertThrows(IllegalStateException.class,()->new WaSettings(env));
        }
        for(String origin:new String[]{"https://vanrides.invalid","https://localhost:18443"}) {
            assertEquals(origin,new WaSettings(new MockEnvironment().withProperty("vanrides.wa.start-link-base-url",origin)).startLinkBaseUrl());
        }
        assertEquals("",new WaSettings(new MockEnvironment()).startLinkBaseUrl());
    }
}
