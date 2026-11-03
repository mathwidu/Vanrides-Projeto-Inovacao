package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaTrackingPolicyTest {
    private static final Instant NOW=Instant.parse("2030-01-01T12:00:00Z");
    private ObjectNode sample() { return WaJson.object("{}").put("sequence",1).put("capturedAt",NOW.toString()).put("latitude",0).put("longitude",0).put("accuracyM",5); }
    @Test void acquisitionBoundariesAndSamePlaceNewFixAreValid() {
        assertNull(WaTrackingPolicy.rejection(sample(),NOW));
        assertNull(WaTrackingPolicy.rejection(sample().put("capturedAt",NOW.minusSeconds(20).toString()).put("accuracyM",50),NOW));
        assertNull(WaTrackingPolicy.rejection(sample().put("capturedAt",NOW.plusSeconds(10).toString()),NOW));
        assertEquals("STALE",WaTrackingPolicy.rejection(sample().put("capturedAt",NOW.minusSeconds(21).toString()),NOW));
        assertEquals("FUTURE",WaTrackingPolicy.rejection(sample().put("capturedAt",NOW.plusSeconds(11).toString()),NOW));
        assertEquals("LOW_ACCURACY",WaTrackingPolicy.rejection(sample().put("accuracyM",50.01),NOW));
    }
    @Test void finiteNumbersAndDocumentedRangesAreRequired() {
        assertEquals("INVALID_VALUE",WaTrackingPolicy.rejection(sample().put("latitude",Double.NaN),NOW));
        assertEquals("INVALID_VALUE",WaTrackingPolicy.rejection(sample().put("longitude",181),NOW));
        assertEquals("INVALID_VALUE",WaTrackingPolicy.rejection(sample().put("speedMps",151),NOW));
        assertEquals("INVALID_VALUE",WaTrackingPolicy.rejection(sample().put("headingDeg",360),NOW));
        assertNull(WaTrackingPolicy.rejection(sample().put("headingDeg",359.9).put("speedMps",150),NOW));
    }
    @Test void strictShapeAndIntegerSequencesRejectInventedFieldsAndAmbiguity() {
        assertThrows(WaProblem.class,()->WaTrackingPolicy.rejection(sample().put("driverId","untrusted"),NOW));
        assertThrows(WaProblem.class,()->WaTrackingPolicy.rejection(sample().put("sequence",0),NOW));
        assertThrows(WaProblem.class,()->WaTrackingPolicy.rejection(sample().put("sequence",1.5),NOW));
        assertThrows(WaProblem.class,()->WaTrackingPolicy.rejection(sample().put("capturedAt","tomorrow"),NOW));
    }
    @Test void receiptAndHeartbeatCannotTurnOldAcquisitionFresh() {
        assertTrue(WaTrackingPolicy.fresh(NOW,NOW.plusSeconds(20)));
        assertFalse(WaTrackingPolicy.fresh(NOW,NOW.plusSeconds(21)));
        assertFalse(WaTrackingPolicy.fresh(null,NOW));
        assertFalse(WaTrackingPolicy.fresh(NOW.plusSeconds(11),NOW));
    }
}
