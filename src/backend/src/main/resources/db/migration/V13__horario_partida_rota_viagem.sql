ALTER TABLE rota_base
    ADD COLUMN horario_partida TIME;

ALTER TABLE viagem
    ADD COLUMN horario_partida TIME;
