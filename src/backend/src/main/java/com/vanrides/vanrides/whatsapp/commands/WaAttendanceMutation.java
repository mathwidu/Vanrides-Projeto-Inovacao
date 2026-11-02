package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Presence and activation serialize on the occurrence before either touches its snapshot. */
final class WaAttendanceMutation {
    record Result(WaDecision decision, String text) { }
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
        String label = WaAttendanceQuestions.label(current);
        if (started && choice.equals("GOING") && !included(run, actor.identityId(), current)) {
            questions.save(actor, event, WaAttendanceSelection.empty());
            return new Result(WaDecision.ok(Map.of("changed", false)),
                "A saída de " + label + " já começou. Seu pedido de ir ficou registrado nesta conversa, mas o embarque ainda precisa ser confirmado. "
                    + "Fale com o motorista ou envie FALAR COM SUPORTE para confirmar seu embarque.");
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
        if (started && choice.equals("NOT_GOING")) {
            questions.jdbc.update("""
                UPDATE wa.run_stop SET state='CANCELED',canceled_at=?,version=version+1
                WHERE run_id=? AND passenger_id=? AND state IN ('WAITING','APPROACHING')
                """, now, run, actor.identityId());
        }
        questions.save(actor, event, WaAttendanceSelection.empty());
        String response = choice.equals("GOING") ? "Você confirmou que vai em " : "Você informou que não vai em ";
        response += label + ".";
        if (started && choice.equals("NOT_GOING")) {
            response += " Os próximos avisos para você foram suspensos. A saída já começou e a alteração não confirma um desvio do motorista.";
        }
        return new Result(WaDecision.ok(Map.of("changed", changed)), response);
    }

    private boolean included(UUID run, UUID passenger, ObjectNode current) {
        if (current.path("attendanceState").asText().equals("NOT_GOING")) return false;
        return Boolean.TRUE.equals(questions.jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.run_stop WHERE run_id=? AND passenger_id=?
              AND state IN ('WAITING','APPROACHING') AND attendance_snapshot IN ('PENDING','GOING'))
            """, Boolean.class, run, passenger));
    }

    static Result expired() {
        return new Result(WaDecision.error(WaProblem.Code.INTENT_EXPIRED),
            "Essa pergunta já encerrou ou mudou. Nenhuma outra data foi alterada. Envie PRESENÇA para escolher uma ocorrência atual.");
    }
}
