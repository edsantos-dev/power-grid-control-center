package br.com.powergrid.controlcenter.migration;

import java.util.UUID;
import br.com.powergrid.controlcenter.support.AlertasDashboardFixture;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class TratativaAlertaV18MigrationTest {
    @Test
    void deveProjetarAuditoriaGenericaSemDuplicarOuModificarOutrasOrigens() {
        var datasource = new SingleConnectionDataSource("jdbc:h2:mem:migracao_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0", "sa", "", true);
        var jdbc = new JdbcTemplate(datasource);
        try {
            Flyway.configure().dataSource(datasource).target("17").load().migrate();
            AlertasDashboardFixture.inserir(jdbc);
            jdbc.update("INSERT INTO alerta (id, tipo, situacao, escopo) VALUES (1302, 'Legado', 'RECONHECIDO', 'REDE_MONITORADA')");
            Long operador = jdbc.queryForObject("SELECT id FROM usuario WHERE perfil = 'OPERADOR'", Long.class);
            Long gerente = jdbc.queryForObject("SELECT id FROM usuario WHERE perfil = 'GERENTE_OPERACOES'", Long.class);
            var reconhecimento = AlertasDashboardFixture.INSTANTE.plusMinutes(2);
            var resolucao = AlertasDashboardFixture.INSTANTE.plusMinutes(3);
            jdbc.update("INSERT INTO historico_alerta (alerta_id, usuario_id, situacao_anterior, situacao_nova, alterado_em, comentario)"
                    + " VALUES (1301, ?, 'ABERTO', 'RECONHECIDO', ?, 'Reconhecido')", operador, reconhecimento);
            jdbc.update("INSERT INTO historico_alerta (alerta_id, usuario_id, situacao_anterior, situacao_nova, alterado_em, comentario)"
                    + " VALUES (1301, ?, 'RECONHECIDO', 'RESOLVIDO', ?, 'Resolvido')", gerente, resolucao);
            var antes = jdbc.queryForList("SELECT * FROM vw_alertas_dashboard ORDER BY tipo_alerta, id_origem");
            var alertasAntes = jdbc.queryForList("SELECT * FROM alerta ORDER BY id");
            var historicoAntes = jdbc.queryForList("SELECT * FROM historico_alerta ORDER BY id");

            Flyway.configure().dataSource(datasource).target("18").load().migrate();

            var depois = jdbc.queryForList("SELECT * FROM vw_alertas_dashboard ORDER BY tipo_alerta, id_origem");
            assertThat(depois).hasSize(5);
            for (int i = 0; i < depois.size(); i++) {
                var atual = depois.get(i);
                Object fim = atual.remove("resolvido_em"); Object autor = atual.remove("resolvido_por_usuario_id");
                if (atual.get("tipo_alerta").equals("GENERICO") && atual.get("id_origem").equals(1301L)) {
                    assertThat(((java.time.OffsetDateTime) atual.get("reconhecido_em")).toInstant()).isEqualTo(reconhecimento.toInstant());
                    assertThat(atual.get("reconhecido_por_usuario_id")).isEqualTo(operador);
                    assertThat(((java.time.OffsetDateTime) fim).toInstant()).isEqualTo(resolucao.toInstant());
                    assertThat(autor).isEqualTo(gerente);
                    atual.put("reconhecido_em", null); atual.put("reconhecido_por_usuario_id", null);
                } else {
                    assertThat(fim).isNull(); assertThat(autor).isNull();
                }
                assertThat(atual).isEqualTo(antes.get(i));
            }
            assertThat(jdbc.queryForList("SELECT * FROM alerta ORDER BY id")).isEqualTo(alertasAntes);
            assertThat(jdbc.queryForList("SELECT * FROM historico_alerta ORDER BY id")).isEqualTo(historicoAntes);
        } finally {
            datasource.destroy();
        }
    }
}
