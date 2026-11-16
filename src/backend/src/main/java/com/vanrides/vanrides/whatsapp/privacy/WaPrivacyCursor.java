package com.vanrides.vanrides.whatsapp.privacy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

/** Server-issued, short-lived continuation; callers cannot choose scope or skip a locked prefix. */
public final class WaPrivacyCursor {
    public record Value(String family, ObjectNode position) { }
    private static final Set<String> FAMILIES = Set.of("TERMINAL_7D", "TECHNICAL_7D", "PROFILE_30D", "HISTORY_90D");
    private final WaCrypto crypto;
    private final WaClock clock;
    public WaPrivacyCursor(WaCrypto crypto, WaClock clock) { this.crypto = crypto; this.clock = clock; }

    public String issue(WaPrincipal actor, String family, ObjectNode position) {
        String context = context(actor);
        if (family == null || !FAMILIES.contains(family) || position == null || WaJson.write(position).length() > 1200) throw WaProblem.invalid();
        ObjectNode body = WaJson.object("{}").put("version", 1).put("family", family)
            .put("expiresAt", clock.now().plusSeconds(300).toString());
        body.set("position", position.deepCopy());
        return "privacycursor_" + WaCrypto.base64(crypto.encrypt(context, WaJson.write(body)));
    }

    public Value read(WaPrincipal actor, String token) {
        String context = context(actor);
        try {
            if (token == null || !token.matches("privacycursor_[A-Za-z0-9_-]{40,2200}")) throw WaProblem.invalid();
            ObjectNode body = WaJson.object(crypto.decrypt(context, Base64.getUrlDecoder().decode(token.substring(14))));
            WaJson.fields(body, "version", "family", "expiresAt", "position");
            Instant expiry = Instant.parse(WaJson.text(body, "expiresAt", 20, 40));
            String family = WaJson.text(body, "family", 1, 30);
            if (!body.path("version").isIntegralNumber() || body.path("version").intValue() != 1
                || !FAMILIES.contains(family) || !body.path("position").isObject()
                || !expiry.isAfter(clock.now()) || expiry.isAfter(clock.now().plusSeconds(300))) throw WaProblem.invalid();
            return new Value(family, ((ObjectNode) body.path("position")).deepCopy());
        } catch (RuntimeException invalid) { throw WaProblem.invalid(); }
    }

    private static String context(WaPrincipal actor) {
        if (actor == null || actor.kind() != WaPrincipal.Kind.SYSTEM_JOB || actor.credentialId() == null
            || actor.instanceId() == null || actor.instanceId().isBlank() || actor.revoked()) throw WaProblem.denied();
        return "wa-privacy-cursor-v1\0" + actor.credentialId() + "\0" + actor.instanceId();
    }
}
