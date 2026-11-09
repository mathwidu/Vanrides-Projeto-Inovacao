package com.vanrides.vanrides.whatsapp.team;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
final class WaTeamEvolution {
    private static final String PN_SUFFIX = "@s.whatsapp.net";
    private final WaTeamSettings settings;
    private final WaTeamRemote remote;

    static final class Contact {
        final String inputPn;
        final String pn;
        final String lid;
        Contact(String inputPn, String pn, String lid) { this.inputPn = inputPn; this.pn = pn; this.lid = lid; }
    }

    WaTeamEvolution(WaTeamSettings settings, WaTeamRemote remote) {
        this.settings = settings;
        this.remote = remote;
    }

    Contact verify(String phone) {
        if (!phone.matches("\\+[1-9][0-9]{7,14}")) throw WaProblem.invalid();
        String digits = phone.substring(1);
        var reply = remote.exchange(settings.evolutionOrigin().resolve("/chat/whatsappNumbers/" + settings.instance()),
            Map.of("apikey", settings.evolutionKey(), "Content-Type", "application/json"),
            WaJson.write(Map.of("numbers", List.of(digits))), settings.evolutionTls());
        return verified(digits, reply);
    }

    static Contact verified(String digits, WaTeamRemote.Reply reply) {
        JsonNode body = reply.body();
        if (reply.status() != 200 || body == null || !body.isArray() || body.size() != 1)
            throw WaTeamRemote.unavailable();
        JsonNode result = body.get(0);
        if (!result.path("exists").isBoolean()) throw WaTeamRemote.unavailable();
        if (!result.path("exists").booleanValue()) throw new WaTeamProblem(WaTeamProblem.Code.WHATSAPP_NOT_FOUND);
        String pn = result.path("jid").asText("");
        if (!pn.matches("[1-9][0-9]{7,14}@s\\.whatsapp\\.net")) throw WaTeamRemote.unavailable();
        String canonical = pn.substring(0, pn.length() - PN_SUFFIX.length());
        // Brazil's mobile ninth digit may be omitted in Evolution's canonical JID.
        if (!digits.equals(canonical) && !omitBrazilianNinth(digits).equals(omitBrazilianNinth(canonical)))
            throw WaTeamRemote.unavailable();
        if (result.hasNonNull("number")) {
            String echoed = result.path("number").asText("");
            if (!echoed.equals(digits) && !echoed.equals(canonical)) throw WaTeamRemote.unavailable();
        }
        String lid = result.hasNonNull("lid") ? result.path("lid").asText("") : null;
        if (lid != null && !lid.matches("[0-9]{1,32}@lid")) throw WaTeamRemote.unavailable();
        return new Contact(digits + PN_SUFFIX, pn, lid);
    }

    private static String omitBrazilianNinth(String digits) {
        return digits.matches("55[1-9][0-9]9[0-9]{8}") ? digits.substring(0, 4) + digits.substring(5) : digits;
    }
}
