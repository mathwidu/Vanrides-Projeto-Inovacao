-- One authenticated request may offer several trips; each trip keeps its own token and review.
CREATE TABLE wa.start_offer (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL,
    device_id UUID NOT NULL,
    environment TEXT NOT NULL CHECK (btrim(environment)<>''),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at>created_at),
    UNIQUE (id,driver_id,device_id,environment),
    FOREIGN KEY (device_id,driver_id) REFERENCES wa.driver_device(id,driver_id)
);

ALTER TABLE wa.message_outbox ADD CONSTRAINT uq_start_offer_outbox_owner
    UNIQUE (id,recipient_identity_id,command_id);
ALTER TABLE wa.start_intent ADD COLUMN offer_id UUID;
ALTER TABLE wa.start_intent ADD COLUMN offer_position SMALLINT;
ALTER TABLE wa.start_intent ADD COLUMN delivery_outbox_id UUID;
ALTER TABLE wa.start_intent ADD CONSTRAINT fk_start_offer_owner
    FOREIGN KEY (offer_id,driver_id,device_id,environment)
    REFERENCES wa.start_offer(id,driver_id,device_id,environment);
ALTER TABLE wa.start_intent ADD CONSTRAINT fk_start_offer_delivery
    FOREIGN KEY (delivery_outbox_id,driver_id,request_command_id)
    REFERENCES wa.message_outbox(id,recipient_identity_id,command_id);
ALTER TABLE wa.start_intent ADD CONSTRAINT ck_start_offer_option CHECK (
    (offer_id IS NULL AND offer_position IS NULL AND delivery_outbox_id IS NULL)
    OR (offer_id IS NOT NULL AND offer_position IS NOT NULL AND offer_position BETWEEN 1 AND 30 AND device_id IS NOT NULL
        AND intent_action='START' AND (delivery_outbox_id IS NULL OR request_command_id IS NOT NULL))
);
DROP INDEX wa.uk_start_pending_driver;
CREATE UNIQUE INDEX uk_start_pending_driver ON wa.start_intent(driver_id)
    WHERE state='PENDING' AND offer_id IS NULL;
CREATE UNIQUE INDEX uk_start_offer_position ON wa.start_intent(offer_id,offer_position) WHERE offer_id IS NOT NULL;
CREATE UNIQUE INDEX uk_start_offer_occurrence ON wa.start_intent(offer_id,occurrence_id) WHERE offer_id IS NOT NULL;
CREATE UNIQUE INDEX uk_start_offer_activated ON wa.start_intent(offer_id) WHERE offer_id IS NOT NULL AND state='ACTIVATED';
CREATE INDEX ix_start_offer_delivery ON wa.start_intent(delivery_outbox_id) WHERE delivery_outbox_id IS NOT NULL;
