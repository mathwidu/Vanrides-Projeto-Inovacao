ALTER TABLE passageiro ADD COLUMN IF NOT EXISTS data_nascimento DATE;
ALTER TABLE passageiro ADD COLUMN IF NOT EXISTS foto_perfil_url TEXT;

ALTER TABLE motorista ADD COLUMN IF NOT EXISTS data_nascimento DATE;
ALTER TABLE motorista ADD COLUMN IF NOT EXISTS foto_perfil_url TEXT;

ALTER TABLE guardian ADD COLUMN IF NOT EXISTS data_nascimento DATE;
ALTER TABLE guardian ADD COLUMN IF NOT EXISTS foto_perfil_url TEXT;

ALTER TABLE company_admin ADD COLUMN IF NOT EXISTS data_nascimento DATE;
ALTER TABLE company_admin ADD COLUMN IF NOT EXISTS foto_perfil_url TEXT;
