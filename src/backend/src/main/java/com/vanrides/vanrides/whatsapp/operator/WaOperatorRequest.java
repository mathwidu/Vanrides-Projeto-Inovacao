package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Private-file input only. Its string representation intentionally contains no fields. */
public final class WaOperatorRequest {
    enum Operation {
        CREATE_COHORT("create-test-cohort", Set.of("synthetic"), Set.of()),
        CREATE_AUTHORIZED_COHORT("create-authorized-cohort", Set.of("reasonCode"), Set.of()),
        ADMIT_PARTICIPANT("admit-participant", Set.of("identityId", "allowedInstanceId", "phoneNumber", "phoneVerification"), Set.of()),
        PROVISION_CREDENTIAL("provision-service-credential", Set.of("principal", "allowedInstanceId", "expiresAt"), Set.of()),
        REVOKE_CREDENTIAL("revoke-service-credential", Set.of("credentialId", "reasonCode"), Set.of()),
        DRIVER_BOOTSTRAP("driver-bootstrap", Set.of("identityId", "expiresAt"), Set.of("inviteId")),
        SEED("seed-test-data", Set.of("identityId", "inboxEventId", "workerCredentialId", "leaseExpiresAt"),
                Set.of("pairingCode", "driverInviteCode", "messageText")),
        COMPLETE_INBOX("complete-test-inbox", Set.of("inboxEventId"), Set.of()),
        RESET("reset-test-data", Set.of(), Set.of()),
        REVOKE_DEVICE("revoke-device", Set.of("deviceId", "reasonCode"), Set.of()),
        LIST_DELIVERY_CASES("list-delivery-cases", Set.of("allowedInstanceId", "limit"), Set.of("afterCaseId"));
        final String value;
        final Set<String> required;
        final Set<String> optional;
        Operation(String value, Set<String> required, Set<String> optional) {
            this.value = value; this.required = required; this.optional = optional;
        }
    }
    private static final Set<String> COMMON = Set.of("environment", "operation", "batchId", "operatorCredential");
    private static final Set<String> TECHNICAL = Set.of("CHANNEL", "CONVERSATION_WORKER", "DELIVERY_WORKER", "SYSTEM_JOB");
    private final ObjectNode data;
    private final Operation operation;

    private WaOperatorRequest(ObjectNode data, Operation operation) { this.data = data; this.operation = operation; }

    public static WaOperatorRequest parse(byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0 || bytes.length > 16384) throw new WaOperatorFailure();
            ObjectNode data = WaJson.object(bytes);
            String label = WaJson.text(data, "operation", 1, 64);
            Operation operation = null;
            for (Operation candidate : Operation.values()) if (candidate.value.equals(label)) operation = candidate;
            if (operation == null) throw new WaOperatorFailure();
            Set<String> allowed = new HashSet<>(COMMON);
            allowed.addAll(operation.required); allowed.addAll(operation.optional);
            data.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw new WaOperatorFailure(); });
            for (String name : COMMON) if (!data.hasNonNull(name)) throw new WaOperatorFailure();
            for (String name : operation.required) if (!data.hasNonNull(name)) throw new WaOperatorFailure();
            for (String name : operation.optional) if (data.has(name) && data.get(name).isNull()) throw new WaOperatorFailure();
            WaOperatorRequest request = new WaOperatorRequest(data, operation);
            request.validate();
            return request;
        } catch (RuntimeException failure) { throw new WaOperatorFailure(); }
    }

    private void validate() {
        if (!text("environment", 1, 32).matches("[a-z][a-z0-9_-]{0,31}")) throw new WaOperatorFailure();
        uuid("batchId");
        WaDpopVerifier.canonical32(text("operatorCredential", 43, 43));
        for (String field : new String[] {"identityId", "inboxEventId", "workerCredentialId", "credentialId", "deviceId", "afterCaseId", "inviteId"})
            if (data.has(field)) uuid(field);
        for (String field : new String[] {"expiresAt", "leaseExpiresAt"}) if (data.has(field)) instant(field);
        if (data.has("reasonCode") && !text("reasonCode", 1, 64).matches("[A-Z][A-Z0-9_]{0,63}")) throw new WaOperatorFailure();
        if (data.has("principal") && !TECHNICAL.contains(text("principal", 1, 32))) throw new WaOperatorFailure();
        if (data.has("allowedInstanceId") && !text("allowedInstanceId", 1, 80).matches("[A-Za-z0-9_-]+"))
            throw new WaOperatorFailure();
        if (data.has("limit")) limit();
        if (operation == Operation.CREATE_COHORT && (!data.get("synthetic").isBoolean() || !data.get("synthetic").booleanValue()))
            throw new WaOperatorFailure();
        if (operation == Operation.CREATE_AUTHORIZED_COHORT && !text("reasonCode",1,64).equals("AUTHORIZED_HOMOLOGATION_GROUP"))
            throw new WaOperatorFailure();
        if (operation == Operation.ADMIT_PARTICIPANT && (!text("phoneNumber",9,16).matches("\\+[1-9][0-9]{7,14}")
                || !text("phoneVerification",1,64).equals("PARTICIPANT_CONFIRMED"))) throw new WaOperatorFailure();
        if (data.has("pairingCode") && !text("pairingCode", 8, 8).matches("[BCDFGHJKMNPQRSTVWXYZ]{8}"))
            throw new WaOperatorFailure();
        if (data.has("driverInviteCode")) WaDpopVerifier.canonical32(text("driverInviteCode", 43, 43));
        if (data.has("pairingCode") && data.has("driverInviteCode")) throw new WaOperatorFailure();
        if (data.has("messageText")) {
            text("messageText", 1, 2000);
            if (data.has("pairingCode") || data.has("driverInviteCode")) throw new WaOperatorFailure();
        }
    }

    Operation operation() { return operation; }
    String environment() { return text("environment", 1, 32); }
    UUID batchId() { return uuid("batchId"); }
    String operatorCredential() { return text("operatorCredential", 43, 43); }
    boolean has(String field) { return data.has(field); }
    int limit() {
        var value = data.get("limit");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<1 || value.intValue()>100)
            throw new WaOperatorFailure();
        return value.intValue();
    }
    String text(String field, int min, int max) { return WaJson.text(data, field, min, max); }
    UUID uuid(String field) { return WaJson.uuid(data, field); }
    Instant instant(String field) {
        String value = text(field, 20, 35);
        if (!value.endsWith("Z")) throw new WaOperatorFailure();
        return Instant.parse(value);
    }
    static boolean technical(String principal) { return TECHNICAL.contains(principal); }
    @Override public String toString() { return "WaOperatorRequest[redacted]"; }
}
