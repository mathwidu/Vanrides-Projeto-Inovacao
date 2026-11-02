package dev.matheusduarte.vanrides.gnssprobe;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** One finite foreground observation. No coordinates, storage, network or shell identity. */
public final class DirectGnssProbe extends Instrumentation {
    private static final String TARGET = "dev.matheusduarte.vanrides.driver";
    private static final String ACTIVITY = "com.example.vanrides_frontend_dev.driver.DriverActivity";
    private static final long WINDOW_MS = 180_000L;
    private static final long STARTUP_MS = 55_000L;
    private final Object lock = new Object();
    private final AtomicBoolean ended = new AtomicBoolean();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledThreadPoolExecutor watchdog = new ScheduledThreadPoolExecutor(1);
    private Thread hardDeadline;
    private volatile Activity driverActivity;
    private volatile boolean resumed;
    private LocationManager manager;
    private boolean gpsAttempted, gnssAttempted, foregroundVerified;
    private boolean gpsRemoved, gnssRemoved;
    private long executionStartMs, windowStartNanos, previousCallbackMs, lastFixNanos;
    private int callbacks, eligible, fromWindow, nonAdvancing, invalidAge, futureAge;
    private long maxGapMs, maxEligibleGapMs, previousEligibleMs;
    private Long firstCallbackMs, firstEligibleMs, lastCallbackMs, lastAgeMs;
    private Long onStartAfterMs, launchRequestedAfterMs, launchReturnedAfterMs;
    private Long firstResumeAfterMs, foregroundReadyAfterMs;
    private int startupPolls;
    private String ageState = "MISSING";
    private String accuracyState = "MISSING";
    private Double lastAccuracy, minAccuracy, maxAccuracy;
    private int statusCallbacks, visibleMax, usedMax, cn0Count;
    private double cn0Sum, cn0Max;
    private boolean gnssStarted, gnssStopped;
    private Integer gnssTtffMs;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        executionStartMs = SystemClock.elapsedRealtime();
        watchdog.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        watchdog.schedule(() -> complete("WATCHDOG_TIMEOUT"), 240, TimeUnit.SECONDS);
        // Independent of the main looper, registration lock and a stuck binder cleanup.
        // A hard exit can lose the result: absence of JSON is an incomplete probe.
        hardDeadline = new Thread(() -> {
            try { Thread.sleep(244_000L); }
            catch (InterruptedException expected) { return; }
            android.os.Process.killProcess(android.os.Process.myPid());
        }, "gnss-probe-hard-cutoff");
        hardDeadline.setDaemon(true);
        hardDeadline.start();
        start();
    }

    @Override public void onStart() {
        try {
            if (!TARGET.equals(getTargetContext().getPackageName())) { complete("WRONG_TARGET"); return; }
            synchronized (lock) {
                if (ended.get()) return;
                onStartAfterMs = SystemClock.elapsedRealtime() - executionStartMs;
                if (!main.post(this::launch)) complete("STARTUP_FAILED");
            }
        } catch (SecurityException ignored) { complete("PERMISSION_DENIED"); }
        catch (RuntimeException ignored) { complete("STARTUP_FAILED"); }
    }

    private void launch() {
        try {
            synchronized (lock) {
                if (ended.get()) return;
                if (SystemClock.elapsedRealtime() - executionStartMs >= STARTUP_MS) {
                    complete("STARTUP_TIMEOUT"); return;
                }
                launchRequestedAfterMs = SystemClock.elapsedRealtime() - executionStartMs;
                Intent intent = new Intent(Intent.ACTION_MAIN).setClassName(TARGET, ACTIVITY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // No instrumentation idle/animation waiter; readiness is checked below.
                getTargetContext().startActivity(intent);
                launchReturnedAfterMs = SystemClock.elapsedRealtime() - executionStartMs;
            }
            main.post(awaitForeground);
        } catch (SecurityException ignored) { complete("PERMISSION_DENIED"); }
        catch (RuntimeException ignored) { complete("STARTUP_FAILED"); }
    }

    private final Runnable awaitForeground = new Runnable() {
        @Override public void run() {
            synchronized (lock) {
                if (ended.get() || windowStartNanos != 0) return;
                startupPolls++;
                if (SystemClock.elapsedRealtime() - executionStartMs >= STARTUP_MS) {
                    complete("STARTUP_TIMEOUT"); return;
                }
                if (!finePermission()) { complete("PERMISSION_DENIED"); return; }
                if (foreground()) { begin(); return; }
                main.postDelayed(this, 100L);
            }
        }
    };

    @Override public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        if (ACTIVITY.equals(activity.getClass().getName())) {
            driverActivity = activity;
            resumed = true;
            synchronized (lock) {
                if (!ended.get() && firstResumeAfterMs == null) {
                    firstResumeAfterMs = SystemClock.elapsedRealtime() - executionStartMs;
                }
            }
        }
    }

    @Override public void callActivityOnPause(Activity activity) {
        super.callActivityOnPause(activity);
        if (activity == driverActivity) {
            resumed = false;
            if (windowStartNanos != 0) main.post(() -> complete("FOREGROUND_LOST"));
        }
    }

    private boolean finePermission() {
        return getTargetContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    }

    private boolean foreground() {
        Activity activity = driverActivity;
        return resumed && activity != null && !activity.isFinishing() && !activity.isDestroyed()
            && activity.hasWindowFocus();
    }

    private void begin() {
        try {
            synchronized (lock) {
                if (ended.get()) return;
                if (!finePermission()) { complete("PERMISSION_DENIED"); return; }
                if (!foreground()) { complete("NOT_FOREGROUND"); return; }
                manager = (LocationManager) getTargetContext().getSystemService(Context.LOCATION_SERVICE);
                if (manager == null || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    complete("GPS_UNAVAILABLE"); return;
                }
                foregroundVerified = true;
                foregroundReadyAfterMs = SystemClock.elapsedRealtime() - executionStartMs;
                windowStartNanos = SystemClock.elapsedRealtimeNanos();
                previousCallbackMs = windowStartNanos / 1_000_000L;
                previousEligibleMs = previousCallbackMs;
                // Mark attempts before binder calls: cleanup also covers partial registration.
                gpsAttempted = true;
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, locations, Looper.getMainLooper());
                gnssAttempted = true;
                if (!manager.registerGnssStatusCallback(gnss, main)) {
                    complete("GNSS_REGISTRATION_FAILED"); return;
                }
            }
            main.post(guard);
        } catch (SecurityException ignored) { complete("PERMISSION_DENIED"); }
        catch (RuntimeException ignored) { complete("REGISTRATION_FAILED"); }
    }

    private final Runnable guard = new Runnable() {
        @Override public void run() {
            if (canObserve()) main.postDelayed(this, 250L);
        }
    };

    private boolean canObserve() {
        if (ended.get()) return false;
        if (!foreground()) { complete("FOREGROUND_LOST"); return false; }
        if (!finePermission()) { complete("PERMISSION_REVOKED"); return false; }
        if (SystemClock.elapsedRealtimeNanos() - windowStartNanos >= WINDOW_MS * 1_000_000L) {
            complete("COMPLETE"); return false;
        }
        return true;
    }

    private final LocationListener locations = new LocationListener() {
        @Override public void onLocationChanged(Location value) {
            if (!canObserve()) return;
            synchronized (lock) {
                if (ended.get()) return;
                long now = SystemClock.elapsedRealtimeNanos();
                long receivedMs = now / 1_000_000L;
                long afterMs = (now - windowStartNanos) / 1_000_000L;
                callbacks++;
                if (firstCallbackMs == null) firstCallbackMs = afterMs;
                lastCallbackMs = afterMs;
                maxGapMs = Math.max(maxGapMs, receivedMs - previousCallbackMs);
                previousCallbackMs = receivedMs;
                long captured = value.getElapsedRealtimeNanos();
                lastAgeMs = captured > 0 ? (now - captured) / 1_000_000L : null;
                ageState = captured <= 0 ? "INVALID" : captured > now ? "FUTURE" : "VALID";
                if (captured <= 0) invalidAge++;
                if (captured > now) futureAge++;
                double accuracy = value.getAccuracy();
                lastAccuracy = value.hasAccuracy() && Double.isFinite(accuracy) && accuracy >= 0 ? accuracy : null;
                accuracyState = !value.hasAccuracy() ? "MISSING" : lastAccuracy == null ? "INVALID" : "VALID";
                if (lastAccuracy != null) {
                    minAccuracy = minAccuracy == null ? accuracy : Math.min(minAccuracy, accuracy);
                    maxAccuracy = maxAccuracy == null ? accuracy : Math.max(maxAccuracy, accuracy);
                }
                boolean advances = captured > lastFixNanos;
                if (!advances) nonAdvancing++;
                if (captured > windowStartNanos && captured <= now) fromWindow++;
                if (advances && captured > windowStartNanos && captured <= now
                    && now - captured <= 20_000_000_000L && lastAccuracy != null && lastAccuracy <= 50) {
                    eligible++;
                    maxEligibleGapMs = Math.max(maxEligibleGapMs, receivedMs - previousEligibleMs);
                    previousEligibleMs = receivedMs;
                    if (firstEligibleMs == null) firstEligibleMs = afterMs;
                }
                if (captured > 0 && captured <= now) lastFixNanos = Math.max(lastFixNanos, captured);
            }
        }
        // Implement all API29 callbacks, even when compiling against a newer android.jar.
        @Override public void onProviderDisabled(String provider) { complete("GPS_DISABLED"); }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    };

    private final GnssStatus.Callback gnss = new GnssStatus.Callback() {
        @Override public void onStarted() { if (canObserve()) synchronized (lock) { if (!ended.get()) gnssStarted = true; } }
        @Override public void onStopped() { if (canObserve()) synchronized (lock) { if (!ended.get()) gnssStopped = true; } }
        @Override public void onFirstFix(int ttffMillis) { if (canObserve()) synchronized (lock) { if (!ended.get() && ttffMillis >= 0) gnssTtffMs = ttffMillis; } }
        @Override public void onSatelliteStatusChanged(GnssStatus status) {
            if (!canObserve()) return;
            synchronized (lock) {
                if (ended.get()) return;
                statusCallbacks++;
                int visible = status.getSatelliteCount(), used = 0;
                for (int index = 0; index < visible; index++) {
                    if (status.usedInFix(index)) used++;
                    double cn0 = status.getCn0DbHz(index);
                    if (Double.isFinite(cn0) && cn0 >= 0) { cn0Count++; cn0Sum += cn0; cn0Max = Math.max(cn0Max, cn0); }
                }
                visibleMax = Math.max(visibleMax, visible);
                usedMax = Math.max(usedMax, used);
            }
        }
    };

    private void complete(String reason) {
        // Serialize terminal transition with registration; late callbacks cannot modify results.
        synchronized (lock) {
            if (!ended.compareAndSet(false, true)) return;
            long observationEndMs = SystemClock.elapsedRealtime();
            main.removeCallbacksAndMessages(null);
            try {
                // Each removal is attempted even if the other one fails.
                try {
                    try { if (gpsAttempted) manager.removeUpdates(locations); gpsRemoved = true; }
                    catch (RuntimeException ignored) { gpsRemoved = false; }
                } finally {
                    try { if (gnssAttempted) manager.unregisterGnssStatusCallback(gnss); gnssRemoved = true; }
                    catch (RuntimeException ignored) { gnssRemoved = false; }
                }
            } finally {
                watchdog.shutdown();
                String json;
                boolean serializationSucceeded = true;
                try {
                    long now = SystemClock.elapsedRealtime();
                    long observed = windowStartNanos == 0 ? 0 : observationEndMs - windowStartNanos / 1_000_000L;
                    if (windowStartNanos != 0) maxGapMs = Math.max(maxGapMs, observationEndMs - previousCallbackMs);
                    if (windowStartNanos != 0) maxEligibleGapMs = Math.max(maxEligibleGapMs, observationEndMs - previousEligibleMs);
                    JSONObject result = new JSONObject();
                    result.put("schemaVersion", 1).put("reason", reason)
                        .put("executionMs", now - executionStartMs).put("observedMs", observed)
                        .put("cleanupMs", now - observationEndMs)
                        .put("onStartAfterMs", nullable(onStartAfterMs)).put("launchRequestedAfterMs", nullable(launchRequestedAfterMs))
                        .put("launchReturnedAfterMs", nullable(launchReturnedAfterMs)).put("firstResumeAfterMs", nullable(firstResumeAfterMs))
                        .put("foregroundReadyAfterMs", nullable(foregroundReadyAfterMs)).put("startupPolls", startupPolls)
                        .put("foregroundVerified", foregroundVerified).put("callbacks", callbacks)
                        .put("capturedAfterStartCount", fromWindow).put("eligibleCount", eligible)
                        .put("nonAdvancingCount", nonAdvancing).put("invalidAgeCount", invalidAge).put("futureAgeCount", futureAge)
                        .put("firstCallbackAfterMs", nullable(firstCallbackMs)).put("firstEligibleAfterMs", nullable(firstEligibleMs))
                        .put("lastCallbackAfterMs", nullable(lastCallbackMs)).put("maxCallbackGapMs", maxGapMs)
                        .put("maxEligibleGapMs", maxEligibleGapMs)
                        .put("lastElapsedAgeAtCallbackMs", nullable(lastAgeMs)).put("lastAgeState", ageState)
                        .put("lastAccuracyState", accuracyState)
                        .put("lastAccuracyM", nullable(lastAccuracy)).put("minAccuracyM", nullable(minAccuracy)).put("maxAccuracyM", nullable(maxAccuracy))
                        .put("gnssStarted", gnssStarted).put("gnssStopped", gnssStopped).put("gnssNativeTtffMs", nullable(gnssTtffMs))
                        .put("gnssStatusCallbacks", statusCallbacks).put("visibleSatellitesMax", visibleMax).put("usedSatellitesMax", usedMax)
                        .put("cn0ObservationMeanDbHz", cn0Count == 0 ? JSONObject.NULL : cn0Sum / cn0Count)
                        .put("cn0MaxDbHz", cn0Count == 0 ? JSONObject.NULL : cn0Max)
                        .put("gpsRemovalAttempted", gpsAttempted).put("gpsRemovalSucceeded", gpsRemoved)
                        .put("gnssRemovalAttempted", gnssAttempted).put("gnssRemovalSucceeded", gnssRemoved);
                    json = result.toString();
                } catch (Exception ignored) {
                    serializationSucceeded = false;
                    json = "{\"schemaVersion\":1,\"reason\":\"RESULT_SERIALIZATION_FAILED\",\"cleanupUnconfirmed\":true}";
                }
                Bundle bundle = new Bundle();
                bundle.putString("gnss_probe_json", json);
                try {
                    finish("COMPLETE".equals(reason) && gpsRemoved && gnssRemoved && serializationSucceeded ? Activity.RESULT_OK : Activity.RESULT_CANCELED, bundle);
                } finally {
                    hardDeadline.interrupt();
                }
            }
        }
    }

    private static Object nullable(Object value) { return value == null ? JSONObject.NULL : value; }
}
