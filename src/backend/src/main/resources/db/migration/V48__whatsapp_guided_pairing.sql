CREATE TABLE wa.pairing_handoff (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES wa.driver_authorization(driver_id),
    environment TEXT NOT NULL,
    instance_id TEXT NOT NULL,
    source_inbox_id UUID NOT NULL REFERENCES wa.integration_inbox(id) ON DELETE CASCADE,
    source_command_id UUID NOT NULL REFERENCES wa.command_receipt(command_id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    generation BIGINT NOT NULL CHECK (generation >= 0),
    state TEXT NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN','CLAIMED','APPROVED','REVOKED')),
    pairing_id UUID UNIQUE REFERENCES wa.pairing_challenge(id),
    claim_key VARCHAR(128),
    claim_hash VARCHAR(64),
    review_outbox_id UUID REFERENCES wa.message_outbox(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    CHECK (expires_at > created_at),
    CHECK ((pairing_id IS NULL) = (claim_hash IS NULL)),
    CHECK (state NOT IN ('CLAIMED','APPROVED') OR pairing_id IS NOT NULL)
);
CREATE UNIQUE INDEX uk_pairing_handoff_current ON wa.pairing_handoff(driver_id,environment)
    WHERE state IN ('OPEN','CLAIMED','APPROVED');

CREATE TABLE wa.pairing_link_notice (
    outbox_id UUID PRIMARY KEY REFERENCES wa.message_outbox(id) ON DELETE CASCADE,
    link_id UUID NOT NULL REFERENCES wa.pairing_handoff(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('ISSUE','REVIEW','RETURN')),
    secret_ciphertext BYTEA,
    CHECK (kind='ISSUE' OR secret_ciphertext IS NULL)
);
CREATE UNIQUE INDEX uk_pairing_link_initial_notice ON wa.pairing_link_notice(link_id,kind)
    WHERE kind IN ('ISSUE','REVIEW');

-- Both direct erasure and replay of the privacy journal update this column.
CREATE FUNCTION wa.revoke_guided_pairing_for_erased_profile() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE wa.pairing_challenge SET state='REVOKED' WHERE state IN ('PENDING','APPROVED')
        AND id IN (SELECT pairing_id FROM wa.pairing_handoff WHERE driver_id=NEW.identity_id);
    UPDATE wa.pairing_link_notice n SET secret_ciphertext=NULL FROM wa.pairing_handoff h
        WHERE n.link_id=h.id AND h.driver_id=NEW.identity_id;
    UPDATE wa.pairing_handoff SET state='REVOKED' WHERE driver_id=NEW.identity_id AND state<>'REVOKED';
    RETURN NEW;
END $$;
CREATE TRIGGER revoke_guided_pairing_after_erasure AFTER UPDATE OF privacy_erased_at ON wa.profile
    FOR EACH ROW WHEN (NEW.privacy_erased_at IS NOT NULL)
    EXECUTE FUNCTION wa.revoke_guided_pairing_for_erased_profile();

-- A source removed by retention/reset must never leave an approved challenge
-- that could later be redeemed as a legacy request without its handoff fences.
CREATE FUNCTION wa.cleanup_removed_pairing_handoff() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE wa.pairing_challenge SET state='REVOKED' WHERE id=OLD.pairing_id AND state IN ('PENDING','APPROVED');
    DELETE FROM wa.pairing_challenge WHERE id=OLD.pairing_id AND driver_id IS NULL AND device_id IS NULL
        AND state IN ('REVOKED','EXPIRED');
    RETURN OLD;
END $$;
CREATE TRIGGER cleanup_removed_pairing_handoff AFTER DELETE ON wa.pairing_handoff
    FOR EACH ROW EXECUTE FUNCTION wa.cleanup_removed_pairing_handoff();
