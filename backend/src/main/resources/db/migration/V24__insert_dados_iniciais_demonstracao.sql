-- IDs gerados pelas identidades mantêm as sequences alinhadas.
-- A linha exige duas subestações distintas e compartilha a origem com o alimentador.
INSERT INTO ativo_rede (nome, latitude, longitude, estado_operacional, data_instalacao)
VALUES ('DEMO-V24-SUBESTACAO-ORIGEM', -23.5505000, -46.6333000, 'OPERACIONAL', DATE '2026-01-01');

INSERT INTO subestacao (id, tensao_nominal_kv, capacidade_mva)
SELECT id, 138.000000, 100.000000
FROM ativo_rede WHERE nome = 'DEMO-V24-SUBESTACAO-ORIGEM';

INSERT INTO ativo_rede (nome, latitude, longitude, estado_operacional, data_instalacao)
VALUES ('DEMO-V24-SUBESTACAO-DESTINO', -23.7000000, -46.7000000, 'OPERACIONAL', DATE '2026-01-01');

INSERT INTO subestacao (id, tensao_nominal_kv, capacidade_mva)
SELECT id, 138.000000, 100.000000
FROM ativo_rede WHERE nome = 'DEMO-V24-SUBESTACAO-DESTINO';

INSERT INTO ativo_rede (nome, latitude, longitude, estado_operacional, data_instalacao)
VALUES ('DEMO-V24-ALIMENTADOR', -23.5505000, -46.6333000, 'OPERACIONAL', DATE '2026-01-01');

INSERT INTO alimentador (id, tensao_nominal_kv, subestacao_id)
SELECT alimentador.id, 13.800000, origem.id
FROM ativo_rede alimentador
JOIN ativo_rede origem ON origem.nome = 'DEMO-V24-SUBESTACAO-ORIGEM'
WHERE alimentador.nome = 'DEMO-V24-ALIMENTADOR';

INSERT INTO ativo_rede (nome, latitude, longitude, estado_operacional, data_instalacao)
VALUES ('DEMO-V24-LINHA', NULL, NULL, 'OPERACIONAL', DATE '2026-01-01');

INSERT INTO linha_transmissao
    (id, extensao_km, tensao_operacao_kv, subestacao_origem_id, subestacao_destino_id)
SELECT linha.id, 20.000000, 138.000000, origem.id, destino.id
FROM ativo_rede linha
JOIN ativo_rede origem ON origem.nome = 'DEMO-V24-SUBESTACAO-ORIGEM'
JOIN ativo_rede destino ON destino.nome = 'DEMO-V24-SUBESTACAO-DESTINO'
WHERE linha.nome = 'DEMO-V24-LINHA';

INSERT INTO ativo_rede (nome, latitude, longitude, estado_operacional, data_instalacao)
VALUES ('DEMO-V24-TRANSFORMADOR', -23.5510000, -46.6340000, 'OPERACIONAL', DATE '2026-01-01');

INSERT INTO transformador (id, capacidade_mva, alimentador_id)
SELECT transformador.id, 10.000000, alimentador.id
FROM ativo_rede transformador
JOIN ativo_rede alimentador ON alimentador.nome = 'DEMO-V24-ALIMENTADOR'
WHERE transformador.nome = 'DEMO-V24-TRANSFORMADOR';

INSERT INTO fonte_medicao
    (identificador, ativo_id, origem, subestacao_referencia_id, monitorada_desde, ultima_comunicacao_em)
SELECT 'DEMO-V24-FONTE-LINHA', linha.id, 'SIMULADA', origem.id, CURRENT_TIMESTAMP, NULL
FROM ativo_rede linha
JOIN ativo_rede origem ON origem.nome = 'DEMO-V24-SUBESTACAO-ORIGEM'
WHERE linha.nome = 'DEMO-V24-LINHA';

INSERT INTO fonte_medicao
    (identificador, ativo_id, origem, subestacao_referencia_id, monitorada_desde, ultima_comunicacao_em)
SELECT 'DEMO-V24-FONTE-TRANSFORMADOR', id, 'SIMULADA', NULL, CURRENT_TIMESTAMP, NULL
FROM ativo_rede WHERE nome = 'DEMO-V24-TRANSFORMADOR';
