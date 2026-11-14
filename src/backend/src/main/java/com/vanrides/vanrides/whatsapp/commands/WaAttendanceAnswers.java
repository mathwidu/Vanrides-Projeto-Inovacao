package com.vanrides.vanrides.whatsapp.commands;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Only explicit transport answers become a server-owned choice. */
final class WaAttendanceAnswers {
    private static final Pattern UNCERTAIN = Pattern.compile(".*\\b(?:talvez|acho|se|depende|possivel|pode ser|nao sei)\\b.*");
    private static final Pattern DAY = Pattern.compile("\\b(\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2})\\b");
    private static final Set<String> ENTRY = Set.of("presenca", "presencas", "minha presenca", "minhas presencas",
        "quem vai", "resumo de presenca", "resumo das presencas", "resumo presencas");
    private static final Set<String> INTERRUPT = Set.of("rotinas", "convidar", "passageiros", "menu passageiros",
        "ponto comum", "corrigir ponto", "continuar cadastro", "encerrar", "encerrar vinculo", "resumo");

    record Input(String text, String choice, boolean generic, Integer number, String day, String direction,
                 boolean multiple) { }

    private WaAttendanceAnswers() { }

    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.forLanguageTag("pt-BR")).strip().replaceAll("\\s+", " ")
            .replaceAll("[.!?]+$", "").strip();
    }

    static boolean entry(String text) {
        return ENTRY.contains(text) || text.matches("(?:presencas?|minhas? presencas?|quem vai|resumo (?:de |das )?presencas?) .+");
    }
    static boolean interrupt(String text) { return INTERRUPT.contains(text); }

    static Input parse(String raw) {
        String text = normalize(raw);
        if (text.matches("[1-9][0-9]?|100")) {
            return new Input(text, null, false, Integer.valueOf(text), null, null, false);
        }
        if (UNCERTAIN.matcher(text).matches()) {
            return new Input(text, null, false, null, null, null, false);
        }
        String direction = null;
        boolean twoDirections = false;
        if (text.matches(".*\\bida\\b.*")) direction = "OUTBOUND";
        if (text.matches(".*\\bvolta\\b.*")) {
            twoDirections = direction != null;
            direction = "RETURN";
        }
        String day = null;
        boolean twoDays = false;
        if (text.matches(".*\\bhoje\\b.*")) day = "TODAY";
        if (text.matches(".*\\bamanha\\b.*")) {
            twoDays = day != null;
            day = "TOMORROW";
        }
        var dates = DAY.matcher(text);
        while (!twoDirections && !twoDays && dates.find()) {
            if (day != null) {
                twoDays = true;
                break;
            }
            try {
                day = parseDay(dates.group(1));
            } catch (DateTimeException invalid) {
                return new Input(text, null, false, null, null, direction, false);
            }
        }
        boolean conflict = twoDirections || twoDays;
        String words = DAY.matcher(text).replaceAll("").replaceAll("\\b(?:hoje|amanha|ida|volta|dia|na|no|para|a|de)\\b", " ")
            .replaceAll("\\s+", " ").strip();
        if (conflict) words = words.replaceAll("\\be\\b", " ").replaceAll("\\s+", " ").strip();
        Integer number = null;
        var numbered = Pattern.compile("^(.*) ([1-9][0-9]?)$").matcher(words);
        if (!conflict && numbered.matches()) {
            words = numbered.group(1).strip();
            number = Integer.valueOf(numbered.group(2));
        }
        boolean generic = !conflict && Set.of("sim", "nao").contains(words);
        String choice = switch (words) {
            case "vou", "eu vou", "vou sim", "eu vou sim", "irei", "confirmo", "sim" -> "GOING";
            case "nao vou", "eu nao vou", "nao irei", "nao" -> "NOT_GOING";
            default -> null;
        };
        if (conflict) return new Input(text, choice, false, null, null, twoDirections ? null : direction, choice != null);
        return new Input(text, choice, generic, number, day, direction, false);
    }

    private static String parseDay(String supplied) {
        if (supplied.matches("\\d{2}/\\d{2}")) {
            return MonthDay.parse(supplied, DateTimeFormatter.ofPattern("dd/MM")).toString();
        }
        return supplied.contains("/")
            ? LocalDate.parse(supplied, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)).toString()
            : LocalDate.parse(supplied).toString();
    }
}
