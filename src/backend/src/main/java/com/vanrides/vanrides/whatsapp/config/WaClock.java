package com.vanrides.vanrides.whatsapp.config;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public final class WaClock {
    private final Clock clock;
    public WaClock() { this(Clock.systemUTC()); }
    public WaClock(Clock clock) { this.clock = clock; }
    public Instant now() { return clock.instant(); }
}
