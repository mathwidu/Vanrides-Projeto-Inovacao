package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceLabels;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Presence and activation serialize on the occurrence before either touches its snapshot. */
final class WaAttendanceMutation {
    record Result(WaDecision decision, String text) { }
    static final String TRIP_ENDED = "Essa viagem já foi encerrada. Não é possível alterar a presença dela.";
    private static final Set<String> STARTED = Set.of("STARTING", "START_FAILED", "TRACKING", "DEGRADED");
    private final WaAttendanceQuestions questions;

    WaAttendanceMutation(WaAttendanceQuestions questions) { this.questions = questions; }

    Result apply(WaProfileState actor, WaInboxAccess.Context event, UUID questionId, String choice) {
        String instance = questions.instance(event);
        List<UUID> occurrences = questions.jdbc.query("""
            SELECT occurrence_id FROM wa.attendance_question
            WHERE id=? AND passenger_id=? AND instance_id=? AND environment=?
            """, (rs, row) -> rs.getObject(1, UUID.class), questionId, actor.identityId(), instance, questions.settings.environment());
        if (occurrences.size() != 1) throw WaProblem.denied();
        questions.jdbc.queryForList("SELECT id FROM wa.routine_occurrence WHERE id=? FOR UPDATE", occurrences.get(0));
        ObjectNode current = questions.own(actor.identityId(), instance, questionId);
        if (current == null) return expired();
        UUID attendance = WaAttendanceQuestions.id(current, "attendance_id");
        questions.jdbc.queryForList("SELECT id FROM wa.attendance WHERE id=? FOR UPDATE", attendance);
        questions.jdbc.queryForList("SELECT id FROM wa.attendance_question WHERE id=? FOR UPDATE", questionId);
        current = questions.own(actor.identityId(), instance, questionId);
        if (current == null) return expired();
        var runs = questions.jdbc.queryForList("SELECT id,state FROM wa.operation_run WHERE occurrence_id=? FOR UPDATE", occurrences.get(0));
        boolean started = !runs.isEmpty() && STARTED.contains(runs.get(0).get("state"));
        UUID run = runs.isEmpty() ? null : (UUID) runs.get(0).get("id");
        String bullet = "\n• " + WaAttendanceLabels.capitalize(WaAttendanceQuestions.label(current, questions.clock.now()));
        var stops = started ? questions.jdbc.queryForList("""
            SELECT state,attendance_snapshot FROM wa.run_stop WHERE run_id=? AND passenger_id=? FOR UPDATE
            """, run, actor.identityId()) : List.<Map<String, Object>>of();
        String stopState = stops.isEmpty() ? "" : (String) stops.get(0).get("state");
        String snapshot = stops.isEmpty() ? "" : (String) stops.get(0).get("attendance_snapshot");
        boolean passed = Set.of("PASSED", "SKIPPED").contains(stopState);
        boolean included = Set.of("WAITING", "APPROACHING").contains(stopState) && Set.of("PENDING", "GOING").contains(snapshot)
            && !current.path("attendanceState").asText().equals("NOT_GOING");
        if (passed) {
            return unchanged(actor, event, "A van já passou pelo seu ponto." + bullet
                + "\nA presença desta viagem não pode mais ser alterada.");
        }
        if (started && choice.equals("GOING") && !included) {
            return unchanged(actor, event, "O motorista já saiu e você não está na lista desta viagem." + bullet
                + "\nPara embarcar, fale diretamente com o motorista.");
        }
        Timestamp now = Timestamp.from(questions.clock.now());
        boolean changed = !choice.equals(current.path("attendanceState").asText());
        if (changed || current.path("state").asText().equals("OPEN")) {
            questions.jdbc.update("""
                UPDATE wa.attendance SET state=?,actor_identity_id=?,credential_id=NULL,question_ref=?,answered_at=?,version=version+1
                WHERE id=? AND passenger_id=?
                """, choice, actor.identityId(), questionId, now, attendance, actor.identityId());
            questions.jdbc.update("UPDATE wa.attendance_question SET state='ANSWERED',answered_at=?,version=version+1 WHERE id=?",
                now, questionId);
        }
        int canceled = started && choice.equals("NOT_GOING") ? questions.jdbc.update("""
            UPDATE wa.run_stop SET state='CANCELED',canceled_at=?,version=version+1
            WHERE run_id=? AND passenger_id=? AND state IN ('WAITING','APPROACHING')
            """, now, run, actor.identityId()) : 0;
        questions.save(actor, event, WaAttendanceQuestions.empty());
        String text;
        if (!changed) text = (choice.equals("GOING") ? "Sua presença já estava confirmada" : "Sua ausência já estava registrada") + bullet;
        else if (choice.equals("GOING")) text = "Presença confirmada" + bullet;
        else if (canceled > 0) text = "Ausência registrada" + bullet + "\nO motorista já saiu e sua parada foi retirada desta viagem.";
        else if (started) text = "Ausência registrada" + bullet;
        else text = "Ausência registrada" + bullet + "\nMudou de ideia? Responda VOU até a saída do motorista.";
        return new Result(WaDecision.ok(Map.of("changed", changed)), text);
    }

    private Result unchanged(WaProfileState actor, WaInboxAccess.Context event, String text) {
        questions.save(actor, event, WaAttendanceQuestions.empty());
        return new Result(WaDecision.ok(Map.of("changed", false)), text);
    }

    static Result expired() {
        return new Result(WaDecision.error(WaProblem.Code.INTENT_EXPIRED), TRIP_ENDED);
    }
}
