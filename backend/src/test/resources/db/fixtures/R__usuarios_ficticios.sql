INSERT INTO usuario (nome, matricula, email, senha_hash, perfil)
VALUES
    ('Operador Inicial', 'INI-001', 'operador@powergrid.local',
     '$2a$10$k6z7p0GvYY1O3wZLU8Es0e8W8sbBLjkvSNNCbmlM2DWClK88LpnXW', 'OPERADOR'),
    ('Gerente Inicial', 'INI-002', 'gerente@powergrid.local',
     '$2a$10$CN6VaJ.ceZe9DM9MoEXrm.qg.vwijwvXdJ3vBXPyij1c3K6r2W92K', 'GERENTE_OPERACOES'),
    ('Técnico Inicial', 'INI-003', 'tecnico@powergrid.local',
     '$2a$10$tO2x5QWSA0XR8GkaKONLT.rtRREtoLctnxQVpj8w5vjFqXoMaQUuO', 'TECNICO_MANUTENCAO');
