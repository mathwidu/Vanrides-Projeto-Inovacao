ALTER TABLE wa.driver_invite
    ADD COLUMN allow_first_contact BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN onboarding_identity_id UUID REFERENCES wa.identity (id),
    ADD CONSTRAINT ck_driver_invite_target_single_use CHECK (
        (NOT allow_first_contact AND onboarding_identity_id IS NULL) OR max_uses = 1
    );

CREATE INDEX ix_driver_invite_first_contact_identity ON wa.driver_invite (onboarding_identity_id)
    WHERE allow_first_contact AND onboarding_identity_id IS NOT NULL;
