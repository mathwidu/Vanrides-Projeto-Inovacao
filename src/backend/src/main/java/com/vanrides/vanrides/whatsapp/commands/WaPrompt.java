package com.vanrides.vanrides.whatsapp.commands;

import java.util.List;
import java.util.Optional;

record WaPrompt(String question, List<Option> options) {
    record Option(String id, String label, List<String> aliases) {
        boolean matches(String text) {
            return WaConversationPolicy.normalize(label).equals(text) || aliases.contains(text);
        }
    }

    static WaPrompt yesNo(String question) {
        return new WaPrompt(
                question,
                List.of(
                        new Option("YES", "SIM", List.of("quero receber")),
                        new Option("NO", "NÃO", List.of("nao quero receber"))));
    }

    String text() {
        return question
                + " Responda "
                + String.join(" ou ", options.stream().map(Option::label).toList())
                + ".";
    }

    Optional<Option> answer(String text) {
        return options.stream().filter(option -> option.matches(text)).findFirst();
    }
}
