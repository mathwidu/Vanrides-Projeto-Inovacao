ALTER TABLE refresh_token
    ADD COLUMN company_id BIGINT;

UPDATE refresh_token rt
SET company_id = COALESCE(
    CASE
        WHEN rt.user_role = 'PASSAGEIRO' THEN (
            SELECT p.company_id
            FROM passageiro p
            WHERE p.id = rt.user_id
        )
        WHEN rt.user_role = 'MOTORISTA' THEN (
            SELECT m.company_id
            FROM motorista m
            WHERE m.id = rt.user_id
        )
        ELSE NULL
    END,
    (SELECT id FROM company WHERE slug = 'default')
)
WHERE rt.company_id IS NULL;

ALTER TABLE refresh_token
    ALTER COLUMN company_id SET NOT NULL;

ALTER TABLE refresh_token
    ADD CONSTRAINT fk_refresh_token_company
        FOREIGN KEY (company_id) REFERENCES company (id);

CREATE INDEX idx_refresh_token_company_id ON refresh_token (company_id);
