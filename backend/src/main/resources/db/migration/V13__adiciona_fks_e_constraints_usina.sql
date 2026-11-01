-- A V1 não permite reconstruir a subestação de uma usina legada.
-- Somente cadastros inconsistentes sem referências externas podem ser removidos.
-- A coluna pode ser preenchida explicitamente antes de reaplicar a migration.
ALTER TABLE usina_geradora ADD COLUMN IF NOT EXISTS subestacao_id BIGINT;
CREATE TEMPORARY TABLE usinas_v13_sem_referencias AS
SELECT u.id
FROM usina_geradora u
WHERE (
    u.subestacao_id IS NULL
    OR NOT EXISTS (SELECT 1 FROM subestacao s WHERE s.id = u.subestacao_id)
    OR u.tipo_geracao IS NULL
    OR u.tipo_geracao NOT IN ('HIDRELETRICA', 'SOLAR', 'EOLICA', 'TERMICA')
    OR u.capacidade_maxima_mw IS NULL
    OR u.capacidade_maxima_mw <= 0
    OR CAST(u.capacidade_maxima_mw AS VARCHAR) = 'NaN'
)
  AND NOT EXISTS (SELECT 1 FROM evento_operacional e WHERE e.ativo_id = u.id)
  AND NOT EXISTS (SELECT 1 FROM alerta a WHERE a.ativo_id = u.id)
  AND NOT EXISTS (SELECT 1 FROM fonte_medicao f WHERE f.ativo_id = u.id)
  AND NOT EXISTS (SELECT 1 FROM alerta_sobrecarga a WHERE a.ativo_id = u.id)
  AND NOT EXISTS (SELECT 1 FROM alerta_tensao a WHERE a.ativo_id = u.id)
  AND NOT EXISTS (SELECT 1 FROM alerta_comunicacao a WHERE a.ativo_id = u.id)
  AND NOT EXISTS (SELECT 1 FROM subestacao s WHERE s.id = u.id)
  AND NOT EXISTS (SELECT 1 FROM alimentador a WHERE a.id = u.id)
  AND NOT EXISTS (SELECT 1 FROM transformador t WHERE t.id = u.id)
  AND NOT EXISTS (SELECT 1 FROM linha_transmissao l WHERE l.id = u.id)
  AND NOT EXISTS (SELECT 1 FROM medidor_inteligente m WHERE m.id = u.id);

DELETE FROM usina_geradora WHERE id IN (SELECT id FROM usinas_v13_sem_referencias);
DELETE FROM ativo_rede WHERE id IN (SELECT id FROM usinas_v13_sem_referencias);
DROP TABLE usinas_v13_sem_referencias;

-- Usinas referenciadas inconsistentes fazem as restrições abaixo interromperem a migration.
-- A associação deve ser regularizada explicitamente, sem valores fictícios.
ALTER TABLE usina_geradora ALTER COLUMN subestacao_id SET NOT NULL;
ALTER TABLE usina_geradora ADD CONSTRAINT fk_usina_subestacao
    FOREIGN KEY (subestacao_id) REFERENCES subestacao (id);
ALTER TABLE usina_geradora ALTER COLUMN tipo_geracao SET NOT NULL;
ALTER TABLE usina_geradora ALTER COLUMN capacidade_maxima_mw SET NOT NULL;
ALTER TABLE usina_geradora ADD CONSTRAINT ck_usina_tipo_geracao
    CHECK (tipo_geracao IN ('HIDRELETRICA', 'SOLAR', 'EOLICA', 'TERMICA'));
ALTER TABLE usina_geradora ADD CONSTRAINT ck_usina_capacidade
    CHECK (capacidade_maxima_mw > 0 AND CAST(capacidade_maxima_mw AS VARCHAR) <> 'NaN');
CREATE INDEX idx_usina_subestacao_tipo ON usina_geradora (subestacao_id, tipo_geracao);
