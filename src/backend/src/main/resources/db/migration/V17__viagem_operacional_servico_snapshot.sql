ALTER TABLE viagem
    ALTER COLUMN rota_base_id DROP NOT NULL;

ALTER TABLE viagem
    ADD COLUMN servico_recorrente_id BIGINT,
    ADD COLUMN servico_nome_snapshot VARCHAR(255),
    ADD COLUMN tipo_operacao_snapshot VARCHAR(32),
    ADD COLUMN destino_final_nome_snapshot VARCHAR(255),
    ADD COLUMN destino_final_endereco_snapshot VARCHAR(512),
    ADD COLUMN destino_final_latitude_snapshot DOUBLE PRECISION,
    ADD COLUMN destino_final_longitude_snapshot DOUBLE PRECISION;

ALTER TABLE viagem
    ADD CONSTRAINT fk_viagem_servico_recorrente
        FOREIGN KEY (servico_recorrente_id) REFERENCES servico_recorrente (id);

UPDATE viagem v
SET servico_recorrente_id = sr.id,
    servico_nome_snapshot = sr.nome,
    tipo_operacao_snapshot = sr.tipo_operacao,
    destino_final_nome_snapshot = sr.destino_final_nome,
    destino_final_endereco_snapshot = sr.destino_final_endereco,
    destino_final_latitude_snapshot = sr.destino_final_latitude,
    destino_final_longitude_snapshot = sr.destino_final_longitude
FROM servico_recorrente sr
WHERE sr.rota_base_id = v.rota_base_id
  AND v.servico_recorrente_id IS NULL;

CREATE INDEX idx_viagem_servico_recorrente_data
    ON viagem (servico_recorrente_id, data_operacao);

CREATE UNIQUE INDEX uk_viagem_servico_data
    ON viagem (servico_recorrente_id, data_operacao)
    WHERE servico_recorrente_id IS NOT NULL;
