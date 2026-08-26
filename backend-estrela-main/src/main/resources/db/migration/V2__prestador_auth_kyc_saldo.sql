ALTER TABLE dim_prestador
    ADD COLUMN email VARCHAR(255),
    ADD COLUMN senha VARCHAR(255),
    ADD COLUMN status_kyc VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
    ADD COLUMN documento_identidade_url VARCHAR(500),
    ADD COLUMN comprovante_pix_url VARCHAR(500),
    ADD COLUMN chave_pix VARCHAR(255),
    ADD COLUMN saldo_disponivel NUMERIC(19,2) NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- email não é NOT NULL a nível de banco porque prestadores de dev pré-existentes (criados antes do
-- login/KYC existir) não têm valor; unicidade permite múltiplos NULLs no Postgres.
ALTER TABLE dim_prestador ADD CONSTRAINT uk_dim_prestador_email UNIQUE (email);
