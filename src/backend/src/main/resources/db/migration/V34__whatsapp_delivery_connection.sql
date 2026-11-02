-- Additive, fail-closed delivery admission. No existing instance is backfilled OPEN.
ALTER TABLE wa.service_credential ADD CONSTRAINT uk_wa_credential_delivery_scope
    UNIQUE (id, principal, environment, allowed_instance_id);

CREATE TABLE wa.delivery_connection (
    environment TEXT NOT NULL CHECK (btrim(environment) <> ''),
    instance_id TEXT NOT NULL CHECK (btrim(instance_id) <> ''),
    state TEXT NOT NULL CHECK (state IN ('OPEN', 'SUSPENDED')),
    ordering_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    channel_credential_id UUID NOT NULL,
    channel_principal TEXT NOT NULL DEFAULT 'CHANNEL' CHECK (channel_principal = 'CHANNEL'),
    source_receipt_id UUID NOT NULL REFERENCES wa.integration_inbox(id) ON DELETE CASCADE,
    PRIMARY KEY (environment, instance_id),
    CONSTRAINT ck_delivery_connection_clock CHECK (ordering_at <= recorded_at),
    CONSTRAINT fk_delivery_connection_channel FOREIGN KEY
        (channel_credential_id, channel_principal, environment, instance_id)
        REFERENCES wa.service_credential(id, principal, environment, allowed_instance_id) ON DELETE CASCADE
);
