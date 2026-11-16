package com.vanrides.vanrides.whatsapp.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import org.junit.jupiter.api.Test;

class WaConversationOwnershipTest {
    @Test void aLegacyPassengerAttendanceDraftNoLongerOwnsTheConversation() {
        for (String kind : new String[] {"PASSENGER_MENU", "PASSENGER_SELECTED"}) {
            ObjectNode context = context(kind).put("conversationOwner", "ATTENDANCE");
            assertThat(WaConversationOwnership.owner(context)).isEqualTo("READY");
            assertThat(WaConversationOwnership.owner(context(kind))).isEqualTo("READY");
        }
    }

    @Test void aLegacyPassengerAttendanceDraftFallsThroughToAnotherDraft() {
        ObjectNode context = context("PASSENGER_MENU").put("conversationOwner", "ATTENDANCE");
        context.putObject("passengerDraft").put("stage", "NAME");
        assertThat(WaConversationOwnership.owner(context)).isEqualTo("PASSENGER_SETUP");
    }

    @Test void aDriverAttendanceDraftStillOwnsTheConversation() {
        assertThat(WaConversationOwnership.owner(context("DRIVER_MENU").put("conversationOwner", "ATTENDANCE"))).isEqualTo("ATTENDANCE");
        assertThat(WaConversationOwnership.owner(context("DRIVER_SUMMARY"))).isEqualTo("ATTENDANCE");
    }

    private static ObjectNode context(String kind) {
        ObjectNode context = WaJson.MAPPER.createObjectNode();
        context.putObject("attendanceDraft").put("kind", kind).put("page", 0);
        return context;
    }
}
