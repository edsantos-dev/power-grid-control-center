ALTER TABLE medicao_linha ADD COLUMN chave_idempotencia UUID;
ALTER TABLE medicao_linha ADD COLUMN hash_payload VARCHAR(64);

ALTER TABLE medicao_linha ADD CONSTRAINT uq_medicao_linha_fonte_chave UNIQUE (fonte_id, chave_idempotencia);
ALTER TABLE medicao_linha ADD CONSTRAINT ck_medicao_linha_idempotencia
    CHECK ((chave_idempotencia IS NULL AND hash_payload IS NULL)
        OR (chave_idempotencia IS NOT NULL AND hash_payload IS NOT NULL));
