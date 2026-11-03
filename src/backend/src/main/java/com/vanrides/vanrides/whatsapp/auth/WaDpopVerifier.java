package com.vanrides.vanrides.whatsapp.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import org.springframework.stereotype.Component;

/** RFC9449 proof plus the documented v1 body digest, not an OAuth server. */
@Component
public final class WaDpopVerifier {
    public record PublicMaterial(String jkt, String json, ECPublicKey key) { }
    public record Proof(PublicMaterial material, String jti, String nonce, Instant issuedAt) {
        public String jkt() { return material.jkt(); }
    }
    private final WaSettings settings;
    private final WaClock clock;
    public WaDpopVerifier(WaSettings settings, WaClock clock) { this.settings = settings; this.clock = clock; }

    public static PublicMaterial publicMaterial(JsonNode value) {
        try {
            WaJson.fields(value, "kty", "crv", "x", "y");
            if (!"EC".equals(value.path("kty").asText()) || !"P-256".equals(value.path("crv").asText()))
                throw WaProblem.unauthorized();
            canonical32(value.path("x").asText()); canonical32(value.path("y").asText());
            String json = WaJson.canonical(value);
            Jwk<?> parsed = Jwks.parser().build().parse(json);
            if (!(parsed.toKey() instanceof ECPublicKey key)) throw WaProblem.unauthorized();
            return new PublicMaterial(parsed.thumbprint().toString(), json, key);
        } catch (Exception e) { throw WaProblem.unauthorized(); }
    }

    public Proof verify(String raw, String method, String path, byte[] body, String accessToken, boolean bindBody) {
        try {
            if (raw == null || raw.length() > 6000 || !raw.equals(raw.strip())) throw WaProblem.unauthorized();
            String[] parts = raw.split("\\.", -1);
            if (parts.length != 3) throw WaProblem.unauthorized();
            JsonNode header = WaJson.object(Base64.getUrlDecoder().decode(parts[0]));
            WaJson.fields(header, "typ", "alg", "jwk");
            if (!"dpop+jwt".equals(header.path("typ").asText()) || !"ES256".equals(header.path("alg").asText()))
                throw WaProblem.unauthorized();
            PublicMaterial material = publicMaterial(header.get("jwk"));
            JsonNode claims = WaJson.object(Base64.getUrlDecoder().decode(parts[1]));
            Set<String> allowed = Set.of("jti", "iat", "htm", "htu", "nonce", "ath", "vr_body_sha256");
            claims.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw WaProblem.unauthorized(); });
            Jwts.parser().verifyWith(material.key()).build().parseSignedClaims(raw);
            if (!method.equals(WaJson.text(claims, "htm", 3, 6))
                    || !(settings.baseUrl() + path).equals(WaJson.text(claims, "htu", 1, 512)))
                throw WaProblem.unauthorized();
            JsonNode issued = claims.get("iat");
            if (issued == null || !issued.isIntegralNumber() || !issued.canConvertToLong()) throw WaProblem.unauthorized();
            long seconds = issued.longValue();
            long now = clock.now().getEpochSecond();
            if (seconds < now - 60 || seconds > now + 10) throw WaProblem.unauthorized();
            String jti = WaJson.text(claims, "jti", 16, 128);
            if (!jti.matches("[A-Za-z0-9_-]{16,128}")) throw WaProblem.unauthorized();
            String nonce = claims.has("nonce") ? WaJson.text(claims, "nonce", 43, 43) : null;
            if (nonce != null) canonical32(nonce);
            if (accessToken != null) {
                String ath = WaJson.text(claims, "ath", 43, 43);
                if (!WaCrypto.same(ath, WaCrypto.base64(WaCrypto.sha256(accessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)))))
                    throw WaProblem.unauthorized();
            } else if (claims.has("ath")) throw WaProblem.unauthorized();
            if (bindBody && !WaCrypto.same(WaJson.text(claims, "vr_body_sha256", 43, 43), WaCrypto.base64(WaCrypto.sha256(body))))
                throw WaProblem.unauthorized();
            return new Proof(material, jti, nonce, Instant.ofEpochSecond(seconds));
        } catch (Exception e) { throw WaProblem.unauthorized(); }
    }

    public static void canonical32(String value) {
        if (!value.matches("[A-Za-z0-9_-]{43}")) throw WaProblem.unauthorized();
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        if (decoded.length != 32 || !WaCrypto.base64(decoded).equals(value)) throw WaProblem.unauthorized();
    }
}
