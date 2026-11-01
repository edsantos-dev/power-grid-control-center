package br.com.powergrid.controlcenter.migration;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class EventosV12MigrationTest {
    @Test
    void deveNormalizarSomenteGrafiaConhecidaSemInventarDatasOuHistorico() {
        var datasource = new SingleConnectionDataSource("jdbc:h2:mem:migracao_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0", "sa", "", true);
        var jdbc = new JdbcTemplate(datasource);
        try {
            Flyway.configure().dataSource(datasource).target("11").load().migrate();
            jdbc.update("INSERT INTO ativo_rede (id, nome, estado_operacional) VALUES (1001, 'Subestação', 'OPERACIONAL')");
            jdbc.update("INSERT INTO subestacao (id) VALUES (1001)");
            Long usuario = jdbc.queryForObject("SELECT MIN(id) FROM usuario", Long.class);
            String[] estados = {" aberto ", "em_andamento", "CONCLUIDO", "IMPORTADO", null};
            var inicio = OffsetDateTime.parse("2026-01-01T10:00:00-03:00");
            for (int i = 0; i < estados.length; i++) {
                jdbc.update("INSERT INTO evento_operacional (id, ativo_id, categoria, situacao, registrador_usuario_id, responsavel_usuario_id, inicio_efetivo) VALUES (?, 1001, 'MANOBRA', ?, ?, ?, ?)",
                        2001L + i, estados[i], usuario, usuario, i == 1 ? inicio : null);
            }
            var dadosAntes = jdbc.queryForList("SELECT id, ativo_id, categoria, registrador_usuario_id, responsavel_usuario_id, inicio_previsto, termino_previsto, inicio_efetivo, termino_efetivo FROM evento_operacional ORDER BY id");

            Flyway.configure().dataSource(datasource).target("12").load().migrate();

            assertThat(jdbc.queryForList("SELECT situacao FROM evento_operacional ORDER BY id", String.class))
                    .containsExactly("ABERTO", "EM_ANDAMENTO", "CONCLUIDO", "IMPORTADO", null);
            assertThat(jdbc.queryForList("SELECT id, ativo_id, categoria, registrador_usuario_id, responsavel_usuario_id, inicio_previsto, termino_previsto, inicio_efetivo, termino_efetivo FROM evento_operacional ORDER BY id"))
                    .isEqualTo(dadosAntes);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_evento_operacional", Long.class)).isZero();
        } finally {
            datasource.destroy();
        }
    }
}
