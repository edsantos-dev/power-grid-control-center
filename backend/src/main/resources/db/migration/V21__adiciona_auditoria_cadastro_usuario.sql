ALTER TABLE usuario ADD COLUMN criado_por_usuario_id BIGINT;
ALTER TABLE usuario ADD COLUMN criado_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE usuario ADD CONSTRAINT fk_usuario_criado_por
    FOREIGN KEY (criado_por_usuario_id) REFERENCES usuario (id);

ALTER TABLE usuario ADD CONSTRAINT ck_usuario_auditoria_cadastro
    CHECK ((criado_por_usuario_id IS NULL AND criado_em IS NULL)
        OR (criado_por_usuario_id IS NOT NULL AND criado_em IS NOT NULL));
