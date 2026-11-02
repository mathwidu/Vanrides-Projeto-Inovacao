package com.vanrides.vanrides.whatsapp.operator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

final class WaOperatorMutableClock {
    private final AtomicReference<Instant> current;
    WaOperatorMutableClock(Instant current) { this.current = new AtomicReference<>(current); }
    Clock source() { return source(ZoneOffset.UTC); }
    private Clock source(ZoneId zone) {
        return new Clock() {
            @Override public ZoneId getZone() { return zone; }
            @Override public Clock withZone(ZoneId requested) { return source(requested); }
            @Override public Instant instant() { return current.get(); }
        };
    }
    void set(Instant value) { current.set(value); }
}
