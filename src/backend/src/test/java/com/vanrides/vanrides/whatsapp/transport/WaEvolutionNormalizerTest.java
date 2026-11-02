package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaEvolutionNormalizerTest {
    private final WaCrypto crypto = new WaCrypto(WaTransportTestSupport.settings(true, true));
    private final WaEvolutionNormalizer normalizer = new WaEvolutionNormalizer(crypto);

    @Test void shouldExtractTextAndOnlyExplicitDifferentTypesOfAliases() {
        ObjectNode envelope = fixture("message-text-pn-lid");
        envelope.put("apikey", "discard-me").put("sender", "discard-me").put("destination", "discard-me");
        ((ObjectNode) envelope.path("data")).put("pushName", "discard-me").put("profilePictureUrl", "discard-me");
        var event = normalizer.normalize(envelope);
        assertThat(event.category()).isEqualTo("MESSAGE");
        assertThat(event.kind()).isEqualTo("TEXT"); assertThat(event.state()).isEqualTo("PENDING");
        assertThat(event.payload()).isEqualTo(WaJson.object("""
            {"text":"Olá, quero começar.","occurredAt":"2030-01-01T12:00:00Z"}
            """));
        assertThat(event.aliases()).containsExactly(
            new WaEvolutionNormalizer.Alias("PN", "000000000000@s.whatsapp.net"),
            new WaEvolutionNormalizer.Alias("LID", "000000000000000@lid"));
        assertThat(event.payload().toString()).doesNotContain("discard-me", "remoteJid", "apikey", "sender", "profile");
    }

    @Test void shouldKeepExtendedTextReplyReferenceWithoutQuotedContentAndNormalizeOffsetTime() {
        var event = normalizer.normalize(fixture("message-extended-reply"));
        assertThat(event.payload()).isEqualTo(WaJson.object("""
            {"text":"Concordo","replyTo":"synthetic-outbound-001","occurredAt":"2030-01-01T12:00:00Z"}
            """));
        assertThat(event.aliases()).containsExactly(new WaEvolutionNormalizer.Alias("LID", "000000000000000@lid"));
    }

    @Test void shouldAcceptTheContextMovedByEvolutionPrepareMessageAndDropInvalidReplyIds() {
        ObjectNode envelope = fixture("message-text-pn-lid");
        ((ObjectNode) envelope.path("data")).putObject("contextInfo").put("stanzaId", "synthetic-reply:opaque/001")
            .putObject("quotedMessage").putObject("locationMessage").put("degreesLatitude", 0).put("degreesLongitude", 0);
        assertThat(normalizer.normalize(envelope).payload().path("replyTo").asText()).isEqualTo("synthetic-reply:opaque/001");
        ((ObjectNode) envelope.path("data").path("contextInfo")).put("stanzaId", "x".repeat(161));
        assertThat(normalizer.normalize(envelope).payload().has("replyTo")).isFalse();
        assertThat(normalizer.normalize(envelope).payload().has("pin")).isFalse();
    }

    @Test void shouldAcceptStaticPinAtZeroAndAtTheInclusiveBounds() {
        ObjectNode envelope = fixture("message-pin-zero");
        var event = normalizer.normalize(envelope);
        assertThat(event.kind()).isEqualTo("PIN");
        assertThat(event.payload()).isEqualTo(WaJson.object("""
            {"pin":{"latitude":0.0,"longitude":0.0},"occurredAt":"2030-01-01T12:00:00Z"}
            """));
        ObjectNode pin = (ObjectNode) envelope.path("data").path("message").path("locationMessage");
        pin.put("isLive", false).put("degreesLatitude", -90).put("degreesLongitude", 180);
        assertThat(normalizer.normalize(envelope).kind()).isEqualTo("PIN");
    }

    @Test void shouldNeverExtractALiveOrInvalidPinAsCoordinates() {
        assertUnsupported(normalizer.normalize(fixture("message-live-location")), "LIVE_LOCATION");
        for (String invalid : List.of("{\"degreesLatitude\":91,\"degreesLongitude\":0}",
                "{\"degreesLatitude\":0,\"degreesLongitude\":181}",
                "{\"degreesLatitude\":\"0\",\"degreesLongitude\":0}",
                "{\"degreesLongitude\":0}", "{\"degreesLatitude\":0,\"degreesLongitude\":0,\"isLive\":null}")) {
            ObjectNode envelope = fixture("message-pin-zero");
            ((ObjectNode) envelope.path("data").path("message")).set("locationMessage", WaJson.object(invalid));
            assertUnsupported(normalizer.normalize(envelope), "INVALID_PIN");
        }
        ObjectNode envelope = fixture("message-pin-zero");
        ObjectNode pin = (ObjectNode) envelope.path("data").path("message").path("locationMessage");
        pin.put("isLive", true);
        assertUnsupported(normalizer.normalize(envelope), "LIVE_LOCATION");
        pin.remove("isLive"); pin.put("degreesLatitude", Double.NaN);
        assertUnsupported(normalizer.normalize(envelope), "INVALID_PIN");
        pin.put("degreesLatitude", 0).put("degreesLongitude", Double.POSITIVE_INFINITY);
        assertUnsupported(normalizer.normalize(envelope), "INVALID_PIN");
    }

    @Test void shouldTreatMediaAndQuotedOnlyPinAsUnsupportedHumanMessages() {
        ObjectNode envelope = fixture("message-pin-zero");
        ObjectNode message = (ObjectNode) envelope.path("data").path("message");
        message.removeAll().putObject("imageMessage").put("url", "https://media.invalid/unused").put("caption", "discard-me");
        assertUnsupported(normalizer.normalize(envelope), "UNSUPPORTED_CONTENT");
        message.removeAll().putObject("extendedTextMessage").putObject("contextInfo")
            .putObject("quotedMessage").putObject("locationMessage").put("degreesLatitude", 0).put("degreesLongitude", 0);
        assertUnsupported(normalizer.normalize(envelope), "UNSUPPORTED_CONTENT");
    }

    @Test void shouldRejectAmbiguousTopLevelContentWithoutKeepingEitherPayload() {
        ObjectNode envelope = fixture("message-pin-zero");
        ((ObjectNode) envelope.path("data").path("message")).put("conversation", "discard-me");
        assertUnsupported(normalizer.normalize(envelope), "AMBIGUOUS_CONTENT");
    }

    @Test void shouldNotSendInvalidUnicodeOrBlankTextToPostgresJsonb() {
        for (String value : List.of(" ", "synthetic\0text", "synthetic" + (char) 0xD800)) {
            ObjectNode envelope = fixture("message-text-pn-lid");
            ((ObjectNode) envelope.path("data").path("message")).put("conversation", value);
            assertUnsupported(normalizer.normalize(envelope), "UNSUPPORTED_CONTENT");
        }
    }

    @Test void shouldTurnCodesIntoPurposeBoundHashesWithoutExposingTheirText() {
        String invite = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        ObjectNode envelope = fixture("message-text-pn-lid");
        ObjectNode message = (ObjectNode) envelope.path("data").path("message");
        message.put("conversation", "  " + invite + "\n");
        ObjectNode payload = normalizer.normalize(envelope).payload();
        assertThat(payload.path("driverInviteCodeHash").asText()).isEqualTo(crypto.secretHash("driver-bootstrap", invite));
        assertThat(payload.has("text")).isFalse(); assertThat(payload.toString()).doesNotContain(invite);
        message.put("conversation", "BCDFGHJK"); payload = normalizer.normalize(envelope).payload();
        assertThat(payload.path("pairingCodeHash").asText()).isEqualTo(crypto.secretHash("pairing-code", "BCDFGHJK"));
        assertThat(payload.has("text")).isFalse(); assertThat(payload.toString()).doesNotContain("BCDFGHJK");
        message.put("conversation", "Meu código é BCDFGHJK"); payload = normalizer.normalize(envelope).payload();
        assertThat(payload.path("text").asText()).isEqualTo("Meu código é BCDFGHJK");
        assertThat(payload.has("pairingCodeHash")).isFalse();
    }

    @Test void shouldSkipEchoGroupBroadcastAndEditWithoutAnyHumanPayload() {
        for (String name : List.of("message-echo", "message-group")) {
            var event = normalizer.normalize(fixture(name));
            assertThat(event.category()).isEqualTo("ECHO"); assertThat(event.state()).isEqualTo("SKIPPED");
            assertThat(event.payload().size()).isEqualTo(1); assertThat(event.payload().has("reason")).isTrue();
            assertThat(event.aliases()).isEmpty();
        }
        ObjectNode envelope = fixture("message-text-pn-lid");
        ((ObjectNode) envelope.path("data").path("key")).put("remoteJid", "status@broadcast");
        assertThat(normalizer.normalize(envelope).payload()).isEqualTo(WaJson.object("{\"reason\":\"BROADCAST\"}"));
        envelope = fixture("message-text-pn-lid").put("event", "messages.edited");
        assertThat(normalizer.normalize(envelope).payload()).isEqualTo(WaJson.object("{\"reason\":\"EDITED\"}"));
        envelope.put("event", "messages.upsert");
        ((ObjectNode) envelope.path("data").path("message")).putObject("protocolMessage").putObject("editedMessage").put("conversation", "discard-me");
        assertThat(normalizer.normalize(envelope).category()).isEqualTo("ECHO");
    }

    @Test void shouldNotInferPhoneFromLidOrAssociateTwoDifferentAliasesOfTheSameType() {
        ObjectNode envelope = fixture("message-text-pn-lid");
        ObjectNode key = (ObjectNode) envelope.path("data").path("key");
        key.put("remoteJid", "000000000000000@lid").remove("remoteJidAlt");
        assertThat(normalizer.normalize(envelope).aliases()).containsExactly(new WaEvolutionNormalizer.Alias("LID", "000000000000000@lid"));
        key.put("remoteJidAlt", "000000000000000@lid");
        assertThat(normalizer.normalize(envelope).aliases()).hasSize(1);
        key.put("remoteJidAlt", "0000000000000000@lid");
        assertThat(normalizer.normalize(envelope).state()).isEqualTo("SKIPPED");
        assertThat(normalizer.normalize(envelope).payload()).isEqualTo(WaJson.object("{\"reason\":\"ALIAS_CONFLICT\"}"));
    }

    @Test void shouldNormalizeOnlyTextualOutboundStatusesAndDedupEachStatusIndependently() {
        ObjectNode envelope = fixture("status-read");
        var read = normalizer.normalize(envelope);
        assertThat(read.category()).isEqualTo("STATUS"); assertThat(read.state()).isEqualTo("PENDING");
        assertThat(read.payload()).isEqualTo(WaJson.object("{\"externalMessageId\":\"synthetic-outbound-001\",\"status\":\"READ\"}"));
        envelope.put("date_time", "2030-01-01T12:01:00Z");
        assertThat(normalizer.normalize(envelope).eventKey()).isEqualTo(read.eventKey());
        ObjectNode data = (ObjectNode) envelope.path("data");
        data.put("status", "DELIVERY_ACK");
        assertThat(normalizer.normalize(envelope).eventKey()).isNotEqualTo(read.eventKey());
        for (String status : List.of("SERVER_ACK", "DELIVERY_ACK", "READ", "PLAYED", "ERROR")) {
            data.put("status", status);
            assertThat(normalizer.normalize(envelope).state()).isEqualTo("PENDING");
        }
        data.put("status", 4);
        assertThat(normalizer.normalize(envelope).payload()).isEqualTo(WaJson.object("{\"reason\":\"UNSUPPORTED_STATUS\"}"));
        data.put("status", "READ").put("fromMe", false);
        assertThat(normalizer.normalize(envelope).payload()).isEqualTo(WaJson.object("{\"reason\":\"INBOUND_STATUS\"}"));
    }

    @Test void shouldRejectStatusWithoutAnOpaqueMessageId() {
        for (String id : List.of("", "x".repeat(161), "unsafe\nidentifier")) {
            ObjectNode envelope = fixture("status-read");
            ((ObjectNode) envelope.path("data")).put("keyId", id);
            assertThatThrownBy(() -> normalizer.normalize(envelope)).isInstanceOf(WaProblem.class);
        }
        ObjectNode envelope = fixture("status-read"); ((ObjectNode) envelope.path("data")).remove("keyId");
        assertThatThrownBy(() -> normalizer.normalize(envelope)).isInstanceOf(WaProblem.class);
    }

    @Test void shouldKeepOnlyDirectConnectionStateAndOriginalDatedDedupKey() {
        ObjectNode envelope = fixture("connection-open");
        ((ObjectNode) envelope.path("data")).put("wuid", "discard-me").put("profileName", "discard-me");
        var event = normalizer.normalize(envelope);
        assertThat(event.category()).isEqualTo("CONNECTION"); assertThat(event.state()).isEqualTo("COMPLETED");
        assertThat(event.payload()).isEqualTo(WaJson.object("{\"state\":\"open\",\"occurredAt\":\"2030-01-01T12:00:00Z\"}"));
        envelope.put("date_time", "2030-01-01T12:00:01Z");
        assertThat(normalizer.normalize(envelope).eventKey()).isNotEqualTo(event.eventKey());
        ((ObjectNode) envelope.path("data")).put("state", "close");
        assertThat(normalizer.normalize(envelope).payload().path("state").asText()).isEqualTo("close");
    }

    @Test void shouldRequireAnOriginalConnectionTimestampWithExplicitTimezone() {
        for (String value : List.of("", "2030-01-01T12:00:00", "invalid")) {
            ObjectNode envelope = fixture("connection-open").put("date_time", value);
            assertThatThrownBy(() -> normalizer.normalize(envelope)).isInstanceOf(WaProblem.class);
        }
        ObjectNode envelope = fixture("connection-open"); envelope.remove("date_time");
        assertThatThrownBy(() -> normalizer.normalize(envelope)).isInstanceOf(WaProblem.class);
    }

    @Test void shouldDurablyClassifyUnknownEventsAndIgnoreTopLevelCredentialsForDedup() {
        ObjectNode envelope = fixture("connection-open").put("event", "future.provider.event");
        var event = normalizer.normalize(envelope);
        assertThat(event.category()).isEqualTo("ECHO"); assertThat(event.state()).isEqualTo("SKIPPED");
        assertThat(event.payload()).isEqualTo(WaJson.object("{\"reason\":\"UNSUPPORTED_EVENT\"}"));
        assertThat(event.eventKey()).matches("[0-9a-f]{64}");
        envelope.put("apikey", "discard-me").put("sender", "discard-me").put("destination", "discard-me");
        assertThat(normalizer.normalize(envelope).eventKey()).isEqualTo(event.eventKey());
    }

    private static void assertUnsupported(WaEvolutionNormalizer.Event event, String reason) {
        assertThat(event.category()).isEqualTo("MESSAGE"); assertThat(event.kind()).isEqualTo("UNSUPPORTED");
        assertThat(event.state()).isEqualTo("PENDING"); assertThat(event.payload()).isEqualTo(WaJson.MAPPER.createObjectNode().put("reason", reason));
        assertThat(event.aliases()).isNotEmpty();
    }
    static ObjectNode fixture(String name) {
        try { return WaJson.object(Files.readString(Path.of("contracts", "whatsapp-mvp", "evolution-2.3.7", name + ".json"))); }
        catch (IOException failure) { throw new IllegalStateException("SYNTHETIC_EVOLUTION_FIXTURE_UNAVAILABLE", failure); }
    }
}
