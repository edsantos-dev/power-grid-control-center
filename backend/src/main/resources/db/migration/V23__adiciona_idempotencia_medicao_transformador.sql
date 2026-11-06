ALTER TABLE medicao_transformador ADD COLUMN chave_idempotencia UUID;
ALTER TABLE medicao_transformador ADD COLUMN hash_payload VARCHAR(64);

ALTER TABLE medicao_transformador ADD CONSTRAINT uq_medicao_transformador_fonte_chave UNIQUE (fonte_id, chave_idempotencia);
ALTER TABLE medicao_transformador ADD CONSTRAINT ck_medicao_transformador_idempotencia
    CHECK ((chave_idempotencia IS NULL AND hash_payload IS NULL)
        OR (chave_idempotencia IS NOT NULL AND hash_payload IS NOT NULL));
