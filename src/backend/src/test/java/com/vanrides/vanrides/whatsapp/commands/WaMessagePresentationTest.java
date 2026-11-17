package com.vanrides.vanrides.whatsapp.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import org.junit.jupiter.api.Test;

class WaMessagePresentationTest {
    @Test
    void homonymousTripsKeepDirectionTimeAndDaysInSeparateReadableBlocks() {
        ObjectNode outbound = routine("Faculdade", "OUTBOUND", "07:30:00");
        ObjectNode returning = routine("Faculdade", "RETURN", "18:00:00");
        ObjectNode driver = WaJson.object("{\"stage\":\"MENU\"}");
        driver.putArray("routineChoices").add(outbound).add(returning);
        String catalog = WaDriverTexts.prompt(driver);
        assertTrue(catalog.contains("Rotina 1: Faculdade\n→ IDA 07:30; fuso Brasília\nDias: segunda, quarta e sexta"), catalog);
        assertTrue(catalog.contains("\n\nRotina 2: Faculdade\n← VOLTA 18:00"), catalog);
        for (String command : java.util.List.of("CRIAR", "CONVIDAR", "RESUMO", "EDITAR N", "ORDEM N",
                "PAUSAR N", "RETOMAR N", "CANCELAR N", "REVOGAR N", "VOLTAR", "CORRIGIR", "ABANDONAR")) {
            assertTrue(catalog.contains(command), command);
        }
        ObjectNode passenger = WaJson.object("{\"stage\":\"ROUTINES\"}");
        passenger.putArray("routineChoices").add(outbound).add(returning);
        String choices = WaPassengerTexts.prompt(passenger);
        assertTrue(choices.contains("1: Faculdade\n→ Ida às 07:30\nDias: segunda, quarta e sexta"), choices);
        assertTrue(choices.contains("\n\n2: Faculdade\n← Volta às 18:00"), choices);
        assertTrue(choices.contains("como 1,2"), choices);
        passenger.put("action", "REQUEST_LINK").put("simpleSignup", true)
                .put("passengerName", "Pessoa sintética").put("driverName", "Motorista sintético");
        passenger.putArray("selections").add(outbound).add(returning);
        String review = WaPassengerTexts.review(passenger, "");
        assertTrue(review.contains("• Faculdade\n→ Ida às 07:30"), review);
        assertTrue(review.contains("\n\n• Faculdade\n← Volta às 18:00"), review);
        assertTrue(review.contains("CONFIRMAR em até 5 minutos"), review);
        assertTrue(review.length() <= 1900, review);
    }

    @Test
    void layoutKeepsWholeCataloguesWithinExistingLimitsAndNeverShowsBlockedChoices() {
        ObjectNode driver = WaJson.object("{\"stage\":\"MENU\"}");
        var routines = driver.putArray("routineChoices");
        for (int index = 0; index < 101; index++) routines.add(routine("Trajeto", "OUTBOUND", "07:30"));
        assertFalse(WaDriverTexts.menuFits(driver));
        assertEquals(WaDriverTexts.LIST_LIMIT, WaDriverTexts.prompt(driver));
        routines.removeAll();
        for (int index = 0; index < 12; index++) routines.add(routine("🚐".repeat(80), "RETURN", "18:00"));
        assertFalse(WaDriverTexts.menuFits(driver));
        String blocked = WaDriverTexts.prompt(driver);
        assertEquals(WaDriverTexts.LIST_LIMIT, blocked);
        assertTrue(blocked.length() <= 2000);
        assertFalse(blocked.contains("Rotina 1:"));

        ObjectNode passenger = WaJson.object("{\"stage\":\"ROUTINES\"}");
        var choices = passenger.putArray("routineChoices");
        for (int index = 0; index < 8; index++) choices.add(routine("N".repeat(80), "OUTBOUND", "07:30"));
        String visible = WaPassengerTexts.prompt(passenger);
        assertTrue(visible.contains("8: " + "N".repeat(80)));
        assertTrue(visible.length() <= 2000);
        passenger.put("routineChoicesBlocked", true);
        assertFalse(WaPassengerTexts.prompt(passenger).contains("1: "));
    }

    private static ObjectNode routine(String name, String direction, String time) {
        ObjectNode routine = WaJson.object("{\"weekdays\":[1,3,5],\"selectedDays\":[1,3,5]}");
        return routine.put("name", name).put("direction", direction).put("state", "ACTIVE")
                .put("localTime", time).put("local_time", time).put("timezone", "America/Sao_Paulo");
    }
}
