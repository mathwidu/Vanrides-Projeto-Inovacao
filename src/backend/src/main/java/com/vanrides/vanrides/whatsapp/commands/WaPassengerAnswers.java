package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.TreeSet;

/** Closed parsing of human answers; absence or ambiguity never implies a preference or weekday. */
final class WaPassengerAnswers {
    private WaPassengerAnswers() {}

    static boolean diverges(String text) {
        return text.matches(
                        ".*(?:pin|ponto|localizacao).*(?:errad[oa]|incorret[oa]|outro lugar|outro"
                                + " local|nao corresponde).*")
                || text.contains("nao e aqui")
                || text.contains("nao e esse ponto")
                || text.contains("nao e este ponto");
    }

    static String clean(String raw, int max) {
        String value = raw.replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "");
        return value.isBlank()
                        || value.length() > max
                        || value.matches("(?s).*[\\p{C}\\p{Zl}\\p{Zp}].*")
                ? null
                : value;
    }

    static ArrayNode days(String text) {
        if (text.equals("segunda a sexta")) {
            text = "1,2,3,4,5";
        }
        if (text.equals("todos os dias")) {
            text = "1,2,3,4,5,6,7";
        }
        if (!text.matches("[1-7](?:[, ]+[1-7]){0,6}")) {
            return null;
        }
        var values = List.of(text.split("[, ]+"));
        var unique = new TreeSet<>(values);
        if (values.size() != unique.size()) {
            return null;
        }
        ArrayNode result = WaDriverDraftStore.options();
        unique.forEach(selection -> result.add(Integer.parseInt(selection)));
        return result;
    }

    static boolean subset(JsonNode days, JsonNode available) {
        for (JsonNode day : days) {
            boolean found = false;
            for (JsonNode availableDay : available) {
                if (availableDay.asInt() == day.asInt()) {
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        return !days.isEmpty();
    }

    static void catalogue(ObjectNode draft, String field, ArrayNode choices) {
        draft.put(field + "Blocked", choices.size() > 8);
        draft.set(field, visible(choices));
    }

    static ArrayNode visible(ArrayNode choices) {
        if (choices.size() > 8) {
            return WaDriverDraftStore.options();
        }
        return choices;
    }

    static ArrayNode select(String text, JsonNode choices) {
        if (!text.matches("(?:[1-9][0-9]?|100)(?:[, ]+(?:[1-9][0-9]?|100)){0,99}")) {
            return null;
        }
        var seen = new TreeSet<Integer>();
        ArrayNode result = WaDriverDraftStore.options();
        for (String part : text.split("[, ]+")) {
            int index = Integer.parseInt(part) - 1;
            if (index >= choices.size() || !seen.add(index)) {
                return null;
            }
            result.add(choices.get(index).deepCopy());
        }
        return result;
    }
}
