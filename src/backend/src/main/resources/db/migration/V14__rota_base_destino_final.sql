ALTER TABLE rota_base
    ADD COLUMN destino_final_nome       VARCHAR(255),
    ADD COLUMN destino_final_endereco   VARCHAR(512),
    ADD COLUMN destino_final_latitude   DOUBLE PRECISION,
    ADD COLUMN destino_final_longitude  DOUBLE PRECISION;
