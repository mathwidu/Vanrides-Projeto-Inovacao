package com.vanrides.vanrides.whatsapp.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;

import org.junit.jupiter.api.Test;

class WaPassengerWizardTest {
    private static JsonNode array(String json) {
        return WaJson.object("{\"v\":" + json + "}").path("v");
    }

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
        var prompt = WaPassengerTexts.APPROACH;
        assertEquals("YES", prompt.answer("sim").orElseThrow().id());
        assertEquals("NO", prompt.answer("nao").orElseThrow().id());
        assertEquals("YES", prompt.answer("quero receber").orElseThrow().id());
        assertEquals("NO", prompt.answer("nao quero receber").orElseThrow().id());
        assertTrue(prompt.answer("continuar").isEmpty());
        assertTrue(prompt.answer("aceito").isEmpty());
        assertTrue(prompt.answer("talvez").isEmpty());
    }

    @Test
    void yesNoPromptRendersTheQuestionFollowedByItsAcceptedAnswers() {
        assertEquals(
                "Você deseja receber mensagens quando a van estiver se aproximando?\n\nResponda"
                        + " Sim ou Não.",
                WaPassengerTexts.APPROACH.text());
        assertEquals(
                "Você deseja receber lembretes para informar sua presença?\n\nResponda Sim ou"
                        + " Não.",
                WaPassengerTexts.REMINDER.text());
    }

    @Test
    void looseTextIgnoresPunctuationEmojiAndExtraSpacesButKeepsListSeparators() {
        assertEquals("sim", WaPassengerAnswers.loose("sim!!"));
        assertEquals("sim", WaPassengerAnswers.loose("  sim 👍 "));
        assertEquals("segunda feira, quarta", WaPassengerAnswers.loose("segunda-feira,   quarta..."));
        assertEquals("ponto 2", WaPassengerAnswers.loose("ponto   2"));
        assertEquals("sim", WaPassengerAnswers.loose("sim,"));
        assertEquals("1, 3", WaPassengerAnswers.loose(", 1, 3 ,"));
    }

    @Test
    void daysAcceptNamesAbbreviationsRangesAndStillNumbers() {
        assertEquals("[1,3,5]", WaPassengerAnswers.days("segunda, quarta e sexta").toString());
        assertEquals("[1,3,5]", WaPassengerAnswers.days("seg qua sex").toString());
        assertEquals("[2,4]", WaPassengerAnswers.days("terca feira e quinta feira").toString());
        assertEquals("[1,2,3,4,5]", WaPassengerAnswers.days("segunda a sexta").toString());
        assertEquals("[1,2,3]", WaPassengerAnswers.days("seg ate qua").toString());
        assertEquals("[1,2,3,4,5,6,7]", WaPassengerAnswers.days("todos os dias").toString());
        assertEquals("[6,7]", WaPassengerAnswers.days("sabado e domingo").toString());
        assertEquals("[1,3,5]", WaPassengerAnswers.days("1,3,5").toString());
        assertNull(WaPassengerAnswers.days("quarta a segunda"));
        assertNull(WaPassengerAnswers.days("segunda segunda"));
        assertNull(WaPassengerAnswers.days("segunda a"));
        assertNull(WaPassengerAnswers.days("segunda a e sexta"));
        assertNull(WaPassengerAnswers.days("a sexta"));
        assertNull(WaPassengerAnswers.days("feriado"));
        assertNull(WaPassengerAnswers.days(""));
    }

    @Test
    void dayNamesRenderReadableText() {
        assertEquals("todos os dias", WaPassengerAnswers.dayNames(array("[1,2,3,4,5,6,7]")));
        assertEquals("segunda a sexta", WaPassengerAnswers.dayNames(array("[1,2,3,4,5]")));
        assertEquals("terça", WaPassengerAnswers.dayNames(array("[2]")));
        assertEquals("", WaPassengerAnswers.dayNames(array("[]")));
        assertEquals("segunda, quarta e sábado", WaPassengerAnswers.dayNames(array("[1,3,6]")));
    }

    @Test
    void dayListOffersCapitalisedLabelsThatTheParserAccepts() {
        var prompt = WaPassengerAnswers.dayPrompt("Quais dias?", array("[1,2,3,4,5,6,7]"));
        assertEquals(
                "Quais dias?\n\n• Segunda\n• Terça\n• Quarta\n• Quinta\n• Sexta\n• Sábado\n•"
                        + " Domingo\n• Segunda a sexta\n• Todos os dias",
                prompt.text());
        for (var option : prompt.options()) {
            assertEquals(
                    option.id(),
                    WaPassengerAnswers.days(
                                    WaPassengerAnswers.loose(
                                            WaConversationPolicy.normalize(option.label())))
                            .toString()
                            .replaceAll("[\\[\\]]", ""),
                    option.label());
        }
        assertTrue(prompt.answer("terca").isPresent());
        var duplicated = WaPassengerAnswers.dayPrompt("?", array("[1,1,2,3,4,5,6]"));
        assertFalse(duplicated.text().contains("Todos os dias"), duplicated.text());
        var weekdaysOnly = WaPassengerAnswers.dayPrompt("Quais dias?", array("[1,3]"));
        assertEquals("Quais dias?\n\n• Segunda\n• Quarta", weekdaysOnly.text());
    }

    @Test
    void promptsRespectWhatsAppButtonAndListLimits() {
        var option = new WaPrompt.Option("A", "A", java.util.List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new WaPrompt("?", WaPrompt.Kind.BUTTONS, java.util.Collections.nCopies(4, option)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WaPrompt("?", WaPrompt.Kind.LIST, java.util.List.of()));
        assertDoesNotThrow(
                () -> new WaPrompt("?", WaPrompt.Kind.LIST, java.util.Collections.nCopies(10, option)));
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
    void helpAtTheReviewExplainsHowToConfirmOrGetANewCodeInOneMessage() {
        String text =
                WaPassengerTexts.prompt(
                        WaJson.object("{\"stage\":\"REVIEW\",\"confirmation\":{}}"));
        assertTrue(text.startsWith("Para confirmar, responda CONFIRMAR."), text);
        String missing = WaPassengerTexts.prompt(WaJson.object("{\"stage\":\"REVIEW\"}"));
        assertTrue(missing.startsWith("Envie CONTINUAR para receber o resumo novamente."), missing);
        assertTrue(text.contains("CONTINUAR para receber um resumo novo"), text);
        assertTrue(text.contains("CORRIGIR") && text.contains("ABANDONAR"), text);
        String coded =
                WaPassengerTexts.prompt(
                        WaJson.object(
                                "{\"stage\":\"REVIEW\",\"confirmation\":{\"markerHash\":\"x\"}}"));
        assertTrue(coded.contains("com o código do último resumo"), coded);
    }

    @Test
    void menuHasNoSelectableEntriesWhenTheVisibleCatalogueIsBlocked() {
        var draft = WaJson.object("{\"stage\":\"DRIVER_MENU\",\"catalogBlocked\":true}");
        String text = WaPassengerTexts.prompt(draft);
        assertTrue(text.contains("Nenhuma opção"));
        assertFalse(text.contains("APROVAR"));
    }
}
