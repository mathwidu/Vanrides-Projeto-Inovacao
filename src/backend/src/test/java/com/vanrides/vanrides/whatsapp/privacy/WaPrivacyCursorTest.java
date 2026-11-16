package com.vanrides.vanrides.whatsapp.privacy;

import static org.junit.jupiter.api.Assertions.*;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class WaPrivacyCursorTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final WaPrincipal actor = actor(WaPrincipal.Kind.SYSTEM_JOB, UUID.randomUUID(), "synthetic-a", false);

    private static WaPrincipal actor(WaPrincipal.Kind kind, UUID credential, String instance, boolean revoked) {
        return new WaPrincipal(kind, credential, null, null, instance, null, 0, revoked);
    }
    private static String key(int value) {
        byte[] bytes = new byte[32]; Arrays.fill(bytes, (byte) value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static WaCrypto crypto(String environment) {
        return new WaCrypto(new WaSettings(new MockEnvironment()
            .withProperty("vanrides.wa.enabled", "true").withProperty("vanrides.wa.environment", environment)
            .withProperty("vanrides.wa.public-base-url", "https://synthetic.invalid")
            .withProperty("vanrides.wa.hash-key", key(1)).withProperty("vanrides.wa.response-key", key(2))
            .withProperty("vanrides.wa.operator-credential", key(3))
            .withProperty("vanrides.wa.operator-expires-at", "2099-01-01T00:00:00Z")));
    }
    private static WaPrivacyCursor cursor(long seconds) {
        return new WaPrivacyCursor(crypto("privacy-local"), new WaClock(Clock.fixed(NOW.plusSeconds(seconds), ZoneOffset.UTC)));
    }

    @Test void roundTripKeepsTheServerSelectedFamilyAndPosition() {
        var position = WaJson.object("{}").put("after", "I:synthetic-prefix").put("asOf", NOW.toString());
        String token = cursor(0).issue(actor, "TERMINAL_7D", position);
        position.put("after", "client-mutated");
        var decoded = cursor(0).read(actor, token);
        assertEquals("TERMINAL_7D", decoded.family()); assertEquals("I:synthetic-prefix", decoded.position().path("after").asText());
        assertFalse(token.contains("synthetic-prefix"));
    }

    @Test void aContinuationCannotMoveToAnotherCredentialOrInstance() {
        String token = cursor(0).issue(actor, "TECHNICAL_7D", WaJson.object("{}"));
        for (WaPrincipal other : new WaPrincipal[] {
                actor(WaPrincipal.Kind.SYSTEM_JOB, UUID.randomUUID(), actor.instanceId(), false),
                actor(WaPrincipal.Kind.SYSTEM_JOB, actor.credentialId(), "synthetic-b", false)})
            assertThrows(WaProblem.class, () -> cursor(0).read(other, token));
        var otherEnvironment = new WaPrivacyCursor(crypto("another-privacy"), new WaClock(Clock.fixed(NOW, ZoneOffset.UTC)));
        assertThrows(WaProblem.class, () -> otherEnvironment.read(actor, token));
    }

    @Test void changedCiphertextAndInvalidEnvelopeAreRejected() {
        String token = cursor(0).issue(actor, "PROFILE_30D", WaJson.object("{}"));
        int at = token.length() / 2;
        String changed = token.substring(0, at) + (token.charAt(at) == 'A' ? 'B' : 'A') + token.substring(at + 1);
        assertThrows(WaProblem.class, () -> cursor(0).read(actor, changed));
        for (String bad : new String[] {"", "privacycursor_bad", "jobcursor_" + "a".repeat(32), token + "=", "x".repeat(3000)})
            assertThrows(WaProblem.class, () -> cursor(0).read(actor, bad));
    }

    @Test void expiryHasAnExactFiveMinuteBoundaryAndRejectsFutureClockDrift() {
        String token = cursor(0).issue(actor, "HISTORY_90D", WaJson.object("{}"));
        assertEquals("HISTORY_90D", cursor(299).read(actor, token).family());
        assertThrows(WaProblem.class, () -> cursor(300).read(actor, token));
        assertThrows(WaProblem.class, () -> cursor(-1).read(actor, token));
    }

    @Test void onlyTheSystemJobCanIssueOrUseAContinuation() {
        String token = cursor(0).issue(actor, "TERMINAL_7D", WaJson.object("{}"));
        for (WaPrincipal invalid : new WaPrincipal[] {null,
                actor(WaPrincipal.Kind.CHANNEL, actor.credentialId(), actor.instanceId(), false),
                actor(WaPrincipal.Kind.SYSTEM_JOB, actor.credentialId(), actor.instanceId(), true),
                actor(WaPrincipal.Kind.SYSTEM_JOB, null, actor.instanceId(), false)}) {
            assertThrows(WaProblem.class, () -> cursor(0).issue(invalid, "TERMINAL_7D", WaJson.object("{}")));
            assertThrows(WaProblem.class, () -> cursor(0).read(invalid, token));
        }
    }

    @Test void issuersMustChooseAKnownFamilyAndBoundedStructuredPosition() {
        assertThrows(WaProblem.class, () -> cursor(0).issue(actor, "GLOBAL_DELETE", WaJson.object("{}")));
        assertThrows(WaProblem.class, () -> cursor(0).issue(actor, "TERMINAL_7D", null));
        assertThrows(WaProblem.class, () -> cursor(0).issue(actor, "TERMINAL_7D", WaJson.object("{}").put("after", "x".repeat(1300))));
    }
}
