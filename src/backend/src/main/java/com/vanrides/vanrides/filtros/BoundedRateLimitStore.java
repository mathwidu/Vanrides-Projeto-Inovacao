package com.vanrides.vanrides.filtros;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

@Component
public final class BoundedRateLimitStore {

    private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
    private static final Pattern TRUNCATED_HMAC_HEX = Pattern.compile("[0-9a-f]{32}");

    public enum Route {
        LOGIN,
        REFRESH,
        EMAIL_CONFIRM,
        EMAIL_RESEND,
        CHANGE_EMAIL,
        BOOTSTRAP,
        PASSENGER_REGISTRATION,
        DRIVER_REGISTRATION,
        DRIVER_INVITE_PREVIEW,
        DRIVER_INVITE_REDEEM,
        PASSENGER_INVITE_REDEEM
    }

    public record Decision(
            boolean allowed,
            boolean capacityRejected,
            long retryAfterSeconds,
            int expiredEntries,
            int entries
    ) {
    }

    private final int limit;
    private final long windowNanos;
    private final int capacityPerRoute;
    private final LongSupplier ticker;
    private final Map<Route, Partition> partitions = new EnumMap<>(Route.class);

    @Autowired
    public BoundedRateLimitStore(
            @Value("${vanrides.rate-limit.sensitive-limit:30}") int limit,
            @Value("${vanrides.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${vanrides.rate-limit.max-entries-per-route:10000}") int capacityPerRoute
    ) {
        this(limit, durationFromSeconds(windowSeconds), capacityPerRoute, System::nanoTime);
    }

    BoundedRateLimitStore(
            int limit,
            Duration window,
            int capacityPerRoute,
            LongSupplier ticker
    ) {
        if (limit <= 0) {
            throw new IllegalArgumentException("rate limit must be positive");
        }
        Objects.requireNonNull(window, "window must not be null");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("rate limit window must be positive");
        }
        if (capacityPerRoute <= 0) {
            throw new IllegalArgumentException("rate limit capacity per route must be positive");
        }

        this.limit = limit;
        this.windowNanos = toNanos(window);
        this.capacityPerRoute = capacityPerRoute;
        this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
        for (Route route : Route.values()) {
            partitions.put(route, new Partition());
        }
    }

    public Decision acquire(Route route, String keyDigest) {
        Objects.requireNonNull(route, "route must not be null");
        String normalizedDigest = normalizeDigest(keyDigest);
        Partition partition = partitions.get(route);

        synchronized (partition) {
            long nowNanos = monotonicNow(partition);
            int expiredEntries = removeExpired(partition, nowNanos);
            WindowCounter counter = partition.entries.get(normalizedDigest);

            if (counter == null) {
                if (partition.entries.size() >= capacityPerRoute) {
                    return new Decision(
                            false,
                            true,
                            retryUntilCapacity(partition, nowNanos),
                            expiredEntries,
                            partition.entries.size()
                    );
                }
                counter = new WindowCounter(nowNanos);
                partition.entries.put(normalizedDigest, counter);
            }

            counter.incrementSaturated(limit);
            boolean allowed = counter.count <= limit;
            return new Decision(
                    allowed,
                    false,
                    allowed ? 0 : retryAfterSeconds(counter.startedAtNanos, nowNanos),
                    expiredEntries,
                    partition.entries.size()
            );
        }
    }

    int entries(Route route) {
        Objects.requireNonNull(route, "route must not be null");
        Partition partition = partitions.get(route);
        synchronized (partition) {
            return partition.entries.size();
        }
    }

    private long monotonicNow(Partition partition) {
        long nowNanos = ticker.getAsLong();
        if (partition.timeObserved && nowNanos < partition.lastObservedNanos) {
            throw new IllegalStateException("rate limit ticker must be monotonic");
        }
        partition.timeObserved = true;
        partition.lastObservedNanos = nowNanos;
        return nowNanos;
    }

    private int removeExpired(Partition partition, long nowNanos) {
        int removed = 0;
        Iterator<Map.Entry<String, WindowCounter>> iterator = partition.entries.entrySet().iterator();
        while (iterator.hasNext()) {
            WindowCounter counter = iterator.next().getValue();
            if (!isExpired(counter.startedAtNanos, nowNanos)) {
                break;
            }
            iterator.remove();
            removed++;
        }
        return removed;
    }

    private long retryUntilCapacity(Partition partition, long nowNanos) {
        WindowCounter oldest = partition.entries.values().iterator().next();
        return retryAfterSeconds(oldest.startedAtNanos, nowNanos);
    }

    private boolean isExpired(long startedAtNanos, long nowNanos) {
        return nowNanos - startedAtNanos >= windowNanos;
    }

    private long retryAfterSeconds(long startedAtNanos, long nowNanos) {
        long elapsed = nowNanos - startedAtNanos;
        long remaining = Math.max(1, windowNanos - elapsed);
        return 1 + ((remaining - 1) / NANOS_PER_SECOND);
    }

    private static String normalizeDigest(String keyDigest) {
        Objects.requireNonNull(keyDigest, "key digest must not be null");
        String normalized = keyDigest.toLowerCase(Locale.ROOT);
        if (!TRUNCATED_HMAC_HEX.matcher(normalized).matches()) {
            throw new IllegalArgumentException("key digest must be a 128-bit HMAC hexadecimal value");
        }
        return normalized;
    }

    private static Duration durationFromSeconds(long windowSeconds) {
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("rate limit window seconds must be positive");
        }
        return Duration.ofSeconds(windowSeconds);
    }

    private static long toNanos(Duration window) {
        try {
            long nanos = window.toNanos();
            if (nanos <= 0) {
                throw new IllegalArgumentException("rate limit window is too small");
            }
            return nanos;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("rate limit window is too large", exception);
        }
    }

    private static final class Partition {
        private final LinkedHashMap<String, WindowCounter> entries = new LinkedHashMap<>();
        private boolean timeObserved;
        private long lastObservedNanos;
    }

    private static final class WindowCounter {
        private final long startedAtNanos;
        private int count;

        private WindowCounter(long startedAtNanos) {
            this.startedAtNanos = startedAtNanos;
        }

        private void incrementSaturated(int limit) {
            if (count <= limit) {
                count++;
            }
        }
    }
}
