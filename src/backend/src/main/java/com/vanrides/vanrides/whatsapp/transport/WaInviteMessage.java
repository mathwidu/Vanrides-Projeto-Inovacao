package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

/** A destination entered in the chat stays inside the API. */
public final class WaInviteMessage {
    private static final String CONTEXT = "passenger-invite-contact-v1";
    private static final String FIELD = "inviteContactCiphertext";
    private static final Pattern CONTACT = Pattern.compile(
        "(?iu)^(?:(convidar(?: passageiro)?|enviar convite)\\s+)?(\\+?[0-9 ()-]{8,28})$");

    private WaInviteMessage() { }

    static boolean normalize(ObjectNode payload, String text, WaCrypto crypto) {
        String command = text.strip().toLowerCase(Locale.ROOT);
        if (command.equals("aceito receber convites") || command.equals("parar convites")) {
            payload.put("invitePermission", command.equals("aceito receber convites"));
            payload.put("text", command.toUpperCase(Locale.ROOT));
            return true;
        }
        var match = CONTACT.matcher(text.strip());
        if (!match.matches()) return false;
        String number = match.group(2).replaceAll("[ ()-]", "");
        String digits = number.startsWith("+") ? number.substring(1)
            : number.matches("[1-9][0-9]{9,10}") ? "55" + number : number;
        if (!(number.startsWith("+") || digits.startsWith("55") && digits.length() >= 12)
                || !digits.matches("[1-9][0-9]{7,14}")) return false;
        payload.put(FIELD, WaCrypto.base64(crypto.encrypt(CONTEXT, digits)));
        payload.put("inviteContactExplicit", match.group(1) != null);
        payload.put("text", "CONVITE [NÚMERO PROTEGIDO]");
        return true;
    }

    public static boolean hasContact(ObjectNode payload) { return payload.path(FIELD).isTextual(); }

    public static String contact(ObjectNode payload, WaCrypto crypto) {
        if (!hasContact(payload)) return null;
        String encoded = payload.path(FIELD).asText();
        if (!encoded.matches("[A-Za-z0-9_-]{48,60}")) return null;
        try {
            String digits = crypto.decrypt(CONTEXT, Base64.getUrlDecoder().decode(encoded));
            return digits.matches("[1-9][0-9]{7,14}") ? digits : null;
        } catch (RuntimeException invalid) { return null; }
    }
}
