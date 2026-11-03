-- Driver setup is additive. Applied foundation/transport migrations remain unchanged.
ALTER TABLE wa.routine ADD COLUMN destination_source_event_id UUID;
ALTER TABLE wa.routine ADD CONSTRAINT fk_routine_destination_actor
    FOREIGN KEY (destination_source_event_id,driver_id) REFERENCES wa.integration_inbox(id,identity_id);
ALTER TABLE wa.routine ADD COLUMN stop_order_pending BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE wa.message_outbox ADD COLUMN private_invite_id UUID REFERENCES wa.driver_invite(id);
ALTER TABLE wa.message_outbox ADD COLUMN private_payload_ciphertext BYTEA;
ALTER TABLE wa.message_outbox ADD CONSTRAINT ck_outbox_private_invite CHECK (
    private_payload_ciphertext IS NULL OR (private_invite_id IS NOT NULL AND octet_length(private_payload_ciphertext)>28
        AND policy_version='driver-setup-v1' AND payload_kind='TEXT' AND purpose='RESPONSE')
);
