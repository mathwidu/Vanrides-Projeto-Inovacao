-- Operational delivery cases are separate from human support and never pause a conversation.
ALTER TABLE wa.support_case ADD COLUMN purpose TEXT NOT NULL DEFAULT 'CONVERSATION'
    CHECK (purpose IN ('CONVERSATION','DELIVERY'));
ALTER TABLE wa.support_case ADD COLUMN delivery_outbox_id UUID;
ALTER TABLE wa.support_case ADD COLUMN environment TEXT;
ALTER TABLE wa.support_case ADD COLUMN instance_id TEXT;
ALTER TABLE wa.message_outbox ADD CONSTRAINT uk_outbox_delivery_recipient UNIQUE(id,recipient_identity_id);
ALTER TABLE wa.support_case ADD CONSTRAINT fk_support_delivery_recipient
    FOREIGN KEY(delivery_outbox_id,identity_id) REFERENCES wa.message_outbox(id,recipient_identity_id) ON DELETE CASCADE;
ALTER TABLE wa.support_case ADD CONSTRAINT ck_support_purpose CHECK (
    (purpose='CONVERSATION' AND delivery_outbox_id IS NULL AND environment IS NULL AND instance_id IS NULL)
    OR (purpose='DELIVERY' AND delivery_outbox_id IS NOT NULL
        AND environment IS NOT NULL AND btrim(environment)<>'' AND instance_id IS NOT NULL AND btrim(instance_id)<>''
        AND reason_code='DELIVERY_UNKNOWN_EXPIRED' AND correlation_id=delivery_outbox_id::text
        AND deduplication_key='delivery-unknown:'||delivery_outbox_id::text)
);
CREATE UNIQUE INDEX uk_support_delivery_outbox ON wa.support_case(delivery_outbox_id) WHERE purpose='DELIVERY';
CREATE INDEX ix_support_delivery_scope ON wa.support_case(environment,instance_id,id) WHERE purpose='DELIVERY';
ALTER TABLE wa.attendance_job_page DROP CONSTRAINT attendance_job_page_job_type_check;
ALTER TABLE wa.attendance_job_page ADD CONSTRAINT attendance_job_page_job_type_check
    CHECK(job_type IN ('MATERIALIZE_OCCURRENCES','DUE_REMINDERS','HEALTH_RECONCILE'));
