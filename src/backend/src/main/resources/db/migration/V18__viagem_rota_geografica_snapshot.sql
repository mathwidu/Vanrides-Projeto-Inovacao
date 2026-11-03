ALTER TABLE viagem
    ADD COLUMN rota_geografica_provider_snapshot VARCHAR(64),
    ADD COLUMN rota_geografica_polyline_snapshot TEXT,
    ADD COLUMN rota_geografica_navegacao_url_snapshot TEXT,
    ADD COLUMN rota_geografica_calculada_em TIMESTAMP;

CREATE INDEX idx_viagem_rota_geografica_calculada_em
    ON viagem (rota_geografica_calculada_em);
