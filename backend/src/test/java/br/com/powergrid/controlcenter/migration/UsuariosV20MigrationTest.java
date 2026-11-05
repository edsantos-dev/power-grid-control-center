package br.com.powergrid.controlcenter.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class UsuariosV20MigrationTest {
    @Test
    void bancoNovoComecaSemContasDemonstrativas() {
        var datasource = datasource();
        try {
            Flyway.configure().dataSource(datasource).load().migrate();
            var jdbc = new JdbcTemplate(datasource);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario", Long.class)).isZero();
        } finally {
            datasource.destroy();
        }
    }

    @Test
    void upgradeRevogaCredenciaisSemPerderIdentidadesNemAutoria() {
        var datasource = datasource();
        try {
            Flyway.configure().dataSource(datasource)
                    .configuration(Map.of("flyway.baselineMigrationPrefix", "LEGADO"))
                    .target("19").load().migrate();
            var jdbc = new JdbcTemplate(datasource);
            var ids = jdbc.queryForList("SELECT id FROM usuario ORDER BY id", Long.class);
            assertThat(ids).hasSize(3);
            Long gerenteReal = UsuariosMigrationFixture.criar(jdbc, "GERENTE_OPERACOES");
            jdbc.update("UPDATE usuario SET senha_hash = 'credencial-de-teste' WHERE id = ?", gerenteReal);
            jdbc.update("INSERT INTO ativo_rede (id, nome, estado_operacional) VALUES (1001, 'Ativo', 'OPERACIONAL')");
            jdbc.update("""
                    INSERT INTO evento_operacional (id, ativo_id, categoria, situacao,
                        registrador_usuario_id, responsavel_usuario_id)
                    VALUES (2001, 1001, 'MANUTENCAO', 'ABERTO', ?, ?)
                    """, ids.get(0), ids.get(1));

            Flyway.configure().dataSource(datasource).load().migrate();

            assertThat(jdbc.queryForList("SELECT id FROM usuario WHERE NOT ativo AND senha_hash IS NULL ORDER BY id", Long.class))
                    .containsExactlyElementsOf(ids);
            assertThat(jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE id = ? AND ativo", String.class, gerenteReal))
                    .isEqualTo("credencial-de-teste");
            assertThat(jdbc.queryForObject("SELECT registrador_usuario_id FROM evento_operacional WHERE id = 2001", Long.class))
                    .isEqualTo(ids.get(0));
            assertThat(jdbc.queryForObject("SELECT responsavel_usuario_id FROM evento_operacional WHERE id = 2001", Long.class))
                    .isEqualTo(ids.get(1));
        } finally {
            datasource.destroy();
        }
    }

    private SingleConnectionDataSource datasource() {
        return new SingleConnectionDataSource("jdbc:h2:mem:usuarios_v20_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "", true);
    }
}
