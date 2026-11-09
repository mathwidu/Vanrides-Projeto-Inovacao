package com.vanrides.vanrides.whatsapp.tracking;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Versioned calibration hypothesis. Geographic review is external evidence, not GPS floor detection. */
public final class WaApproachPolicy {
    public static final String VERSION = "approach-v1";
    public static final double NOTICE_M = 800, PASS_M = 80, HYSTERESIS_M = 120;
    public record Fix(long sequence, Instant capturedAt, double latitude, double longitude, double accuracy, Double heading) { }
    public record Decision(String reason, boolean approaching, boolean departing, double distance) { }
    private WaApproachPolicy() { }

    public static Decision assess(List<Fix> fixes, double latitude, double longitude, Instant now, boolean reviewed) {
        double d = fixes.isEmpty() ? Double.POSITIVE_INFINITY : distance(fixes.get(fixes.size()-1), latitude, longitude);
        if (!reviewed) return new Decision("UNREVIEWED_GEOGRAPHY", false, false, d);
        if (fixes.size()!=3) return new Decision("INSUFFICIENT_FIXES", false, false, d);
        for (Fix fix:fixes) if (!WaTrackingPolicy.fresh(fix.capturedAt(), now) || fix.capturedAt().isAfter(now)
                || !Double.isFinite(fix.accuracy()) || fix.accuracy()>50) return new Decision("INVALID_FIX",false,false,d);
        Fix a=fixes.get(0), b=fixes.get(1), c=fixes.get(2);
        double ab=distance(a,b.latitude(),b.longitude()), bc=distance(b,c.latitude(),c.longitude());
        double dt1=Duration.between(a.capturedAt(),b.capturedAt()).toMillis()/1000.0;
        double dt2=Duration.between(b.capturedAt(),c.capturedAt()).toMillis()/1000.0;
        if (dt1<=0 || dt2<=0 || ab/dt1>45 || bc/dt2>45) return new Decision("GPS_JUMP",false,false,d);
        double movement=Math.max(10,Math.max(a.accuracy(),Math.max(b.accuracy(),c.accuracy())));
        if (ab<movement || bc<movement) return new Decision("STATIONARY_OR_UNCERTAIN",false,false,d);
        double first=distance(a,latitude,longitude), middle=distance(b,latitude,longitude);
        double bearing1=bearing(a.latitude(),a.longitude(),b.latitude(),b.longitude());
        double bearing2=bearing(b.latitude(),b.longitude(),c.latitude(),c.longitude());
        if (angle(bearing1,bearing2)>30 || b.heading()!=null && angle(b.heading(),bearing1)>45
                || c.heading()!=null && angle(c.heading(),bearing2)>45) return new Decision("DIRECTION_UNCERTAIN",false,false,d);
        boolean toward=first-middle>=movement && middle-d>=movement
            && angle(bearing2,bearing(c.latitude(),c.longitude(),latitude,longitude))<=30;
        boolean away=middle-first>=movement && d-middle>=movement
            && angle(bearing2,bearing(c.latitude(),c.longitude(),latitude,longitude))>=150;
        return new Decision(toward && d<=NOTICE_M ? "APPROACHING" : away ? "DEPARTING" : "OUTSIDE_OR_AMBIGUOUS",
            toward && d<=NOTICE_M,away,d);
    }
    public static double distance(Fix fix,double lat,double lon) {
        double a=Math.toRadians(lat-fix.latitude()), b=Math.toRadians(lon-fix.longitude());
        double h=Math.sin(a/2)*Math.sin(a/2)+Math.cos(Math.toRadians(fix.latitude()))*Math.cos(Math.toRadians(lat))*Math.sin(b/2)*Math.sin(b/2);
        return 6371000*2*Math.asin(Math.sqrt(Math.min(1,h)));
    }
    private static double bearing(double lat1,double lon1,double lat2,double lon2) {
        double d=Math.toRadians(lon2-lon1),a=Math.toRadians(lat1),b=Math.toRadians(lat2);
        return (Math.toDegrees(Math.atan2(Math.sin(d)*Math.cos(b),Math.cos(a)*Math.sin(b)-Math.sin(a)*Math.cos(b)*Math.cos(d)))+360)%360;
    }
    private static double angle(double a,double b) { double d=Math.abs(a-b)%360; return Math.min(d,360-d); }
}
