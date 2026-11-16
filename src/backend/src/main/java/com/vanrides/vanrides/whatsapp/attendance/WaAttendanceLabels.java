package com.vanrides.vanrides.whatsapp.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class WaAttendanceLabels {
    public static final List<String> DAY_NAMES =
            List.of("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo");
    private static final DateTimeFormatter DAY_AND_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HOUR_AND_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private WaAttendanceLabels() {}

    public static String date(LocalDate date) {
        return DAY_NAMES.get(date.getDayOfWeek().getValue() - 1) + " (" + date.format(DAY_AND_MONTH) + ")";
    }

    public static String day(LocalDate date, Instant now, String timezone) {
        LocalDate today = now.atZone(ZoneId.of(timezone)).toLocalDate();
        String named = date(date);
        if (date.equals(today)) {
            return "hoje, " + named;
        }
        if (date.equals(today.plusDays(1))) {
            return "amanhã, " + named;
        }
        return named;
    }

    public static String trip(String direction, LocalTime time, String routineName) {
        return (direction.equals("OUTBOUND") ? "ida" : "volta") + " às " + time.format(HOUR_AND_MINUTE)
                + " (" + routineName + ")";
    }

    public static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
