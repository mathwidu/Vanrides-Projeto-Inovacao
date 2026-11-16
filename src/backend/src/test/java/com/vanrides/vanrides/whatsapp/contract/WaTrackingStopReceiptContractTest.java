package com.vanrides.vanrides.whatsapp.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaTrackingStopReceiptContractTest {
    private JsonNode contract() throws Exception {
        return WaJson.object(Files.readString(Path.of("contracts/whatsapp-mvp/openapi.yaml")));
    }
    @Test void bodyRequiresExactGenerationAndExplicitNullableReferenceAndRejectsGps() throws Exception {
        var contract=contract(); var body=WaJson.object("{\"generation\":1,\"stopRequestRef\":null}");
        assertTrue(ContractFixtureValidator.validate(contract,"TrackingStopReceiptRequest",body).isEmpty());
        assertFalse(ContractFixtureValidator.validate(contract,"TrackingStopReceiptRequest",body.deepCopy().put("latitude",0)).isEmpty());
        var missing=body.deepCopy(); missing.remove("stopRequestRef");
        assertFalse(ContractFixtureValidator.validate(contract,"TrackingStopReceiptRequest",missing).isEmpty());
        assertFalse(ContractFixtureValidator.validate(contract,"TrackingStopReceiptRequest",body.deepCopy().put("generation",0)).isEmpty());
    }
    @Test void responseRequiresRealConfirmationAndClosedPrivacyMinimalFields() throws Exception {
        var contract=contract(); var body=WaJson.object("""
            {"schemaVersion":1,"sessionId":"00000000-0000-4000-8000-000000000001","generation":1,
             "stopRequestRef":null,"stopConfirmed":true,"stoppedAt":"2030-01-01T12:00:00Z",
             "serverTime":"2030-01-01T12:05:00Z","runState":"COMPLETED"}
            """);
        assertTrue(ContractFixtureValidator.validate(contract,"TrackingStopReceiptResponse",body).isEmpty());
        assertFalse(ContractFixtureValidator.validate(contract,"TrackingStopReceiptResponse",body.deepCopy().put("stopConfirmed",false)).isEmpty());
        assertFalse(ContractFixtureValidator.validate(contract,"TrackingStopReceiptResponse",body.deepCopy().put("latitude",0)).isEmpty());
        var missing=body.deepCopy(); missing.remove("stoppedAt");
        assertFalse(ContractFixtureValidator.validate(contract,"TrackingStopReceiptResponse",missing).isEmpty());
        var operation=contract.path("paths").path("/driver-app/v1/tracking-sessions/{id}/stop-receipt").path("post");
        assertEquals(WaJson.object("{\"DPoPProof\":[]}"),operation.path("security").get(0));
    }
}
