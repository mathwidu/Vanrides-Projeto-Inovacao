package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WaOperatorRequestTest {
    private static ObjectNode request(String operation) {
        return WaJson.MAPPER.createObjectNode().put("environment", "test").put("operation", operation)
                .put("batchId", UUID.randomUUID().toString()).put("operatorCredential", WaCrypto.randomToken());
    }
    private static WaOperatorRequest parse(ObjectNode value) {
        return WaOperatorRequest.parse(WaJson.write(value).getBytes(StandardCharsets.UTF_8));
    }
    @Test void acceptsExplicitParticipantAdmissionAndNeverPrintsItsPhone() {
        var cohort=request("create-authorized-cohort").put("reasonCode","AUTHORIZED_HOMOLOGATION_GROUP");
        assertThat(parse(cohort).operation()).isEqualTo(WaOperatorRequest.Operation.CREATE_AUTHORIZED_COHORT);
        var admission=request("admit-participant").put("identityId",UUID.randomUUID().toString()).put("allowedInstanceId","hml-individual")
            .put("phoneNumber","+15550001111").put("phoneVerification","PARTICIPANT_CONFIRMED");
        assertThat(parse(admission).toString()).isEqualTo("WaOperatorRequest[redacted]").doesNotContain("15550001111");
    }
    @Test void participantAdmissionRejectsUnverifiedOrNoncanonicalPhonesAndProviderInjection() {
        var admission=request("admit-participant").put("identityId",UUID.randomUUID().toString()).put("allowedInstanceId","hml-individual")
            .put("phoneNumber","+15550001111").put("phoneVerification","PARTICIPANT_CONFIRMED");
        for(String phone:new String[]{"15550001111","+05550001111","+1 5550001111","+15550001111@s.whatsapp.net","+123","+1234567890123456"})
            assertThatThrownBy(()->parse(admission.deepCopy().put("phoneNumber",phone))).isInstanceOf(WaOperatorFailure.class);
        for(var invalid:new ObjectNode[]{admission.deepCopy().put("phoneVerification","ASSUMED"),admission.deepCopy().put("phoneVerification",true),
                admission.deepCopy().put("provider","EVOLUTION"),admission.deepCopy().put("synthetic",false)})
            assertThatThrownBy(()->parse(invalid)).isInstanceOf(WaOperatorFailure.class);
    }
    @Test void idempotentBootstrapAcceptsOnlyAnOpaqueReferenceNotACallerChosenSecret() {
        var bootstrap=request("driver-bootstrap").put("identityId",UUID.randomUUID().toString()).put("inviteId",UUID.randomUUID().toString())
            .put("expiresAt","2030-01-01T00:00:00Z");
        assertThat(parse(bootstrap).operation()).isEqualTo(WaOperatorRequest.Operation.DRIVER_BOOTSTRAP);
        assertThatThrownBy(()->parse(bootstrap.deepCopy().put("inviteId","invalid"))).isInstanceOf(WaOperatorFailure.class);
        assertThatThrownBy(()->parse(bootstrap.deepCopy().put("driverInviteCode",WaCrypto.randomToken()))).isInstanceOf(WaOperatorFailure.class);
    }

    @Test void acceptsExplicitSyntheticCohortAndRedactsItsRepresentation() {
        ObjectNode data = request("create-test-cohort").put("synthetic", true);
        WaOperatorRequest parsed = parse(data);
        assertThat(parsed.toString()).isEqualTo("WaOperatorRequest[redacted]")
                .doesNotContain(data.get("operatorCredential").asText());
    }

    @Test void rejectsImplicitCohortsUnknownFieldsAndOperatorEscalation() {
        assertThatThrownBy(() -> parse(request("create-test-cohort").put("synthetic", false)))
                .isInstanceOf(WaOperatorFailure.class);
        ObjectNode data = request("provision-service-credential").put("principal", "OPERATOR")
                .put("allowedInstanceId", "test-instance").put("expiresAt", "2030-01-01T00:00:00Z");
        assertThatThrownBy(() -> parse(data)).isInstanceOf(WaOperatorFailure.class);
        ObjectNode injected = request("reset-test-data").put("actorId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> parse(injected)).isInstanceOf(WaOperatorFailure.class);
        ObjectNode selectedSecret = request("provision-service-credential").put("principal", "CHANNEL")
                .put("allowedInstanceId", "test-instance").put("expiresAt", "2030-01-01T00:00:00Z")
                .put("credential", WaCrypto.randomToken());
        assertThatThrownBy(() -> parse(selectedSecret)).isInstanceOf(WaOperatorFailure.class);
    }

    @Test void rejectsNoncanonicalSecretsDuplicateKeysAndTrailingDocuments() {
        ObjectNode data = request("reset-test-data").put("operatorCredential", "B".repeat(43));
        assertThatThrownBy(() -> parse(data)).isInstanceOf(WaOperatorFailure.class);
        String valid = WaJson.write(request("reset-test-data"));
        assertThatThrownBy(() -> WaOperatorRequest.parse((valid + "{}").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(WaOperatorFailure.class);
        assertThatThrownBy(() -> WaOperatorRequest.parse("{\"operation\":\"reset-test-data\",\"operation\":\"seed-test-data\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(WaOperatorFailure.class);
    }

    @Test void seedOnlyAcceptsHashableCodeMetadataAndDoesNotLetCallerChooseLeaseOrActor() {
        ObjectNode seed = request("seed-test-data").put("identityId", UUID.randomUUID().toString())
                .put("inboxEventId", UUID.randomUUID().toString()).put("workerCredentialId", UUID.randomUUID().toString())
                .put("leaseExpiresAt", "2030-01-01T00:00:00Z").put("pairingCode", "BCDFGHJZ");
        assertThat(parse(seed).operation()).isEqualTo(WaOperatorRequest.Operation.SEED);
        ObjectNode invalid = seed.deepCopy(); invalid.put("pairingCode", "BCDFGHJL");
        assertThatThrownBy(() -> parse(invalid)).isInstanceOf(WaOperatorFailure.class);
        ObjectNode lease = seed.deepCopy(); lease.put("leaseToken", WaCrypto.randomToken());
        assertThatThrownBy(() -> parse(lease)).isInstanceOf(WaOperatorFailure.class);
        ObjectNode coordinate = seed.deepCopy(); coordinate.put("latitude", 0);
        assertThatThrownBy(() -> parse(coordinate)).isInstanceOf(WaOperatorFailure.class);
    }
    @Test void seedAcceptsExplicitHumanTextAndNeverCombinesItWithCodes() {
        ObjectNode seed = request("seed-test-data").put("identityId", UUID.randomUUID().toString())
            .put("inboxEventId", UUID.randomUUID().toString()).put("workerCredentialId", UUID.randomUUID().toString())
            .put("leaseExpiresAt", "2030-01-01T00:00:00Z").put("messageText", "ACEITO");
        assertThat(parse(seed).text("messageText", 1, 2000)).isEqualTo("ACEITO");
        for (String value : new String[]{"", "x".repeat(2001), "invalid\ntext"}) {
            ObjectNode invalid = seed.deepCopy().put("messageText", value);
            assertThatThrownBy(() -> parse(invalid)).isInstanceOf(WaOperatorFailure.class);
        }
        ObjectNode code = seed.deepCopy().put("pairingCode", "BCDFGHJK");
        assertThatThrownBy(() -> parse(code)).isInstanceOf(WaOperatorFailure.class);
    }
    @Test void deliveryCaseLookupIsBoundedScopedAndRedacted() {
        var data = request("list-delivery-cases").put("allowedInstanceId", "synthetic-instance").put("limit", 100)
            .put("afterCaseId", UUID.randomUUID().toString());
        assertThat(parse(data).limit()).isEqualTo(100);
        assertThat(parse(data).toString()).isEqualTo("WaOperatorRequest[redacted]");
    }
    @Test void deliveryCaseLookupRejectsUnboundedOrContentBasedFilters() {
        var data = request("list-delivery-cases").put("allowedInstanceId", "synthetic-instance").put("limit", 1);
        for (var invalid : new ObjectNode[]{data.deepCopy().put("limit", 0), data.deepCopy().put("limit", 101),
                data.deepCopy().put("limit", "10"), data.deepCopy().put("afterCaseId", "invalid"),
                data.deepCopy().put("externalMessageId", "synthetic"), data.deepCopy().put("identityId", UUID.randomUUID().toString())})
            assertThatThrownBy(() -> parse(invalid)).isInstanceOf(WaOperatorFailure.class);
    }
}
