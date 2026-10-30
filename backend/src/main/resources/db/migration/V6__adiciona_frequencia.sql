ALTER TABLE medicao_linha ADD COLUMN frequencia_hz NUMERIC(9, 6);

ALTER TABLE medicao_linha DROP CONSTRAINT ck_medicao_grandeza;
ALTER TABLE medicao_linha
    ADD CONSTRAINT ck_medicao_grandeza CHECK (
        tensao_kv IS NOT NULL OR corrente_a IS NOT NULL
        OR potencia_ativa_mw IS NOT NULL OR potencia_reativa_mvar IS NOT NULL
        OR frequencia_hz IS NOT NULL
    );

ALTER TABLE medicao_linha
    ADD CONSTRAINT ck_frequencia_positiva CHECK (
        frequencia_hz > 0
        AND CAST(frequencia_hz AS VARCHAR) NOT IN ('NaN', 'Infinity', '-Infinity')
    );
