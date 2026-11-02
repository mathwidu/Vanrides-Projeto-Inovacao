package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Evolution 2.3.7/Baileys webhook adapter. The provider envelope is never a stored payload. */
@Component
public final class WaEvolutionNormalizer {
    public record Alias(String type, String value) { }
    public record Event(String category, String messageId, String eventKey, String kind,
                        ObjectNode payload, String state, List<Alias> aliases) {
        boolean humanMessage() { return category.equals("MESSAGE") && state.equals("PENDING"); }
    }
    private static final Set<String> STATUSES = Set.of("SERVER_ACK", "DELIVERY_ACK", "READ", "PLAYED", "ERROR");
    private static final Set<String> CONNECTIONS = Set.of("open", "connecting", "close", "refused");
    private final WaCrypto crypto;

    public WaEvolutionNormalizer(WaCrypto crypto) { this.crypto = crypto; }

    public Event normalize(ObjectNode envelope) {
        String event = WaJson.text(envelope, "event", 1, 80);
        return switch (event) {
            case "messages.upsert" -> message(envelope);
            case "messages.update" -> status(envelope);
            case "connection.update" -> connection(envelope);
            case "messages.edited" -> ignored(envelope, "EDITED");
            case "send.message", "send.message.update" -> ignored(envelope, "ECHO");
            default -> ignored(envelope, "UNSUPPORTED_EVENT");
        };
    }

    private Event message(ObjectNode envelope) {
        JsonNode data = envelope.path("data"), key = data.path("key");
        String id = opaqueId(key.get("id"));
        if (!data.isObject() || !key.isObject() || id == null) return ignored(envelope, "INVALID_MESSAGE");
        if (!key.path("fromMe").isBoolean()) return skippedMessage(id, "INVALID_MESSAGE");
        if (key.path("fromMe").booleanValue()) return ignored(envelope, "ECHO");
        String remote = string(key.get("remoteJid"));
        if (remote != null && remote.endsWith("@g.us")) return ignored(envelope, "GROUP");
        if (remote != null && remote.endsWith("@broadcast")) return ignored(envelope, "BROADCAST");
        JsonNode message = data.path("message");
        if (message.has("protocolMessage") || message.has("editedMessage")
                || Set.of("protocolMessage", "editedMessage").contains(data.path("messageType").asText()))
            return ignored(envelope, "EDITED");
        Alias primary = alias(remote);
        if (primary == null) return skippedMessage(id, "INVALID_SENDER");
        Alias alternate = alias(string(key.get("remoteJidAlt")));
        List<Alias> aliases = List.of(primary);
        if (alternate != null && !primary.equals(alternate)) {
            if (primary.type().equals(alternate.type())) return skippedMessage(id, "ALIAS_CONFLICT");
            aliases = List.of(primary, alternate);
        }
        if (!message.isObject()) return unsupported(id, aliases, "UNSUPPORTED_CONTENT");
        if (message.has("liveLocationMessage")) return unsupported(id, aliases, "LIVE_LOCATION");
        JsonNode location = message.get("locationMessage");
        if (location != null) {
            if (message.has("conversation") || message.has("extendedTextMessage"))
                return unsupported(id, aliases, "AMBIGUOUS_CONTENT");
            if (!location.isObject() || location.has("isLive") && !location.path("isLive").isBoolean())
                return unsupported(id, aliases, "INVALID_PIN");
            if (location.path("isLive").asBoolean(false)) return unsupported(id, aliases, "LIVE_LOCATION");
            JsonNode latitude = location.get("degreesLatitude"), longitude = location.get("degreesLongitude");
            if (!coordinate(latitude, 90) || !coordinate(longitude, 180))
                return unsupported(id, aliases, "INVALID_PIN");
            ObjectNode payload = object();
            payload.putObject("pin").put("latitude", latitude.doubleValue()).put("longitude", longitude.doubleValue());
            messageMetadata(envelope, data, message, payload);
            return new Event("MESSAGE", id, null, "PIN", payload, "PENDING", aliases);
        }
        String conversation = string(message.get("conversation"));
        String extended = string(message.path("extendedTextMessage").get("text"));
        if (conversation != null && extended != null && !conversation.equals(extended))
            return unsupported(id, aliases, "AMBIGUOUS_CONTENT");
        String text = conversation != null ? conversation : extended;
        if (!validText(text)) return unsupported(id, aliases, "UNSUPPORTED_CONTENT");
        ObjectNode payload = object();
        String code = text.strip();
        var passenger = java.util.regex.Pattern.compile("(?iu)PASSAGEIRO[\\s\\p{Z}]+([A-Za-z0-9_-]{43})(?![A-Za-z0-9_-])").matcher(code);
        if (passenger.find()) {
            String secret = passenger.group(1);
            if (canonicalToken(secret) && code.matches("(?iu)^PASSAGEIRO[\\s\\p{Z}]+[A-Za-z0-9_-]{43}[.!?]?$"))
                payload.put("passengerInviteCodeHash", crypto.secretHash("passenger-invite", secret));
            else {
                payload.put("reason", "PASSENGER_INVITE_CONTENT"); messageMetadata(envelope, data, message, payload);
                return new Event("MESSAGE", id, null, "UNSUPPORTED", payload, "PENDING", aliases);
            }
        } else if (code.matches("[BCDFGHJKMNPQRSTVWXYZ]{8}"))
            payload.put("pairingCodeHash", crypto.secretHash("pairing-code", code));
        else if (canonicalToken(code)) payload.put("driverInviteCodeHash", crypto.secretHash("driver-bootstrap", code));
        else payload.put("text", text);
        messageMetadata(envelope, data, message, payload);
        return new Event("MESSAGE", id, null, "TEXT", payload, "PENDING", aliases);
    }

