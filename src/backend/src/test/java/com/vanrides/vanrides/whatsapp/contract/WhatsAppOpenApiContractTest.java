package com.vanrides.vanrides.whatsapp.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.vanrides.vanrides.whatsapp.contract.OpenApiContractAssertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class WhatsAppOpenApiContractTest {

    private static final String CURRENT_CARD = "VR-WA-040-DOMAIN-AUTH";
    private static final Path CONTRACT_ROOT = Path.of("contracts", "whatsapp-mvp");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> COMMANDS = Set.of(
            "START_ONBOARDING", "SET_PROFILE_NAME", "ACCEPT_NOTICE", "CLAIM_DRIVER_INVITE",
            "CREATE_INVITE", "REVOKE_INVITE", "REQUEST_LINK", "APPROVE_LINK", "CLOSE_LINK",
            "PROPOSE_PICKUP", "APPROVE_PICKUP", "CONFIGURE_ROUTINE", "SET_STOP_ORDER", "CANCEL_OCCURRENCE",
            "SET_ATTENDANCE", "REQUEST_START", "REQUEST_RESUME", "FINISH_RUN", "GET_SUMMARY",
            "GET_DRIVER_LOCATION", "CONFIRM_PAIRING", "SET_PREFERENCE", "REQUEST_SUPPORT", "REQUEST_DATA_DELETION", "RESPOND_TO_MESSAGE",
            "ADVANCE_DRIVER_SETUP", "SET_ROUTINE_STATE", "ADVANCE_PASSENGER_SETUP", "ADVANCE_ATTENDANCE", "ADVANCE_RUN_SETUP", "SET_OPERATIONAL_ATTENDANCE");
    private static final Set<String> FOUNDATION_COMMANDS = Set.of(
            "START_ONBOARDING", "SET_PROFILE_NAME", "ACCEPT_NOTICE", "CLAIM_DRIVER_INVITE", "CONFIRM_PAIRING");
    private static final Set<String> DANGEROUS_COMMAND_FIELDS = Set.of(
            "actorId", "driverId", "passengerId", "userId", "companyId", "role", "principal", "recipient",
            "latitude", "longitude", "coordinates", "url", "authorization");
    private static JsonNode contract;
    private static JsonNode fixtureCatalog;
    private static String source;
    private static OpenApiContractAssertions checks;

    @BeforeAll
    static void readArtifacts() throws IOException {
        source = Files.readString(CONTRACT_ROOT.resolve("openapi.yaml"));
        contract = MAPPER.readTree(source);
        fixtureCatalog = MAPPER.readTree(CONTRACT_ROOT.resolve("fixtures/catalog.json").toFile());
        checks = new OpenApiContractAssertions(contract);
    }

    @Test
    void parsesOpenApi303WithoutNetworkResolutionOrWarnings() {
        // Reject external refs before the parser can attempt any resolution.
        ContractFixtureValidator.assertLocalReferences(contract);
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        options.setFlatten(false);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(source, null, options);
        assertNotNull(result.getOpenAPI());
        assertEquals("3.0.3", result.getOpenAPI().getOpenapi());
        assertTrue(result.getMessages() == null || result.getMessages().isEmpty(),
                () -> "OpenAPI parser messages: " + result.getMessages());
        assertEquals(21, result.getOpenAPI().getPaths().size());
    }

    @Test
    void rejectsRemoteReferencesBeforeParsing() {
        ObjectNode changed = contract.deepCopy();
        changed.with("components").with("schemas").set("UnexpectedRemote",
                MAPPER.createObjectNode().put("$ref", "https://vanrides.invalid/remote-schema.json"));
        assertThrows(IllegalArgumentException.class,
                () -> ContractFixtureValidator.assertLocalReferences(changed));
    }

    @Test
    void productDtosAreClosedWithOneExplicitPendingThirdPartyBody() {
        checks.assertClosedProductDtosWithProviderBody();
    }

    @TestFactory
    Stream<DynamicTest> everySyntheticFixtureMatchesItsLocalSchema() {
        return stream(fixtureCatalog.path("fixtures")).map(fixture -> DynamicTest.dynamicTest(
                fixture.path("id").asText(), () -> {
                    String id = fixture.path("id").asText();
                    assertEquals(contract.at("/components/examples/" + id + "/value"), fixture.path("value"));
                    assertTrue(ContractFixtureValidator.validate(contract, fixture.path("schema").asText(),
                            fixture.path("value")).isEmpty(),
                            () -> id + ": " + ContractFixtureValidator.validate(contract,
                                    fixture.path("schema").asText(), fixture.path("value")));
                }));
    }

    @TestFactory
    Stream<DynamicTest> everyNegativeFixtureFailsItsLocalSchema() {
        return stream(fixtureCatalog.path("negativeFixtures")).map(fixture -> DynamicTest.dynamicTest(
                fixture.path("id").asText(), () -> checks.assertInvalid(
                        fixture.path("schema").asText(), fixture.path("value"))));
    }

    @Test
    void everyCommandAndJobTypeHasAnExampleAndFutureHandlersAreNotAdvertised() {
        assertEquals(COMMANDS, textSet(contract.at("/components/schemas/CommandType/enum")));
        JsonNode human = contract.at("/components/schemas/HumanCommandRequest");
        assertEquals(COMMANDS.size(), human.path("oneOf").size());
        assertEquals(COMMANDS, fieldSet(human.at("/discriminator/mapping")));
        Set<String> exemplified = new HashSet<>();
        Set<String> jobs = new HashSet<>();
        Set<String> foundation = new HashSet<>();
        for (JsonNode fixture : fixtureCatalog.path("fixtures")) {
            JsonNode value = fixture.path("value");
            if (value.has("commandType")) {
                String command = value.path("commandType").asText();
                exemplified.add(command);
                assertTrue(ContractFixtureValidator.validate(contract, "HumanCommandRequest", value).isEmpty());
                if (CURRENT_CARD.equals(fixture.path("implementationCard").asText())) {
                    foundation.add(command);
                }
            }
            if ("JobTickRequest".equals(fixture.path("schema").asText())) {
                jobs.add(value.path("jobType").asText());
            }
        }
        assertEquals(COMMANDS, exemplified);
        assertEquals(FOUNDATION_COMMANDS, foundation);
        assertEquals(textSet(contract.at("/components/schemas/JobType/enum")), jobs);
        assertEquals(FOUNDATION_COMMANDS, textSet(contract.at(
                "/paths/~1integrations~1whatsapp~1v1~1commands/post/x-vanrides-supported-commands-in-040")));
        for (JsonNode allowed : checks.fixture("ConversationContext").path("allowedCommands")) {
            assertTrue(FOUNDATION_COMMANDS.contains(allowed.asText())
                    || Set.of("RESPOND_TO_MESSAGE", "REQUEST_SUPPORT").contains(allowed.asText()));
        }
    }

    @Test
    void commandEnvelopeAndEveryPayloadRejectAuthorityAndGpsInjection() {
        for (JsonNode item : fixtureCatalog.path("fixtures")) {
            JsonNode value = item.path("value");
            if (!value.has("commandType")) {
                continue;
            }
            for (String forbidden : DANGEROUS_COMMAND_FIELDS) {
                ObjectNode envelope = value.deepCopy();
                envelope.put(forbidden, "untrusted");
                checks.assertInvalid("HumanCommandRequest", envelope);
                ObjectNode payload = value.deepCopy();
                ((ObjectNode) payload.get("payload")).put(forbidden, "untrusted");
                checks.assertInvalid("HumanCommandRequest", payload);
            }
            ObjectNode missingEvent = value.deepCopy();
            missingEvent.remove("inboxEventId");
            checks.assertInvalid("HumanCommandRequest", missingEvent);
            ObjectNode noVersion = value.deepCopy();
            noVersion.remove("expectedVersion");
            checks.assertInvalid("HumanCommandRequest", noVersion);
            ObjectNode negativeVersion = value.deepCopy();
            negativeVersion.put("expectedVersion", -1);
            checks.assertInvalid("HumanCommandRequest", negativeVersion);
            ObjectNode unknownCommand = value.deepCopy();
            unknownCommand.put("commandType", "GRANT_OPERATOR");
            checks.assertInvalid("HumanCommandRequest", unknownCommand);
        }
        ObjectNode notice = checks.fixture("AcceptNotice").deepCopy();
        ((ObjectNode) notice.get("payload")).put("acceptedAt", "2026-10-31T12:00:00Z");
        checks.assertInvalid("HumanCommandRequest", notice);
    }

    @Test
    void leasesAndIdempotencyAreRequiredAtTheirBoundaries() {
        checks.assertRequiredHeaders("/integrations/whatsapp/v1/context", "get", "X-Inbox-Lease");
        checks.assertRequiredHeaders("/integrations/whatsapp/v1/commands", "post", "Idempotency-Key", "X-Inbox-Lease");
        checks.assertRequiredHeaders("/integrations/whatsapp/v1/inbox/{id}/result", "post", "X-Inbox-Lease");
        for (String suffix : new String[] {"pairings/begin", "pairings/redeem", "session/refresh", "session/revoke"}) {
            checks.assertRequiredHeaders("/driver-app/v1/" + suffix, "post", "Idempotency-Key", "DPoP");
        }
        JsonNode policy = contract.at("/x-vanrides-security-policy/idempotency");
        assertTrue(policy.has("request"));
        assertTrue(policy.has("humanEvent"));
        assertEquals(86400, policy.path("humanCommandResultTtlSeconds").asLong());
        assertEquals(300, policy.path("pairingBeginMaximumReplayTtlSeconds").asInt());
        assertEquals(300, policy.path("credentialReplayTtlSeconds").asInt());
        assertTrue(policy.path("credentialReplayEncrypted").asBoolean());
        assertTrue(policy.path("credentialReplayRequiresCurrentAuthorization").asBoolean());
    }

    @Test
    void pairingIsKeyBoundClosedAndCannotAcceptPrivateKeyOrActorSelection() {
        ObjectNode begin = checks.fixture("PairingBegin").deepCopy();
        ((ObjectNode) begin.get("publicJwk")).put("d", "EXAMPLE_NEVER_ACCEPT_PRIVATE_KEY");
        checks.assertInvalid("PairingBeginRequest", begin);
        begin = checks.fixture("PairingBegin").deepCopy();
        ((ObjectNode) begin.get("publicJwk")).put("jku", "https://vanrides.invalid/key");
        checks.assertInvalid("PairingBeginRequest", begin);
        begin = checks.fixture("PairingBegin").deepCopy();
        begin.put("pkceMethod", "plain");
        checks.assertInvalid("PairingBeginRequest", begin);
        begin = checks.fixture("PairingBegin").deepCopy();
        begin.put("driverId", "untrusted");
        checks.assertInvalid("PairingBeginRequest", begin);
        ObjectNode redeem = checks.fixture("PairingRedeem").deepCopy();
        redeem.put("pkceVerifier", "too-short");
        checks.assertInvalid("PairingRedeemRequest", redeem);
        ObjectNode proof = checks.fixture("DPoPHeader").deepCopy();
        proof.put("alg", "none");
        checks.assertInvalid("DPoPProtectedHeader", proof);
        JsonNode policy = contract.at("/x-vanrides-security-policy/pairing");
        assertEquals(300, policy.path("ttlSeconds").asInt());
        assertEquals(5, policy.path("maxAttemptsPerActorWindow").asInt());
        assertEquals(8, policy.path("userCodeLength").asInt());
        String alphabet = policy.path("userCodeAlphabet").asText();
        assertEquals("BCDFGHJKMNPQRSTVWXYZ", alphabet);
        assertEquals(20, alphabet.length());
        assertEquals(20, alphabet.chars().distinct().count());
        assertEquals("^[" + alphabet + "]{8}$",
                contract.at("/components/schemas/PairingUserCode/pattern").asText());
        assertTrue(checks.fixture("PairingBegunWithZ").path("userCode").asText().contains("Z"));
        assertTrue(ContractFixtureValidator.validate(
                contract, "PairingBeginResponse", checks.fixture("PairingBegunWithZ")).isEmpty());
        ObjectNode forbiddenCode = checks.fixture("PairingBegunWithZ").deepCopy();
        forbiddenCode.put("userCode", "BCDFGHJL");
        checks.assertInvalid("PairingBeginResponse", forbiddenCode);
        assertEquals(300, policy.path("accessLifetimeSeconds").asInt());
        assertEquals(2592000, policy.path("refreshMaximumLifetimeSeconds").asInt());
        JsonNode status = contract.at("/components/schemas/PairingStatusResponse/properties");
        assertEquals(Set.of("pairingId", "state", "expiresAt", "serverTime"), fieldSet(status));
    }

    @Test
    void proofClaimsRequireBodyAndAccessBindingAtTheAppropriateBoundaries() {
        ObjectNode bodyProof = checks.fixture("DPoPBeginClaims").deepCopy();
        bodyProof.remove("vr_body_sha256");
        checks.assertInvalid("DPoPBodyBoundClaims", bodyProof);
        ObjectNode accessProof = checks.fixture("DPoPStatusClaims").deepCopy();
        accessProof.remove("ath");
        checks.assertInvalid("DPoPAccessClaims", accessProof);
        ObjectNode both = checks.fixture("DPoPRevokeClaims").deepCopy();
        both.remove("ath");
        checks.assertInvalid("DPoPBodyAndAccessClaims", both);
        both = checks.fixture("DPoPRevokeClaims").deepCopy();
        both.remove("vr_body_sha256");
        checks.assertInvalid("DPoPBodyAndAccessClaims", both);
        bodyProof = checks.fixture("DPoPBeginClaims").deepCopy();
        bodyProof.remove("nonce");
        checks.assertInvalid("DPoPBodyBoundClaims", bodyProof);
        JsonNode errors = contract.at("/components/schemas/ErrorResponse/properties/code/enum");
        assertTrue(textSet(errors).contains("PROOF_NONCE_REQUIRED"));
        assertTrue(contract.at("/components/responses/Error401/headers").has("DPoP-Nonce"));
    }

    @Test
    void systemJobsCannotForgeHumanEventsAndSamplesCannotChooseAnOwner() {
        ObjectNode job = checks.fixture("JobHealthReconcile").deepCopy();
        job.put("inboxEventId", "fake-human-event");
        checks.assertInvalid("JobTickRequest", job);
        job = checks.fixture("JobHealthReconcile").deepCopy();
        job.put("actorId", "fake-human-actor");
        checks.assertInvalid("JobTickRequest", job);
        ObjectNode batch = checks.fixture("TrackingSamples").deepCopy();
        batch.put("driverId", "another-driver");
        checks.assertInvalid("TrackingSamplesRequest", batch);
        batch = checks.fixture("TrackingSamples").deepCopy();
        ((ObjectNode) batch.withArray("samples").get(0)).put("latitude", 91);
        checks.assertInvalid("TrackingSamplesRequest", batch);
        batch = checks.fixture("TrackingSamples").deepCopy();
        ArrayNode samples = batch.withArray("samples");
        while (samples.size() < 31) {
            samples.add(samples.get(0).deepCopy());
        }
        checks.assertInvalid("TrackingSamplesRequest", batch);
    }

    @Test
    void everyOperationNamesItsPrincipalCardAndExamplesWithoutPublicAdminOrCors() {
        assertTrue(contract.path("x-vanrides-default-deny").asBoolean());
        assertTrue(contract.path("x-vanrides-allowed-origins").isEmpty());
        assertEquals("CLI_ONLY", contract.path("x-vanrides-operator-transport").asText());
        assertEquals(Set.of("CHANNEL", "CONVERSATION_WORKER", "DELIVERY_WORKER", "SYSTEM_JOB"),
                textSet(contract.path("x-vanrides-service-principals")));
        Set<String> operationIds = new HashSet<>();
        Iterator<Map.Entry<String, JsonNode>> paths = contract.path("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            assertFalse(path.getKey().contains("admin") || path.getKey().contains("operator"));
            assertEquals(1, path.getValue().size(), "Only an explicit method may be declared");
            Iterator<Map.Entry<String, JsonNode>> methods = path.getValue().fields();
            while (methods.hasNext()) {
                Map.Entry<String, JsonNode> method = methods.next();
                assertTrue(Set.of("get", "post").contains(method.getKey()));
                JsonNode operation = method.getValue();
                assertTrue(operationIds.add(operation.path("operationId").asText()));
                assertTrue(operation.path("x-vanrides-implementation-card").asText().startsWith("VR-WA-"));
                assertTrue(Set.of("CARD_040_FOUNDATION", "CARD_050_TRANSPORT", "CARD_070_CONVERSATION", "CARD_080_DRIVER_SETUP", "CARD_100_ATTENDANCE", "CARD_110_RUN_TRACKING", "FUTURE")
                        .contains(operation.path("x-vanrides-availability").asText()));
                assertFalse(operation.path("security").isEmpty());
                assertTrue(operation.path("x-vanrides-allowed-origins").isEmpty());
                Set<String> principals = textSet(operation.path("x-vanrides-principals"));
                assertEquals(1, principals.size());
                assertFalse(principals.contains("OPERATOR"));
                assertEquals(expectedPrincipal(path.getKey()), principals.iterator().next());
                if (operation.has("requestBody")) {
                    checks.assertMediaExamples(operation.at("/requestBody/content/application~1json"));
                }
                for (JsonNode response : operation.path("responses")) {
                    if (!response.has("$ref") && response.has("content")) {
                        checks.assertMediaExamples(response.at("/content/application~1json"));
                    }
                }
            }
        }
        assertEquals(21, operationIds.size());
    }

    @Test
    void examplesContainNoRuntimeEvidenceOrExecutableServiceDestination() {
        assertTrue(fixtureCatalog.path("syntheticOnly").asBoolean());
        assertFalse(fixtureCatalog.path("runtimeEvidence").asBoolean(true));
        assertEquals("https://vanrides.invalid", contract.at("/servers/0/url").asText());
        assertEquals(Set.of("code", "message", "correlationId"),
                fieldSet(contract.at("/components/schemas/ErrorResponse/properties")));
        Set<String> families = new HashSet<>();
        for (JsonNode entry : fixtureCatalog.path("fixtures")) {
            families.add(entry.path("family").asText());
            if (!CURRENT_CARD.equals(entry.path("implementationCard").asText())) {
                String expected = switch (entry.path("implementationCard").asText()) {
                    case "VR-WA-050-WHATSAPP-TRANSPORT" -> "CARD_050_TRANSPORT";
                    case "VR-WA-070-AGENT-WORKFLOWS" -> "CARD_070_CONVERSATION";
                    case "VR-WA-080-DRIVER-ROUTINES" -> "CARD_080_DRIVER_SETUP";
                    case "VR-WA-090-PASSENGERS" -> "CARD_090_PASSENGER_SETUP";
                    case "VR-WA-100-ATTENDANCE" -> "CARD_100_ATTENDANCE";
                    case "VR-WA-110-RUN-TRACKING" -> "CARD_110_RUN_TRACKING"; case "VR-WA-120-DELIVERY-RECOVERY" -> "CARD_120_DELIVERY_RECOVERY";
                    default -> "FUTURE";
                };
                assertEquals(expected, entry.path("availability").asText());
            }
        }
        assertTrue(families.containsAll(Set.of("human-command", "error", "pairing", "session", "driver-status",
                "proof", "channel", "inbox", "context", "system-job", "run", "tracking", "delivery", "delivery-pin")));
    }

}
