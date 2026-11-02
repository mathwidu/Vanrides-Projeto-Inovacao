-- Sliding-window admission uses every durable authorization, including failed/uncertain/no-POST facts.
CREATE INDEX ix_attempt_delivery_quota ON wa.message_attempt(instance_id,started_at DESC,id DESC)
    INCLUDE(outbox_id,credential_id);
