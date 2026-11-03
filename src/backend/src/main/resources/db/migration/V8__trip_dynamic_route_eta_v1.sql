ALTER TABLE viagem
    ADD COLUMN rota_versao INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN eta_base_minutos INTEGER,
    ADD COLUMN eta_atualizado_em TIMESTAMP;

ALTER TABLE viagem_parada
    ADD COLUMN eta_minutos INTEGER;

UPDATE viagem
SET rota_versao = 1
WHERE rota_versao IS NULL;
