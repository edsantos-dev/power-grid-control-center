package br.com.powergrid.controlcenter.migration;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class DesignacaoEquipeV16MigrationTest {
    @Test
    void devePreservarVinculosLegadosInicializarVersaoZeroSemInventarHistorico() {
        var datasource = new SingleConnectionDataSource("jdbc:h2:mem:migracao_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0", "sa", "", true);
        var jdbc = new JdbcTemplate(datasource);
        try {
            Flyway.configure().dataSource(datasource).target("15").load().migrate();
            jdbc.update("INSERT INTO ativo_rede (id, nome, estado_operacional) VALUES (1001, 'Subestação', 'OPERACIONAL')");
            jdbc.update("INSERT INTO subestacao (id) VALUES (1001)");
            jdbc.update("INSERT INTO equipe_manutencao (id, nome, situacao) VALUES (1001, 'Equipe legada', 'FORA_DE_SERVICO')");
            Long usuario = jdbc.queryForObject("SELECT MIN(id) FROM usuario", Long.class);
            var inicio = OffsetDateTime.parse("2026-11-01T10:00:00.123456-03:00");
            for (int i = 0; i < 2; i++) {
                jdbc.update("INSERT INTO evento_operacional (id, ativo_id, categoria, situacao, registrador_usuario_id,"
                                + " responsavel_usuario_id, equipe_designada_id, inicio_efetivo) VALUES (?, 1001, 'MANUTENCAO', 'ABERTO', ?, ?, ?, ?)",
                        2001L + i, usuario, usuario, i == 0 ? 1001L : null, inicio);
            }
            var antes = jdbc.queryForList("SELECT * FROM evento_operacional ORDER BY id");

            Flyway.configure().dataSource(datasource).target("16").load().migrate();

            var depois = jdbc.queryForList("SELECT * FROM evento_operacional ORDER BY id");
            for (int i = 0; i < depois.size(); i++) {
                assertThat(depois.get(i).remove("versao_designacao")).isEqualTo(0);
                assertThat(depois.get(i)).isEqualTo(antes.get(i));
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historico_designacao_equipe", Long.class)).isZero();
            jdbc.update("INSERT INTO evento_operacional (id, ativo_id, categoria, situacao, registrador_usuario_id, responsavel_usuario_id)"
                    + " VALUES (2003, 1001, 'MANOBRA', 'ABERTO', ?, ?)", usuario, usuario);
            assertThat(jdbc.queryForObject("SELECT versao_designacao FROM evento_operacional WHERE id = 2003", Integer.class)).isZero();
        } finally {
            datasource.destroy();
        }
    }
}
