package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public final class WaOnboardingCommands {
    public static final String NOTICE_VERSION = "wa-mvp-v1";
    private final JdbcTemplate jdbc;
    private final WaCrypto crypto;
    private final WaClock clock;
    public WaOnboardingCommands(JdbcTemplate jdbc, WaCrypto crypto, WaClock clock) {
        this.jdbc = jdbc; this.crypto = crypto; this.clock = clock;
    }
    public WaDecision apply(String type, ObjectNode payload, WaProfileState actor, WaInboxAccess.Context context) {
        return switch (type) {
            case "START_ONBOARDING" -> start(payload, actor);
            case "SET_PROFILE_NAME" -> name(payload, actor);
            case "ACCEPT_NOTICE" -> notice(payload, actor);
            case "CLAIM_DRIVER_INVITE" -> claim(payload, actor, context);
            default -> throw WaProblem.denied();
        };
    }
    private WaDecision changed(boolean changed) { return WaDecision.ok(Map.of("changed", changed)); }
    private WaDecision start(ObjectNode payload, WaProfileState actor) {
        WaJson.fields(payload);
        if (!"UNREGISTERED".equals(actor.role())) return changed(false);
        jdbc.update("INSERT INTO wa.profile(identity_id,role,status,version,created_at,updated_at) VALUES (?,'PASSENGER','ONBOARDING',1,?,?)",
            actor.identityId(), Timestamp.from(clock.now()), Timestamp.from(clock.now()));
        return changed(true);
    }
    private WaDecision name(ObjectNode payload, WaProfileState actor) {
        WaJson.fields(payload, "name"); String name = WaJson.text(payload, "name", 1, 80);
        if ("UNREGISTERED".equals(actor.role())) return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        if (name.equals(actor.name())) return changed(false);
        jdbc.update("UPDATE wa.profile SET display_name=?,version=version+1,updated_at=? WHERE identity_id=?", name,
            Timestamp.from(clock.now()), actor.identityId());
        return changed(true);
    }
    private WaDecision notice(ObjectNode payload, WaProfileState actor) {
        WaJson.fields(payload, "noticeVersion"); String notice = WaJson.text(payload, "noticeVersion", 1, 40);
        if (actor.name() == null || !NOTICE_VERSION.equals(notice)) return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        if (notice.equals(actor.noticeVersion())) return changed(false);
        jdbc.update("UPDATE wa.profile SET notice_version=?,notice_accepted_at=?,status='ACTIVE',version=version+1,updated_at=? WHERE identity_id=?",
            notice, Timestamp.from(clock.now()), Timestamp.from(clock.now()), actor.identityId());
        return changed(true);
    }
    private WaDecision claim(ObjectNode payload, WaProfileState actor, WaInboxAccess.Context context) {
        String hash = WaCommandPayloads.codeHash(payload, context, "inviteCode", "driverInviteCodeHash", "driver-bootstrap", crypto);
        if (hash == null) return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id,consumed_at,expires_at FROM wa.driver_bootstrap_invite
            WHERE identity_id=? AND code_hash=? AND revoked_at IS NULL AND expires_at>? FOR UPDATE
            """, actor.identityId(), hash, Timestamp.from(clock.now()));
        if (rows.size() != 1) return WaDecision.error(WaProblem.Code.INVALID_CREDENTIAL);
        if (!((Timestamp) rows.get(0).get("expires_at")).toInstant().isAfter(clock.now()))
            return WaDecision.error(WaProblem.Code.INTENT_EXPIRED);
        if (rows.get(0).get("consumed_at") != null) return WaDecision.error(WaProblem.Code.INTENT_EXPIRED);
        if (!"ONBOARDING".equals(actor.status())) return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        if ("UNREGISTERED".equals(actor.role())) {
            jdbc.update("INSERT INTO wa.profile(identity_id,role,status,driver_enabled,version,created_at,updated_at) VALUES (?,'DRIVER','ONBOARDING',true,1,?,?)",
                actor.identityId(), Timestamp.from(clock.now()), Timestamp.from(clock.now()));
        } else {
            jdbc.update("UPDATE wa.profile SET role='DRIVER',driver_enabled=true,version=version+1,updated_at=? WHERE identity_id=?",
                Timestamp.from(clock.now()), actor.identityId());
        }
        jdbc.update("INSERT INTO wa.driver_authorization(driver_id,generation) VALUES (?,0) ON CONFLICT DO NOTHING", actor.identityId());
        jdbc.update("UPDATE wa.driver_bootstrap_invite SET consumed_at=? WHERE id=?", Timestamp.from(clock.now()), rows.get(0).get("id"));
        return changed(true);
    }
}
