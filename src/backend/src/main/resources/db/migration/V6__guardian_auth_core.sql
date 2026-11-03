ALTER TABLE guardian
    ADD COLUMN senha VARCHAR(255);

CREATE INDEX idx_guardian_email ON guardian (email);
