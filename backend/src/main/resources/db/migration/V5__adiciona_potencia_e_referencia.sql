ALTER TABLE fonte_medicao ADD COLUMN subestacao_referencia_id BIGINT;
ALTER TABLE fonte_medicao
    ADD CONSTRAINT fk_fonte_subestacao_referencia
    FOREIGN KEY (subestacao_referencia_id) REFERENCES subestacao (id);

ALTER TABLE medicao_linha ADD COLUMN potencia_ativa_mw NUMERIC(19, 6);
ALTER TABLE medicao_linha ADD COLUMN potencia_reativa_mvar NUMERIC(19, 6);

ALTER TABLE medicao_linha DROP CONSTRAINT ck_medicao_grandeza;
ALTER TABLE medicao_linha
    ADD CONSTRAINT ck_medicao_grandeza CHECK (
        tensao_kv IS NOT NULL OR corrente_a IS NOT NULL
        OR potencia_ativa_mw IS NOT NULL OR potencia_reativa_mvar IS NOT NULL
    );
ALTER TABLE medicao_linha
    ADD CONSTRAINT ck_medicao_potencia_ativa_finita CHECK (
        CAST(potencia_ativa_mw AS VARCHAR) NOT IN ('NaN', 'Infinity', '-Infinity')
    );
ALTER TABLE medicao_linha
    ADD CONSTRAINT ck_medicao_potencia_reativa_finita CHECK (
        CAST(potencia_reativa_mvar AS VARCHAR) NOT IN ('NaN', 'Infinity', '-Infinity')
    );
