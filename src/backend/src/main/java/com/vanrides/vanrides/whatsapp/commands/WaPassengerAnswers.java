package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private static final List<String> DAY_NAMES =
            List.of("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo");
    private static final Map<String, Integer> DAY_NUMBERS =
            Map.ofEntries(
                    Map.entry("segunda", 1),
                    Map.entry("seg", 1),
                    Map.entry("terca", 2),
                    Map.entry("ter", 2),
                    Map.entry("quarta", 3),
                    Map.entry("qua", 3),
                    Map.entry("quinta", 4),
                    Map.entry("qui", 4),
                    Map.entry("sexta", 5),
                    Map.entry("sex", 5),
                    Map.entry("sabado", 6),
                    Map.entry("sab", 6),
                    Map.entry("domingo", 7),
                    Map.entry("dom", 7));

    static String loose(String normalized) {
        return normalized
                .replaceAll("[^\\p{L}\\p{N}, ]", " ")
                .replaceAll("^[ ,]+|[ ,]+$", "")
                .replaceAll(" +", " ");
    }

    static ArrayNode days(String text) {
        String list = text.equals("todos os dias") ? "1 2 3 4 5 6 7" : text;
        var selected = new TreeSet<Integer>();
        int previous = 0;
        boolean range = false;
        for (String token : list.split("[, ]+")) {
            if (token.equals("e") && range) {
                return null;
            }
            if (token.equals("e") || token.equals("feira")) {
                continue;
            }
            if (token.equals("a") || token.equals("ate")) {
                if (previous == 0 || range) {
                    return null;
                }
                range = true;
                continue;
            }
            Integer day =
                    token.matches("[1-7]") ? Integer.valueOf(token) : DAY_NUMBERS.get(token);
            if (day == null || range && day < previous) {
                return null;
            }
            int first = range ? previous + 1 : day;
            for (int selection = first; selection <= day; selection++) {
                if (!selected.add(selection)) {
                    return null;
                }
            }
            previous = day;
            range = false;
        }
        if (range || selected.isEmpty()) {
            return null;
        }
        ArrayNode result = WaDriverDraftStore.options();
        selected.forEach(result::add);
        return result;
    }

    static String dayNames(JsonNode days) {
        List<Integer> numbers = new ArrayList<>();
        days.forEach(day -> numbers.add(day.asInt()));
        if (numbers.isEmpty()) {
            return "";
        }
        if (numbers.equals(List.of(1, 2, 3, 4, 5, 6, 7))) {
            return "todos os dias";
        }
        if (numbers.equals(List.of(1, 2, 3, 4, 5))) {
            return "segunda a sexta";
        }
        List<String> names = numbers.stream().map(day -> DAY_NAMES.get(day - 1)).toList();
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1))
                + " e "
                + names.get(names.size() - 1);
    }

    static WaPrompt dayPrompt(String question, JsonNode available) {
        List<Integer> numbers = new ArrayList<>();
        available.forEach(day -> numbers.add(day.asInt()));
        List<WaPrompt.Option> options = new ArrayList<>();
        for (int day : numbers) {
            String label = DAY_NAMES.get(day - 1);
            options.add(
                    new WaPrompt.Option(
                            String.valueOf(day), capitalize(label), List.of()));
        }
        if (numbers.containsAll(List.of(1, 2, 3, 4, 5))) {
            options.add(new WaPrompt.Option("1,2,3,4,5", "Segunda a sexta", List.of()));
        }
        if (numbers.containsAll(List.of(1, 2, 3, 4, 5, 6, 7))) {
            options.add(new WaPrompt.Option("1,2,3,4,5,6,7", "Todos os dias", List.of()));
        }
        return WaPrompt.list(question, options);
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
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
