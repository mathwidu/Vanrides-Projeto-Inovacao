package com.vanrides.vanrides.whatsapp.commands;

import java.util.List;
import java.util.Optional;

record WaPrompt(String question, Kind kind, List<Option> options) {
    enum Kind {
        BUTTONS(3),
        LIST(10);

        private final int maxOptions;

        Kind(int maxOptions) {
            this.maxOptions = maxOptions;
        }
    }

    record Option(String id, String label, List<String> aliases) {
        boolean matches(String text) {
            return WaConversationPolicy.normalize(label).equals(text) || aliases.contains(text);
        }
    }

    WaPrompt {
        if (options.isEmpty() || options.size() > kind.maxOptions) {
            throw new IllegalArgumentException("WA_PROMPT_OPTIONS");
        }
    }

    static WaPrompt yesNo(String question) {
        return new WaPrompt(
                question,
                Kind.BUTTONS,
                List.of(
                        new Option("YES", "Sim", List.of("quero receber")),
                        new Option("NO", "Não", List.of("nao quero receber"))));
    }

    static WaPrompt list(String question, List<Option> options) {
        return new WaPrompt(question, Kind.LIST, options);
    }

    String text() {
        List<String> labels = options.stream().map(Option::label).toList();
        return switch (kind) {
            case BUTTONS -> question + " Responda " + String.join(" ou ", labels) + ".";
            case LIST -> question + " Opções: " + String.join(", ", labels) + ".";
        };
    }

    Optional<Option> answer(String text) {
        return options.stream().filter(option -> option.matches(text)).findFirst();
    }
}