    private Event status(ObjectNode envelope) {
        JsonNode data = envelope.path("data");
        String id = opaqueId(data.get("keyId"));
        if (!data.isObject() || id == null) throw WaProblem.invalid();
        JsonNode rawStatus = data.path("status");
        String status = string(rawStatus);
        String eventKey = hash("status", object().put("id", id).set("status", rawStatus));
        if (!data.path("fromMe").isBoolean() || !data.path("fromMe").booleanValue())
            return callback("STATUS", eventKey, "SKIPPED", reason("INBOUND_STATUS"));
        if (status == null || !STATUSES.contains(status))
            return callback("STATUS", eventKey, "SKIPPED", reason("UNSUPPORTED_STATUS"));
        return callback("STATUS", eventKey, "PENDING", object().put("externalMessageId", id).put("status", status));
    }

    private Event connection(ObjectNode envelope) {
        String originalTime = string(envelope.get("date_time"));
        Instant time = instant(originalTime);
        if (time == null) throw WaProblem.invalid();
        JsonNode rawState = envelope.path("data").path("state");
        String state = string(rawState);
        String eventKey = hash("connection", object().put("date_time", originalTime).set("state", rawState));
        if (state == null || !CONNECTIONS.contains(state))
            return callback("CONNECTION", eventKey, "SKIPPED", reason("UNSUPPORTED_CONNECTION"));
        return callback("CONNECTION", eventKey, "COMPLETED", object().put("state", state).put("occurredAt", time.toString()));
    }

    private Event ignored(ObjectNode envelope, String reason) {
        // Only the digest survives. Headers, credentials and top-level profile/destination never enter even this key.
        ObjectNode input = object();
        for (String field : List.of("event", "data", "date_time"))
            if (envelope.has(field)) input.set(field, envelope.get(field));
        return callback("ECHO", hash("ignored", input), "SKIPPED", reason(reason));
    }
    private static Event skippedMessage(String id, String reason) {
        return new Event("MESSAGE", id, null, "UNSUPPORTED", reason(reason), "SKIPPED", List.of());
    }
    private static Event unsupported(String id, List<Alias> aliases, String reason) {
        return new Event("MESSAGE", id, null, "UNSUPPORTED", reason(reason), "PENDING", aliases);
    }
    private static Event callback(String category, String key, String state, ObjectNode payload) {
        return new Event(category, null, key, "UNSUPPORTED", payload, state, List.of());
    }
    private String hash(String purpose, JsonNode input) {
        return crypto.secretHash("evolution-" + purpose, WaJson.canonical(input));
    }
    static ObjectNode reason(String value) { return object().put("reason", value); }
    private static ObjectNode object() { return WaJson.MAPPER.createObjectNode(); }
    private static String string(JsonNode value) { return value != null && value.isTextual() ? value.textValue() : null; }
    private static String opaqueId(JsonNode value) {
        String id = string(value);
        return id != null && !id.isEmpty() && id.length() <= 160 && id.chars().allMatch(c -> c >= 33 && c <= 126) ? id : null;
    }
    private static Alias alias(String value) {
        if (value == null) return null;
        if (value.matches("[0-9]{1,32}@s\\.whatsapp\\.net")) return new Alias("PN", value);
        if (value.matches("[0-9]{1,32}@lid")) return new Alias("LID", value);
        return null;
    }
    private static boolean validText(String text) {
        return text != null && !text.isBlank() && text.length() <= 32768
            && text.codePoints().noneMatch(c -> c == 0 || c >= 0xD800 && c <= 0xDFFF);
    }
    private static boolean canonicalToken(String value) {
        if (!value.matches("[A-Za-z0-9_-]{43}")) return false;
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        return bytes.length == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value);
    }
    private static boolean coordinate(JsonNode value, int limit) {
        return value != null && value.isNumber() && Double.isFinite(value.doubleValue()) && Math.abs(value.doubleValue()) <= limit;
    }
    private static Instant instant(String value) {
        if (value == null || value.length() > 80) return null;
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (DateTimeException failure) { return null; }
    }
    private static void messageMetadata(ObjectNode envelope, JsonNode data, JsonNode message, ObjectNode payload) {
        // Evolution 2.3.7 moves extended-text contextInfo onto data.contextInfo.
        String reply = opaqueId(data.path("contextInfo").get("stanzaId"));
        if (reply == null) reply = opaqueId(message.path("extendedTextMessage").path("contextInfo").get("stanzaId"));
        if (reply == null) reply = opaqueId(message.path("locationMessage").path("contextInfo").get("stanzaId"));
        if (reply != null) payload.put("replyTo", reply);
        Instant occurred = null;
        JsonNode timestamp = data.path("messageTimestamp");
        if (timestamp.isIntegralNumber() && timestamp.canConvertToLong()) {
            long seconds = timestamp.longValue();
            if (seconds >= 0 && seconds <= 253402300799L) occurred = Instant.ofEpochSecond(seconds);
        }
        if (occurred == null) occurred = instant(string(envelope.get("date_time")));
        if (occurred != null) payload.put("occurredAt", occurred.toString());
    }
}
