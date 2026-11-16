package com.vanrides.vanrides.whatsapp.privacy;

import java.util.UUID;

/** Port owned by the external journal adapter. No database-only acknowledgement is durable. */
public interface WaPrivacyJournal {
    record Entry(long databaseSequence, UUID eventId, UUID subjectId, String environment,
                 String instanceId, String canonicalPayload, String payloadHash) { }
    record Ack(UUID eventId, String payloadHash, long journalSequence, String chainDigest) { }

    /** Append idempotently by eventId; return only after durable content AND directory metadata. */
    Ack append(Entry entry);

    /** Re-read and verify the durable record/chain. An in-memory implementation is test evidence only. */
    boolean verify(Entry entry, Ack acknowledgement);

    static WaPrivacyJournal unavailable() {
        return new WaPrivacyJournal() {
            public Ack append(Entry entry) { throw new IllegalStateException("PRIVACY_JOURNAL_UNAVAILABLE"); }
            public boolean verify(Entry entry, Ack acknowledgement) { return false; }
        };
    }
}
