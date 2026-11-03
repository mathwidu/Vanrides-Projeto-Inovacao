package com.vanrides.vanrides.whatsapp.auth;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.mock.env.MockEnvironment;

final class WaAuthTestSupport {
    static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    static final WaClock CLOCK = new WaClock(Clock.fixed(NOW, ZoneOffset.UTC));
    private WaAuthTestSupport() { }
    static MockEnvironment environment() {
        return new MockEnvironment().withProperty("vanrides.wa.enabled", "true")
            .withProperty("vanrides.wa.environment", "test")
            .withProperty("vanrides.wa.public-base-url", "http://127.0.0.1:8080")
            .withProperty("vanrides.wa.hash-key", key(11))
            .withProperty("vanrides.wa.response-key", key(22))
            .withProperty("vanrides.wa.operator-credential", key(33))
            .withProperty("vanrides.wa.operator-expires-at", "2035-01-01T00:00:00Z");
    }
    static WaSettings settings() { return new WaSettings(environment()); }
    static String key(int fill) { byte[] b = new byte[32]; Arrays.fill(b, (byte) fill); return WaCrypto.base64(b); }
    static KeyPair pair() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
    static ObjectNode jwk(KeyPair pair) {
        var key = (ECPublicKey) pair.getPublic();
        return WaJson.object(WaJson.write(Map.of("kty", "EC", "crv", "P-256",
            "x", coordinate(key.getW().getAffineX()), "y", coordinate(key.getW().getAffineY()))));
    }
    private static String coordinate(BigInteger number) {
        byte[] source = number.toByteArray(), padded = new byte[32];
        int length = Math.min(32, source.length); System.arraycopy(source, source.length - length, padded, 32 - length, length);
        return WaCrypto.base64(padded);
    }
    static Map<String, Object> claims(String method, String path, byte[] body) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("htm", method); out.put("htu", "http://127.0.0.1:8080" + path);
        out.put("iat", NOW.getEpochSecond()); out.put("jti", WaCrypto.randomToken());
        out.put("nonce", key(44));
        if (body != null) out.put("vr_body_sha256", WaCrypto.base64(WaCrypto.sha256(body)));
        return out;
    }
    static String sign(KeyPair signer, ObjectNode jwk, Map<String, Object> claims) {
        return Jwts.builder().header().type("dpop+jwt").add("jwk", WaJson.MAPPER.convertValue(jwk, Map.class)).and()
            .claims(claims).signWith(signer.getPrivate(), Jwts.SIG.ES256).compact();
    }
}
