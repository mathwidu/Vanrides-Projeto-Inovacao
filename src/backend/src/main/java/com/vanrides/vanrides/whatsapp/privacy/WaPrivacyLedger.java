package com.vanrides.vanrides.whatsapp.privacy;

import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class WaPrivacyLedger {
    private final JdbcTemplate jdbc;
    WaPrivacyLedger(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void intent(UUID subject, Instant now) {
        jdbc.update("""
            WITH intent AS (SELECT jsonb_build_object('policy','wa-privacy-v1','eventId',?::uuid,
              'subjectId',subject_id,'environment',environment,'instanceId',instance_id,
              'closedAt',closed_at,'eraseAfter',erase_after) payload,subject_id,environment,instance_id
              FROM wa.privacy_subject WHERE subject_id=?)
            INSERT INTO wa.privacy_erasure_ledger(event_id,subject_id,environment,instance_id,payload_json,payload_hash,created_at)
            SELECT (payload->>'eventId')::uuid,subject_id,environment,instance_id,payload,
              encode(sha256(convert_to(payload::text,'UTF8')),'hex'),? FROM intent
            ON CONFLICT(subject_id) DO NOTHING
            """, UUID.randomUUID(), subject, Timestamp.from(now));
    }

    WaPrivacyJournal.Entry entry(UUID subject) {
        return jdbc.query("""
            SELECT sequence,event_id,subject_id,environment,instance_id,payload_json::text,payload_hash
            FROM wa.privacy_erasure_ledger WHERE subject_id=?
            """, (rs, row) -> new WaPrivacyJournal.Entry(rs.getLong(1), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)), subject)
            .stream().findFirst().orElseThrow(() -> new IllegalStateException("PRIVACY_INTENT_MISSING"));
    }

    boolean acknowledge(WaPrivacyJournal.Entry entry, WaPrivacyJournal.Ack ack, Instant now) {
        if (ack == null || !entry.eventId().equals(ack.eventId()) || !entry.payloadHash().equals(ack.payloadHash())
            || !WaCrypto.digest(entry.canonicalPayload()).equals(entry.payloadHash()) || ack.journalSequence() < 1
            || ack.chainDigest() == null || !ack.chainDigest().matches("[0-9a-f]{64}")) return false;
        jdbc.update("""
            UPDATE wa.privacy_erasure_ledger SET state='JOURNALED',journal_sequence=?,journal_digest=?,journaled_at=?
            WHERE event_id=? AND payload_hash=? AND state='INTENT'
            """, ack.journalSequence(), ack.chainDigest(), Timestamp.from(now), entry.eventId(), entry.payloadHash());
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.privacy_erasure_ledger WHERE event_id=? AND payload_hash=?
              AND state IN ('JOURNALED','PURGED') AND journal_sequence=? AND journal_digest=?)
            """, Boolean.class, entry.eventId(), entry.payloadHash(), ack.journalSequence(), ack.chainDigest()));
    }
}
