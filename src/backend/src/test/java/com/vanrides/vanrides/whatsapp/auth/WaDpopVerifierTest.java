package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static com.vanrides.vanrides.whatsapp.auth.WaAuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class WaDpopVerifierTest {
    private final WaDpopVerifier verifier = new WaDpopVerifier(settings(), CLOCK);
    private final byte[] body = "{\"synthetic\":true}".getBytes(StandardCharsets.UTF_8);
    private static final String PATH = "/driver-app/v1/pairings/begin";
    @Test void verifiesRealSignatureAndRetainsOnlyPublicKey() throws Exception {
        var pair = pair(); var material = jwk(pair); var claims = claims("POST", PATH, body);
        var proof = verifier.verify(sign(pair, material, claims), "POST", PATH, body, null, true);
        assertThat(proof.jkt()).hasSize(43);
        assertThat(proof.material().json()).doesNotContain("\"d\"");
        assertThat(proof.jti()).isEqualTo(claims.get("jti"));
    }
    @Test void rejectsSignatureFromAnotherKeyAndPrivateJwk() throws Exception {
        var first = pair(); var second = pair(); var publicJwk = jwk(first);
        assertThatThrownBy(() -> verifier.verify(sign(second, publicJwk, claims("POST", PATH, body)), "POST", PATH, body, null, true))
            .isInstanceOf(WaProblem.class);
        var privateJwk = publicJwk.deepCopy().put("d", key(9));
        assertThatThrownBy(() -> WaDpopVerifier.publicMaterial(privateJwk))
            .isInstanceOf(WaProblem.class);
    }
    @Test void bindsExactBodyMethodAndFullPublicPath() throws Exception {
        var pair = pair(); String path = "/api/servlet" + PATH;
        String signed = sign(pair, jwk(pair), claims("POST", path, body));
        assertThat(verifier.verify(signed, "POST", path, body, null, true)).isNotNull();
        assertThatThrownBy(() -> verifier.verify(signed, "POST", PATH, body, null, true)).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> verifier.verify(signed, "GET", path, body, null, true)).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> verifier.verify(signed, "POST", path, "{}".getBytes(StandardCharsets.UTF_8), null, true)).isInstanceOf(WaProblem.class);
    }
    @Test void rejectsStaleFutureAndUnknownClaims() throws Exception {
        var pair = pair();
        for (long offset : new long[]{-61, 11}) {
            var claims = claims("POST", PATH, body); claims.put("iat", NOW.getEpochSecond() + offset);
            String signed = sign(pair, jwk(pair), claims);
            assertThatThrownBy(() -> verifier.verify(signed, "POST", PATH, body, null, true)).isInstanceOf(WaProblem.class);
        }
        var claims = claims("POST", PATH, body); claims.put("driverId", "chosen-by-client");
        String signed = sign(pair, jwk(pair), claims);
        assertThatThrownBy(() -> verifier.verify(signed, "POST", PATH, body, null, true)).isInstanceOf(WaProblem.class);
    }
    @Test void requiresAccessHashOnlyForAnAuthenticatedDevice() throws Exception {
        var pair = pair(); var claims = claims("GET", PATH, null); String token = key(7);
        String missing = sign(pair, jwk(pair), claims);
        assertThatThrownBy(() -> verifier.verify(missing, "GET", PATH, new byte[0], token, false)).isInstanceOf(WaProblem.class);
        claims.put("ath", WaCrypto.base64(WaCrypto.sha256(token.getBytes(StandardCharsets.UTF_8))));
        String signed = sign(pair, jwk(pair), claims);
        assertThat(verifier.verify(signed, "GET", PATH, new byte[0], token, false)).isNotNull();
        assertThatThrownBy(() -> verifier.verify(signed, "GET", PATH, new byte[0], key(8), false)).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> verifier.verify(signed, "GET", PATH, new byte[0], null, false)).isInstanceOf(WaProblem.class);
    }
    @Test void permitsMissingNonceOnlyForSubsequentChallengeAndRejectsMalformedProof() throws Exception {
        var pair = pair(); var claims = claims("POST", PATH, body); claims.remove("nonce");
        assertThat(verifier.verify(sign(pair, jwk(pair), claims), "POST", PATH, body, null, true).nonce()).isNull();
        for (String raw : new String[]{"", "a.b", "a.b.c", "x".repeat(6001)})
            assertThatThrownBy(() -> verifier.verify(raw, "POST", PATH, body, null, true)).isInstanceOf(WaProblem.class);
    }
    @Test void strictJsonRejectsDuplicateUnknownTrailingAndUnsafeVersion() {
        assertThatThrownBy(() -> WaJson.object("{\"a\":1,\"a\":2}")).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> WaJson.object("{} {}")).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> WaJson.fields(WaJson.object("{\"a\":1,\"actor\":2}"), "a")).isInstanceOf(WaProblem.class);
        assertThatThrownBy(() -> WaJson.version(WaJson.object("{\"expectedVersion\":9007199254740992}"))).isInstanceOf(WaProblem.class);
        assertThat(WaJson.text(WaJson.object("{\"name\":\"😀\"}"), "name", 1, 1)).isEqualTo("😀");
    }
    @Test void replayEncryptionBindsEnvironmentAndOperation() {
        var crypto = new WaCrypto(settings()); var encrypted = crypto.encrypt("kind:scope:key", "synthetic-response");
        assertThat(crypto.decrypt("kind:scope:key", encrypted)).isEqualTo("synthetic-response");
        assertThatThrownBy(() -> crypto.decrypt("different:scope:key", encrypted)).isInstanceOf(IllegalStateException.class);
        encrypted[encrypted.length - 1] ^= 1;
        assertThatThrownBy(() -> crypto.decrypt("kind:scope:key", encrypted)).isInstanceOf(IllegalStateException.class);
        for (int i = 0; i < 100; i++) assertThat(WaCrypto.userCode()).matches("[BCDFGHJKMNPQRSTVWXYZ]{8}");
    }
}
