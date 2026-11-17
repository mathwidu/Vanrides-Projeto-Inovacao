package com.vanrides.vanrides.whatsapp.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Shared assertions for the OpenAPI artifact, independent of its parser/test lifecycle. */
final class OpenApiContractAssertions {
    private final JsonNode contract;
    OpenApiContractAssertions(JsonNode contract) { this.contract = contract; }

    void assertClosedProductDtosWithProviderBody() {
        JsonNode schemas = contract.at("/components/schemas");
        Iterator<Map.Entry<String, JsonNode>> fields = schemas.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> schema = fields.next();
            assertObjectClosure(schema.getValue(), schema.getKey(), "EvolutionRawEventRequest".equals(schema.getKey()));
        }
        JsonNode rawBody = schemas.get("EvolutionRawEventRequest");
        assertFalse(rawBody.has("x-contract-pending"));
        assertEquals("evolution-2.3.7-baileys", rawBody.path("x-provider-profile").asText());
        assertTrue(rawBody.path("x-vanrides-raw-provider-body").asBoolean());
        assertTrue(rawBody.path("properties").has("event"));
        assertTrue(rawBody.path("properties").has("instance"));
        JsonNode endpoint = contract.at("/paths/~1integrations~1whatsapp~1v1~1events/post");
        assertEquals("CARD_050_TRANSPORT", endpoint.path("x-vanrides-availability").asText());
        assertFalse(endpoint.has("x-contract-pending"));
    }

    static String expectedPrincipal(String path) {
        if (path.startsWith("/driver-app/")) {
            return "DEVICE";
        }
        if (path.endsWith("/events")) {
            return "CHANNEL";
        }
        if (path.contains("/outbox/")) {
            return "DELIVERY_WORKER";
        }
        if (path.endsWith("/jobs/tick")) {
            return "SYSTEM_JOB";
        }
        return "CONVERSATION_WORKER";
    }

    static void assertObjectClosure(JsonNode node, String location, boolean pendingProviderBody) {
        if ("object".equals(node.path("type").asText())) {
            assertTrue(node.has("additionalProperties"), location + " must declare object closure");
            assertEquals(pendingProviderBody, node.path("additionalProperties").asBoolean(), location);
        }
        if (pendingProviderBody) return; // Provider extensions are the sole documented raw-body exception.
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> assertObjectClosure(field.getValue(),
                    location + "/" + field.getKey(), false));
        } else if (node.isArray()) {
            node.forEach(child -> assertObjectClosure(child, location + "[]", false));
        }
    }

    void assertRequiredHeaders(String path, String method, String... names) {
        JsonNode parameters = contract.path("paths").path(path).path(method).path("parameters");
        for (String name : names) {
            boolean required = false;
            for (JsonNode parameter : parameters) {
                JsonNode resolved = parameter.has("$ref")
                        ? contract.at(parameter.get("$ref").asText().substring(1)) : parameter;
                if (name.equals(resolved.path("name").asText())) {
                    required = resolved.path("required").asBoolean();
                }
            }
            assertTrue(required, path + " requires " + name);
        }
    }

    void assertOperationAvailability(String path, JsonNode operation) {
        String card = operation.path("x-vanrides-implementation-card").asText();
        String availability = switch (card) {
            case "VR-WA-040-DOMAIN-AUTH" -> "CARD_040_FOUNDATION";
            case "VR-WA-050-WHATSAPP-TRANSPORT" -> "CARD_050_TRANSPORT";
            case "VR-WA-100-ATTENDANCE" -> "CARD_100_ATTENDANCE";
            case "VR-WA-110-RUN-TRACKING" -> "CARD_110_RUN_TRACKING";
            case "VR-HML-010-TEAM-API" -> {
                boolean pairing = Set.of("/driver-app/v1/pairing-links/{id}/preview",
                        "/driver-app/v1/pairing-links/{id}/claim").contains(path);
                assertTrue(pairing || Set.of("/driver-app/v1/runs/{id}/finish",
                        "/driver-app/v1/finish-intents/{id}/preview",
                        "/driver-app/v1/finish-intents/{id}/receipt").contains(path));
                yield pairing ? "MVP_GUIDED_PAIRING" : "MVP_FINISH_INTENT";
            }
            default -> throw new AssertionError("Operation has no reviewed implementation card: " + card);
        };
        assertEquals(availability, operation.path("x-vanrides-availability").asText());
    }

    void assertFixtureAvailability(JsonNode entry) {
        String expected = switch (entry.path("implementationCard").asText()) {
            case "VR-WA-050-WHATSAPP-TRANSPORT" -> "CARD_050_TRANSPORT";
            case "VR-WA-070-AGENT-WORKFLOWS" -> "CARD_070_CONVERSATION";
            case "VR-WA-080-DRIVER-ROUTINES" -> "CARD_080_DRIVER_SETUP";
            case "VR-WA-090-PASSENGERS" -> "CARD_090_PASSENGER_SETUP";
            case "VR-WA-100-ATTENDANCE" -> "CARD_100_ATTENDANCE";
            case "VR-WA-110-RUN-TRACKING" -> "CARD_110_RUN_TRACKING";
            case "VR-WA-120-DELIVERY-RECOVERY" -> "CARD_120_DELIVERY_RECOVERY";
            case "VR-WA-130-APPROACH" -> "CARD_130_APPROACH";
            case "VR-WA-140-FINISH-SUPPORT" -> "SetPreferenceCommand".equals(entry.path("schema").asText())
                    ? "CARD_140_PREFERENCES" : "FUTURE";
            case "VR-HML-010-TEAM-API" -> {
                String schema = entry.path("schema").asText();
                assertTrue(schema.startsWith("PairingLink") || schema.startsWith("FinishIntent"));
                yield schema.startsWith("PairingLink") ? "MVP_GUIDED_PAIRING" : "MVP_FINISH_INTENT";
            }
            case "VR-WA-150-OPERATIONS" -> "FUTURE";
            default -> throw new AssertionError("Fixture has no reviewed implementation card");
        };
        assertEquals(expected, entry.path("availability").asText());
    }

    void assertMediaExamples(JsonNode media) {
        assertFalse(media.path("schema").isMissingNode());
        assertFalse(media.path("examples").isEmpty());
        String schemaReference = media.path("schema").path("$ref").asText();
        assertTrue(schemaReference.startsWith("#/components/schemas/"));
        String schemaName = schemaReference.substring("#/components/schemas/".length());
        for (JsonNode example : media.path("examples")) {
            JsonNode resolved = contract.at(example.path("$ref").asText().substring(1)).path("value");
            assertTrue(ContractFixtureValidator.validate(contract, schemaName, resolved).isEmpty(),
                    () -> schemaName + " has an invalid operation example");
        }
    }

    void assertInvalid(String schema, JsonNode value) {
        assertFalse(ContractFixtureValidator.validate(contract, schema, value).isEmpty(),
                () -> schema + " unexpectedly accepts forbidden/missing data");
    }

    JsonNode fixture(String id) {
        return contract.at("/components/examples/" + id + "/value");
    }

    static Set<String> textSet(JsonNode values) {
        Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    static Set<String> fieldSet(JsonNode object) {
        Set<String> result = new HashSet<>();
        object.fieldNames().forEachRemaining(result::add);
        return result;
    }

    static Stream<JsonNode> stream(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false);
    }
}
