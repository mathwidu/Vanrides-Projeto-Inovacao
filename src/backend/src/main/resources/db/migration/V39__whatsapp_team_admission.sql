-- Team operators are GitHub users; legacy invitations still require their OPERATOR credential.
ALTER TABLE wa.driver_bootstrap_invite
    ALTER COLUMN created_by DROP NOT NULL,
    ADD COLUMN created_by_github_id BIGINT,
    ADD CONSTRAINT ck_bootstrap_author CHECK (
        (created_by IS NOT NULL AND created_by_github_id IS NULL)
        OR (created_by IS NULL AND created_by_github_id IS NOT NULL AND created_by_github_id > 0)
    );

-- One committed operation is also its audit record. No contact data or invitation code is stored here.
CREATE TABLE wa.team_admission_operation (
    id UUID PRIMARY KEY,
    github_actor_id BIGINT NOT NULL CHECK (github_actor_id > 0),
    github_login TEXT NOT NULL CHECK (github_login ~ '^[A-Za-z0-9-]{1,39}$'),
    action TEXT NOT NULL CHECK (action IN ('ADMIT_PASSENGER', 'ISSUE_DRIVER_INVITE', 'RENEW_DRIVER_INVITE')),
    request_fingerprint VARCHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    identity_id UUID NOT NULL REFERENCES wa.identity (id),
    invite_id UUID REFERENCES wa.driver_bootstrap_invite (id),
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_team_admission_invite CHECK ((action = 'ADMIT_PASSENGER') = (invite_id IS NULL))
);
CREATE INDEX ix_team_admission_actor_time ON wa.team_admission_operation (github_actor_id, created_at);
