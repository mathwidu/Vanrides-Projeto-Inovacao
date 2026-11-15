package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.commands.WaConversationResponses;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fixed acknowledgements, committed with the command receipt; no model-selected destination. */
public final class WaFoundationResponses {
    public static final String POLICY = "foundation-v1";
    public static final String ELIGIBLE = """
        ((r.identity_id=o.recipient_identity_id AND ((o.policy_version='foundation-v1' AND o.response_part='foundation'
          AND r.command_type IN ('START_ONBOARDING','SET_PROFILE_NAME','ACCEPT_NOTICE','CLAIM_DRIVER_INVITE','CONFIRM_PAIRING'))
        OR (o.policy_version='conversation-v1' AND o.response_part='conversation'
          AND r.command_type IN ('START_ONBOARDING','SET_PROFILE_NAME','ACCEPT_NOTICE','CLAIM_DRIVER_INVITE','CONFIRM_PAIRING','RESPOND_TO_MESSAGE','REQUEST_SUPPORT'))
        OR (o.policy_version='driver-setup-v1' AND o.response_part='driver-setup'
          AND r.command_type IN ('ADVANCE_DRIVER_SETUP','CONFIGURE_ROUTINE','SET_STOP_ORDER','SET_ROUTINE_STATE','CREATE_INVITE','REVOKE_INVITE','CANCEL_OCCURRENCE','GET_SUMMARY'))
        OR (o.policy_version='attendance-v1' AND o.response_part='attendance'
          AND r.command_type IN ('ADVANCE_ATTENDANCE','SET_ATTENDANCE','GET_SUMMARY'))
        OR (o.policy_version='run-start-v1' AND o.response_part='run-start'
          AND r.command_type IN ('ADVANCE_RUN_SETUP','REQUEST_START','REQUEST_RESUME','SET_OPERATIONAL_ATTENDANCE'))))
        OR
        """ + WaPassengerResponses.ELIGIBLE + ")";
    private WaFoundationResponses() { }
    public static void save(JdbcTemplate jdbc, UUID id, UUID command, UUID actor, String type, Instant now) {
        save(jdbc, id, command, actor, text(type), POLICY, "foundation", now);
    }
    public static String text(String type) {
        return switch (type) {
            case "START_ONBOARDING" -> "Oi! Sou o VanRides e ajudo a organizar o transporte de van pelo WhatsApp. "
                + "Como você prefere ser chamado? Se já recebeu um convite de passageiro ou de motorista, pode enviá-lo aqui agora. "
                + "Para ver como funciona, envie AJUDA.";
            case "SET_PROFILE_NAME" -> "Pronto, salvei seu nome.";
            case "ACCEPT_NOTICE" -> "Pronto, aceite registrado.";
            case "CLAIM_DRIVER_INVITE" -> "Convite aceito! Sou o VanRides. Vou ajudar você a organizar os embarques pelo WhatsApp. "
                + "Primeiro, vamos concluir seu cadastro. Depois, você monta os dias e horários da van e convida os passageiros.";
            case "CONFIRM_PAIRING" -> "Aparelho vinculado. Já dá para voltar ao aplicativo VanRides Motorista.";
            default -> throw new IllegalStateException("WA_RESPONSE_NOT_IMPLEMENTED");
        };
    }
    public static void saveConversation(JdbcTemplate jdbc, UUID id, UUID command, UUID actor, String text, Instant now) {
        save(jdbc, id, command, actor, text, WaConversationResponses.POLICY, "conversation", now);
    }
    public static void saveDriver(JdbcTemplate jdbc, UUID id, UUID command, UUID actor, String text, Instant now) {
        save(jdbc, id, command, actor, text, "driver-setup-v1", "driver-setup", now);
    }
    public static void saveAttendance(JdbcTemplate jdbc, UUID id, UUID command, UUID actor, String text, Instant now) {
        save(jdbc, id, command, actor, text, "attendance-v1", "attendance", now);
    }
    public static void saveRun(JdbcTemplate jdbc, UUID id, UUID command, UUID actor, String text, Instant now) {
        save(jdbc, id, command, actor, text, "run-start-v1", "run-start", now);
    }
    private static void save(JdbcTemplate jdbc, UUID id, UUID command, UUID actor, String text, String policy, String part, Instant now) {
        jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,command_id,response_part,policy_version,
              payload_kind,payload_json,not_before,expires_at,created_at)
            VALUES (?,?,'RESPONSE',?,?,?,'TEXT',?::jsonb,?,?,?)
            """, id, actor, command, part, policy,
            WaJson.write(Map.of("type", "TEXT", "text", text, "referenceTime", now.toString())),
            Timestamp.from(now), Timestamp.from(now.plusSeconds(300)), Timestamp.from(now));
    }
}
