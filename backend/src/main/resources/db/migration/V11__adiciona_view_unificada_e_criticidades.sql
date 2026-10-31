ALTER TABLE alerta_sobrecarga ADD COLUMN criticidade VARCHAR(20) DEFAULT 'ALTA';
UPDATE alerta_sobrecarga SET criticidade = 'ALTA';
ALTER TABLE alerta_sobrecarga ALTER COLUMN criticidade SET NOT NULL;
ALTER TABLE alerta_sobrecarga ADD CONSTRAINT ck_alerta_sobrecarga_criticidade
    CHECK (criticidade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA'));

ALTER TABLE alerta_tensao ADD COLUMN criticidade VARCHAR(20) DEFAULT 'ALTA';
UPDATE alerta_tensao SET criticidade = 'ALTA';
ALTER TABLE alerta_tensao ALTER COLUMN criticidade SET NOT NULL;
ALTER TABLE alerta_tensao ADD CONSTRAINT ck_alerta_tensao_criticidade
    CHECK (criticidade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA'));

ALTER TABLE alerta_comunicacao ADD COLUMN criticidade VARCHAR(20) DEFAULT 'MEDIA';
UPDATE alerta_comunicacao SET criticidade = 'MEDIA';
ALTER TABLE alerta_comunicacao ALTER COLUMN criticidade SET NOT NULL;
ALTER TABLE alerta_comunicacao ADD CONSTRAINT ck_alerta_comunicacao_criticidade
    CHECK (criticidade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA'));

CREATE VIEW vw_alertas_dashboard AS
SELECT
    a.id AS id_origem,
    CAST('GENERICO' AS VARCHAR(20)) AS tipo_alerta,
    a.criticidade AS criticidade,
    a.escopo AS escopo,
    a.ativo_id AS ativo_id,
    CAST(NULL AS BIGINT) AS fonte_id,
    CAST(NULL AS BIGINT) AS medicao_id,
    a.gerado_em AS ocorrido_em,
    a.situacao AS status_origem,
    CAST(NULL AS VARCHAR(20)) AS subtipo,
    a.tipo AS mensagem,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS reconhecido_em,
    CAST(NULL AS BIGINT) AS reconhecido_por_usuario_id,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS restabelecido_em,
    CAST(NULL AS NUMERIC(27,6)) AS carga_percentual,
    CAST(NULL AS NUMERIC(19,6)) AS limiar_percentual,
    CAST(NULL AS NUMERIC(19,6)) AS tensao_medida_kv,
    CAST(NULL AS NUMERIC(19,6)) AS tensao_referencia_kv,
    CAST(NULL AS NUMERIC(22,8)) AS limite_inferior_kv,
    CAST(NULL AS NUMERIC(22,8)) AS limite_superior_kv,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS monitorada_desde,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS ultima_comunicacao_em,
    CAST(NULL AS INTEGER) AS tolerancia_segundos
FROM alerta a
UNION ALL
SELECT
    a.id AS id_origem,
    CAST('SOBRECARGA' AS VARCHAR(20)) AS tipo_alerta,
    a.criticidade AS criticidade,
    CAST('ATIVO' AS VARCHAR(20)) AS escopo,
    a.ativo_id AS ativo_id,
    m.fonte_id AS fonte_id,
    a.medicao_id AS medicao_id,
    a.gerado_em AS ocorrido_em,
    a.status AS status_origem,
    CAST(NULL AS VARCHAR(20)) AS subtipo,
    CAST(NULL AS VARCHAR(128)) AS mensagem,
    a.reconhecido_em AS reconhecido_em,
    a.reconhecido_por_usuario_id AS reconhecido_por_usuario_id,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS restabelecido_em,
    a.carga_percentual AS carga_percentual,
    a.limiar_percentual AS limiar_percentual,
    CAST(NULL AS NUMERIC(19,6)) AS tensao_medida_kv,
    CAST(NULL AS NUMERIC(19,6)) AS tensao_referencia_kv,
    CAST(NULL AS NUMERIC(22,8)) AS limite_inferior_kv,
    CAST(NULL AS NUMERIC(22,8)) AS limite_superior_kv,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS monitorada_desde,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS ultima_comunicacao_em,
    CAST(NULL AS INTEGER) AS tolerancia_segundos
FROM alerta_sobrecarga a
JOIN medicao_transformador m ON m.id = a.medicao_id
UNION ALL
SELECT
    a.id AS id_origem,
    CAST('TENSAO' AS VARCHAR(20)) AS tipo_alerta,
    a.criticidade AS criticidade,
    CAST('ATIVO' AS VARCHAR(20)) AS escopo,
    a.ativo_id AS ativo_id,
    m.fonte_id AS fonte_id,
    a.medicao_id AS medicao_id,
    a.gerado_em AS ocorrido_em,
    a.status AS status_origem,
    a.tipo AS subtipo,
    CAST(NULL AS VARCHAR(128)) AS mensagem,
    a.reconhecido_em AS reconhecido_em,
    a.reconhecido_por_usuario_id AS reconhecido_por_usuario_id,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS restabelecido_em,
    CAST(NULL AS NUMERIC(27,6)) AS carga_percentual,
    CAST(NULL AS NUMERIC(19,6)) AS limiar_percentual,
    a.tensao_medida_kv AS tensao_medida_kv,
    a.tensao_referencia_kv AS tensao_referencia_kv,
    a.limite_inferior_kv AS limite_inferior_kv,
    a.limite_superior_kv AS limite_superior_kv,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS monitorada_desde,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS ultima_comunicacao_em,
    CAST(NULL AS INTEGER) AS tolerancia_segundos
FROM alerta_tensao a
JOIN medicao_linha m ON m.id = a.medicao_id
UNION ALL
SELECT
    a.id AS id_origem,
    CAST('COMUNICACAO' AS VARCHAR(20)) AS tipo_alerta,
    a.criticidade AS criticidade,
    CAST('ATIVO' AS VARCHAR(20)) AS escopo,
    a.ativo_id AS ativo_id,
    a.fonte_id AS fonte_id,
    CAST(NULL AS BIGINT) AS medicao_id,
    a.detectado_em AS ocorrido_em,
    a.status AS status_origem,
    CAST(NULL AS VARCHAR(20)) AS subtipo,
    CAST(NULL AS VARCHAR(128)) AS mensagem,
    CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS reconhecido_em,
    CAST(NULL AS BIGINT) AS reconhecido_por_usuario_id,
    a.restabelecido_em AS restabelecido_em,
    CAST(NULL AS NUMERIC(27,6)) AS carga_percentual,
    CAST(NULL AS NUMERIC(19,6)) AS limiar_percentual,
    CAST(NULL AS NUMERIC(19,6)) AS tensao_medida_kv,
    CAST(NULL AS NUMERIC(19,6)) AS tensao_referencia_kv,
    CAST(NULL AS NUMERIC(22,8)) AS limite_inferior_kv,
    CAST(NULL AS NUMERIC(22,8)) AS limite_superior_kv,
    a.monitorada_desde AS monitorada_desde,
    a.ultima_comunicacao_em AS ultima_comunicacao_em,
    a.tolerancia_segundos AS tolerancia_segundos
FROM alerta_comunicacao a;
