package com.vanrides.vanrides.whatsapp.tracking;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaApproachPolicyTest {
    private static final Instant NOW=Instant.parse("2030-01-01T12:00:00Z");
    private WaApproachPolicy.Fix fix(long seq,long offset,double lat,double lon,Double heading) {
        return new WaApproachPolicy.Fix(seq,NOW.plusSeconds(offset),lat,lon,5,heading);
    }
    private List<WaApproachPolicy.Fix> straight() { return List.of(fix(1,-10,0,-.007,90.0),fix(2,-5,0,-.0055,90.0),fix(3,0,0,-.004,90.0)); }
    @Test void versionedThreeFixHypothesisRecognizesCoherentApproachWithoutNumericEta() {
        var d=WaApproachPolicy.assess(straight(),0,0,NOW,true);assertTrue(d.approaching());assertEquals("APPROACHING",d.reason());assertEquals("approach-v1",WaApproachPolicy.VERSION);
    }
    @Test void unreviewedGradeSeparatedGeometryCannotBeAuthorizedFromGpsAlone() {
        assertEquals("UNREVIEWED_GEOGRAPHY",WaApproachPolicy.assess(straight(),0,0,NOW,false).reason());
    }
    @Test void twoFixesAreInsufficientEvenWhenInsideTheCircle() {
        assertFalse(WaApproachPolicy.assess(straight().subList(1,3),0,0,NOW,true).approaching());
    }
    @Test void parallelMovementWithoutHeadingTowardTheTargetSuppresses() {
        var fixes=List.of(fix(1,-10,.003,-.004,90.0),fix(2,-5,.003,-.003,90.0),fix(3,0,.003,-.002,90.0));
        assertFalse(WaApproachPolicy.assess(fixes,0,0,NOW,true).approaching());
    }
    @Test void stationaryFixesAndUncertainMovementNeverTrigger() {
        var fixes=List.of(fix(1,-10,0,-.004,90.0),fix(2,-5,0,-.004,90.0),fix(3,0,0,-.004,90.0));
        assertEquals("STATIONARY_OR_UNCERTAIN",WaApproachPolicy.assess(fixes,0,0,NOW,true).reason());
    }
    @Test void staleAcquisitionIsNeverRefreshedByReceiptTime() {
        assertEquals("INVALID_FIX",WaApproachPolicy.assess(straight(),0,0,NOW.plusSeconds(21),true).reason());
    }
    @Test void oppositeHeadingAndLargeJumpSuppress() {
        var fixes=List.of(fix(1,-10,0,-.007,270.0),fix(2,-5,0,-.0055,270.0),fix(3,0,0,-.004,270.0));
        assertEquals("DIRECTION_UNCERTAIN",WaApproachPolicy.assess(fixes,0,0,NOW,true).reason());
        assertEquals("GPS_JUMP",WaApproachPolicy.assess(List.of(fix(1,-2,0,-.05,90.0),fix(2,-1,0,-.005,90.0),fix(3,0,0,-.004,90.0)),0,0,NOW,true).reason());
    }
    @Test void movingAwayIsEvidenceOfDepartureButCannotGenerateApproach() {
        var fixes=List.of(fix(1,-10,0,.001,90.0),fix(2,-5,0,.0025,90.0),fix(3,0,0,.004,90.0));
        var d=WaApproachPolicy.assess(fixes,0,0,NOW,true);assertTrue(d.departing());assertFalse(d.approaching());
    }
}
