package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

/** Acquisition time, never receipt/heartbeat time, establishes GPS freshness. */
public final class WaTrackingPolicy {
    public static final int LEASE_SECONDS = 300;
    public static final int START_SECONDS = 30;
    private WaTrackingPolicy() { }
    public static Instant instant(JsonNode body, String field) {
        try { return Instant.parse(WaJson.text(body, field, 20, 35)); }
        catch (DateTimeParseException e) { throw WaProblem.invalid(); }
    }
    public static long positive(JsonNode body, String field) {
        var value = body.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue()<1 || value.longValue()>9007199254740991L) throw WaProblem.invalid();
        return value.longValue();
    }
    public static void optionalFields(JsonNode body, Set<String> required, Set<String> optional) {
        if (!body.isObject()) throw WaProblem.invalid();
        body.fieldNames().forEachRemaining(name -> { if (!required.contains(name) && !optional.contains(name)) throw WaProblem.invalid(); });
        for (String name : required) if (!body.hasNonNull(name)) throw WaProblem.invalid();
    }
    public static String rejection(JsonNode sample, Instant now) {
        optionalFields(sample, Set.of("sequence","capturedAt","latitude","longitude","accuracyM"), Set.of("speedMps","headingDeg"));
        positive(sample,"sequence"); Instant captured = instant(sample,"capturedAt");
        if (!number(sample,"latitude",-90,90) || !number(sample,"longitude",-180,180)
                || !number(sample,"accuracyM",0,10000)
                || sample.has("speedMps") && !number(sample,"speedMps",0,150)
                || sample.has("headingDeg") && (!number(sample,"headingDeg",0,360) || sample.path("headingDeg").doubleValue()==360)) return "INVALID_VALUE";
        if (captured.isAfter(now.plusSeconds(10))) return "FUTURE";
        if (captured.isBefore(now.minusSeconds(20))) return "STALE";
        if (sample.path("accuracyM").doubleValue()>50) return "LOW_ACCURACY";
        return null;
    }
    public static boolean fresh(Instant captured, Instant now) {
        return captured != null && !captured.isBefore(now.minusSeconds(20)) && !captured.isAfter(now.plusSeconds(10));
    }
    private static boolean number(JsonNode sample,String name,double min,double max) {
        var value=sample.get(name);
        return value!=null && value.isNumber() && Double.isFinite(value.doubleValue())
            && value.doubleValue()>=min && value.doubleValue()<=max;
    }
}
