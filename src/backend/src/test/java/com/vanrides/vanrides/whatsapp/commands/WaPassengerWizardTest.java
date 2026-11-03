package com.vanrides.vanrides.whatsapp.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaJson;

import org.junit.jupiter.api.Test;

class WaPassengerWizardTest {
    @Test
    void weekdaysAreExplicitUniqueAndBoundedByTheRoutine() {
        assertEquals("[1,2,3,4,5]", WaPassengerAnswers.days("segunda a sexta").toString());
        assertNull(WaPassengerAnswers.days("1,1,3"));
        assertNull(WaPassengerAnswers.days("talvez"));
        assertTrue(
                WaPassengerAnswers.subset(
                        WaPassengerAnswers.days("1,3"),
                        WaPassengerAnswers.days("segunda a sexta")));
        assertFalse(
                WaPassengerAnswers.subset(
                        WaPassengerAnswers.days("6"), WaPassengerAnswers.days("segunda a sexta")));
    }

    @Test
    void preferenceRequiresExplicitPositiveOrNegativeAnswer() {
        assertEquals(Boolean.TRUE, WaPassengerAnswers.yesNo("sim"));
        assertEquals(Boolean.FALSE, WaPassengerAnswers.yesNo("nao"));
        assertNull(WaPassengerAnswers.yesNo("continuar"));
        assertNull(WaPassengerAnswers.yesNo("aceito"));
    }

    @Test
    void rejectsControlAndInvisibleNamesWithoutDiscardingAccents() {
        for (String value :
                java.util.List.of(
                        "\u00a0", "Nome\nOutro", "Nome\u200b", "Nome\u202e", "Nome\u2028Outro")) {
            assertNull(WaPassengerAnswers.clean(value, 80));
        }
        assertEquals("São José", WaPassengerAnswers.clean("\u00a0São José\u00a0", 80));
    }

    @Test
    void menuHasNoSelectableEntriesWhenTheVisibleCatalogueIsBlocked() {
        var draft = WaJson.object("{\"stage\":\"DRIVER_MENU\",\"catalogBlocked\":true}");
        String text = WaPassengerTexts.prompt(draft);
        assertTrue(text.contains("Nenhuma opção"));
        assertFalse(text.contains("APROVAR"));
    }
}
