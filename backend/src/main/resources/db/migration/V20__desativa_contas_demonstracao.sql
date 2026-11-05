ALTER TABLE usuario ADD COLUMN ativo BOOLEAN DEFAULT TRUE NOT NULL;

-- Preserva as identidades e as referências históricas criadas antes desta migração.
UPDATE usuario
SET ativo = FALSE, senha_hash = NULL
WHERE (matricula = 'INI-001' AND LOWER(TRIM(email)) = 'operador@powergrid.local')
   OR (matricula = 'INI-002' AND LOWER(TRIM(email)) = 'gerente@powergrid.local')
   OR (matricula = 'INI-003' AND LOWER(TRIM(email)) = 'tecnico@powergrid.local');
