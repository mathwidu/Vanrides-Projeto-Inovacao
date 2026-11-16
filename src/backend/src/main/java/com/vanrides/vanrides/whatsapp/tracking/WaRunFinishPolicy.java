package com.vanrides.vanrides.whatsapp.tracking;

import java.time.Instant;

/** Initial rehearsal parameters, not a claim of field calibration. No timer can finish a run. */
public final class WaRunFinishPolicy {
    public static final String VERSION = "arrival-v1";
    public static final int DWELL_SECONDS = 60, MAX_GAP_SECONDS = 20;
    public static final double ARRIVAL_M = 50, OUTSIDE_M = 200, MAX_ACCURACY_M = 25, MAX_SPEED_MPS = 2;
    public record Progress(boolean outside, Instant arrivedSince, Instant lastCaptured) { }
    public record Decision(Progress progress, boolean finish, String reason) { }
    private WaRunFinishPolicy() { }

    public static Decision assess(Progress previous, Instant captured, Instant now, double distance,
                                  double accuracy, Double speed, boolean pickupsComplete) {
        boolean outside = previous != null && previous.outside();
        boolean valid = captured != null && !captured.isAfter(now) && !captured.isBefore(now.minusSeconds(MAX_GAP_SECONDS))
            && Double.isFinite(distance) && distance >= 0 && Double.isFinite(accuracy) && accuracy >= 0 && accuracy <= MAX_ACCURACY_M;
        if (!valid) return decision(outside, null, captured, false, "INVALID_FIX");
        // Distance minus uncertainty must prove that the vehicle actually was outside.
        outside |= distance - accuracy >= OUTSIDE_M;
        boolean continuous = previous != null && previous.lastCaptured() != null
            && captured.isAfter(previous.lastCaptured()) && !captured.isAfter(previous.lastCaptured().plusSeconds(MAX_GAP_SECONDS));
        if (!pickupsComplete || !outside) return decision(outside, null, captured, false, "PROGRESS_REQUIRED");
        if (distance + accuracy > ARRIVAL_M) return decision(outside, null, captured, false, "OUTSIDE_DESTINATION");
        if (speed == null || !Double.isFinite(speed) || speed < 0 || speed > MAX_SPEED_MPS)
            return decision(outside, null, captured, false, "SPEED_UNPROVEN");
        Instant since = continuous && previous.arrivedSince() != null ? previous.arrivedSince() : captured;
        boolean finish = !captured.isBefore(since.plusSeconds(DWELL_SECONDS));
        return decision(outside, since, captured, finish, finish ? "ARRIVED" : "DWELL_PENDING");
    }
    private static Decision decision(boolean outside, Instant since, Instant captured, boolean finish, String reason) {
        return new Decision(new Progress(outside, since, captured), finish, reason);
    }
}
