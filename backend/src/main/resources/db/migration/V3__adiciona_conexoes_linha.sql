ALTER TABLE linha_transmissao ADD COLUMN subestacao_origem_id BIGINT NOT NULL;
ALTER TABLE linha_transmissao ADD COLUMN subestacao_destino_id BIGINT NOT NULL;

ALTER TABLE linha_transmissao
    ADD CONSTRAINT fk_linha_subestacao_origem
    FOREIGN KEY (subestacao_origem_id) REFERENCES subestacao (id);

ALTER TABLE linha_transmissao
    ADD CONSTRAINT fk_linha_subestacao_destino
    FOREIGN KEY (subestacao_destino_id) REFERENCES subestacao (id);

ALTER TABLE linha_transmissao
    ADD CONSTRAINT ck_linha_subestacoes_distintas
    CHECK (subestacao_origem_id <> subestacao_destino_id);
