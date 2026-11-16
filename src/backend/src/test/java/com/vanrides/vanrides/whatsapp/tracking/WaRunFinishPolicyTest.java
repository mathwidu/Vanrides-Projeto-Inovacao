package com.vanrides.vanrides.whatsapp.tracking;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaRunFinishPolicyTest {
    private static final Instant START = Instant.parse("2030-01-01T12:00:00Z");
    private WaRunFinishPolicy.Decision fix(WaRunFinishPolicy.Progress prior, int seconds, double distance, Double speed, boolean pickups) {
        return WaRunFinishPolicy.assess(prior, START.plusSeconds(seconds), START.plusSeconds(seconds), distance, 5, speed, pickups);
    }
    @Test void arrivalRequiresObservedProgressBeforeContinuousDwell() {
        var progress = fix(null, 0, 300, 10.0, false).progress();
        for (int second=10;second<70;second+=10) {
            var result=fix(progress,second,10,0.0,true); assertFalse(result.finish()); progress=result.progress();
        }
        assertTrue(fix(progress,70,10,0.0,true).finish());
    }
    @Test void beingAtDestinationSinceStartupDoesNotProveACompletedJourney() {
        WaRunFinishPolicy.Progress progress=null;
        for(int second=0;second<=120;second+=10) {
            var result=fix(progress,second,0,0.0,true); assertFalse(result.finish()); progress=result.progress();
        }
    }
    @Test void pendingPickupsBlockArrivalEvenAfterLongDwell() {
        var progress=fix(null,0,300,10.0,false).progress();
        for(int second=10;second<=120;second+=10) {
            var result=fix(progress,second,0,0.0,false); assertFalse(result.finish()); progress=result.progress();
        }
    }
    @Test void gapCannotBeCountedAsTimeParked() {
        var outside=fix(null,0,300,10.0,true).progress();
        var arrived=fix(outside,10,0,0.0,true).progress();
        var afterGap=fix(arrived,80,0,0.0,true);
        assertFalse(afterGap.finish()); assertEquals(START.plusSeconds(80),afterGap.progress().arrivedSince());
    }
    @Test void departureAndUnprovenSpeedResetTheArrivalWindow() {
        var outside=fix(null,0,300,10.0,true).progress();
        var arrived=fix(outside,10,0,0.0,true).progress();
        for(var result:new WaRunFinishPolicy.Decision[]{fix(arrived,20,80,0.0,true),fix(arrived,20,0,null,true),fix(arrived,20,0,3.0,true)}) {
            assertFalse(result.finish()); assertNull(result.progress().arrivedSince());
        }
    }
    @Test void uncertaintyMarginRequiresProofOnBothSidesOfTheGeofence() {
        assertFalse(fix(null,0,202,0.0,true).progress().outside());
        var outside=fix(null,0,300,0.0,true).progress();
        assertNull(fix(outside,10,48,0.0,true).progress().arrivedSince());
    }
    @Test void inaccurateStaleAndFutureFixesCannotAccumulateArrival() {
        var prior=new WaRunFinishPolicy.Progress(true,START.minusSeconds(60),START.minusSeconds(10));
        for(var result:new WaRunFinishPolicy.Decision[]{
            WaRunFinishPolicy.assess(prior,START,START,0,26,0.0,true),
            WaRunFinishPolicy.assess(prior,START.minusSeconds(21),START,0,5,0.0,true),
            WaRunFinishPolicy.assess(prior,START.plusSeconds(1),START,0,5,0.0,true),
            WaRunFinishPolicy.assess(prior,START,START,Double.NaN,5,0.0,true)}) {
            assertFalse(result.finish()); assertNull(result.progress().arrivedSince());
        }
    }
    @Test void aNewSessionCannotInheritArrivalEvidence() {
        assertFalse(fix(null,100,0,0.0,true).finish());
    }
}
