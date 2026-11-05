package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.config.WaClock;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class WaPassengerNotice {
    private static final long WINDOW_SECONDS = 600;

    private final WaClock clock;

    WaPassengerNotice(WaClock clock) {
        this.clock = clock;
    }

    void open(ObjectNode draft) {
        draft.put("stage", "NOTICE").put("noticeEmittedAt", clock.now().toString());
    }

    boolean accepts(WaInboxAccess.Context event, ObjectNode draft) {
        try {
            Instant issued = Instant.parse(draft.path("noticeEmittedAt").asText());
            Instant received = Instant.parse(event.normalizedPayload().path("occurredAt").asText());
            return !received.isBefore(issued.truncatedTo(ChronoUnit.SECONDS))
                    && issued.plusSeconds(WINDOW_SECONDS).isAfter(clock.now());
        } catch (DateTimeException invalid) {
            return false;
        }
    }

    boolean expired(ObjectNode draft) {
        try {
            Instant issued = Instant.parse(draft.path("noticeEmittedAt").asText());
            return !issued.plusSeconds(WINDOW_SECONDS).isAfter(clock.now());
        } catch (DateTimeException invalid) {
            return true;
        }
    }
}
