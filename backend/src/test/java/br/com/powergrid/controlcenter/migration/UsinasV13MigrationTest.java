package br.com.powergrid.controlcenter.migration;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsinasV13MigrationTest {
    @Test
    void deveRemoverSomenteUsinasLegadasSemReferenciasEPreservarOutrosAtivos() {
        var datasource = datasource();
        var jdbc = new JdbcTemplate(datasource);
        try {
            migrar(datasource, "12");
            usina(jdbc);
            ativo(jdbc, 9002, "Ativo preservado");
            jdbc.update("insert into subestacao (id) values (9002)");
            migrar(datasource, "13");
            assertThat(jdbc.queryForObject("select count(*) from usina_geradora where id = 9001", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from ativo_rede where id = 9001", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select nome from ativo_rede where id = 9002", String.class)).isEqualTo("Ativo preservado");
            assertThat(jdbc.queryForObject("select count(*) from subestacao where id = 9002", Long.class)).isEqualTo(1);
        } finally { datasource.destroy(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"EVENTO", "HISTORICO", "ALERTA", "FONTE", "SOBRECARGA", "TENSAO", "COMUNICACAO", "OUTRO_SUBTIPO"})
    void deveInterromperMigracaoEPreservarUsinaEReferenciasSemInventarSubestacao(String referencia) {
        var datasource = datasource();
        var jdbc = new JdbcTemplate(datasource);
        try {
            migrar(datasource, "12");
            usina(jdbc);
            criarReferencia(jdbc, referencia);
            var ativoAntes = jdbc.queryForMap("select * from ativo_rede where id = 9001");
            var usinaAntes = jdbc.queryForMap("select * from usina_geradora where id = 9001");
            var tabelas = java.util.List.of("evento_operacional", "historico_evento_operacional", "alerta", "fonte_medicao",
                    "medicao_linha", "medicao_transformador", "alerta_sobrecarga", "alerta_tensao", "alerta_comunicacao", "subestacao");
            var antes = new java.util.HashMap<String, java.util.List<java.util.Map<String, Object>>>();
            for (var tabela : tabelas) antes.put(tabela, jdbc.queryForList("select * from " + tabela + " order by id"));
            assertThrows(FlywayException.class, () -> migrar(datasource, "13"));
            assertThat(jdbc.queryForMap("select * from ativo_rede where id = 9001")).isEqualTo(ativoAntes);
            assertThat(jdbc.queryForMap("select id, tipo_geracao, capacidade_maxima_mw from usina_geradora where id = 9001")).isEqualTo(usinaAntes);
            for (var tabela : tabelas) {
                assertThat(jdbc.queryForList("select * from " + tabela + " order by id")).as(tabela).isEqualTo(antes.get(tabela));
            }
        } finally { datasource.destroy(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void devePreservarUsinaRegularizadaExplicitamenteAntesDaMigration(boolean referenciada) {
        var datasource = datasource();
        var jdbc = new JdbcTemplate(datasource);
        try {
            migrar(datasource, "12");
            usina(jdbc);
            ativo(jdbc, 9002, "Subestação informada na regularização");
            jdbc.update("insert into subestacao (id) values (9002)");
            jdbc.execute("alter table usina_geradora add column subestacao_id bigint");
            jdbc.update("update usina_geradora set tipo_geracao = 'SOLAR', capacidade_maxima_mw = 150, subestacao_id = 9002 where id = 9001");
            if (referenciada) criarReferencia(jdbc, "HISTORICO");
            var usinaAntes = jdbc.queryForMap("select * from usina_geradora where id = 9001");
            var ativoAntes = jdbc.queryForMap("select * from ativo_rede where id = 9001");
            migrar(datasource, "13");
            assertThat(jdbc.queryForMap("select * from usina_geradora where id = 9001")).isEqualTo(usinaAntes);
            assertThat(jdbc.queryForMap("select * from ativo_rede where id = 9001")).isEqualTo(ativoAntes);
            if (referenciada) assertThat(jdbc.queryForObject("select count(*) from historico_evento_operacional", Long.class)).isEqualTo(1);
        } finally { datasource.destroy(); }
    }

    private void criarReferencia(JdbcTemplate jdbc, String tipo) {
        Long usuario = jdbc.queryForObject("select min(id) from usuario", Long.class);
        switch (tipo) {
            case "EVENTO", "HISTORICO" -> {
                jdbc.update("insert into evento_operacional (id, ativo_id, categoria, situacao, registrador_usuario_id, responsavel_usuario_id) values (9101, 9001, 'MANUTENCAO', 'ABERTO', ?, ?)", usuario, usuario);
                if (tipo.equals("HISTORICO")) jdbc.update("insert into historico_evento_operacional (evento_id, usuario_id, situacao_anterior, situacao_nova, alterado_em, comentario) values (9101, ?, 'ABERTO', 'EM_ANDAMENTO', CURRENT_TIMESTAMP, 'Início registrado')", usuario);
            }
            case "ALERTA" -> jdbc.update("insert into alerta (tipo, escopo, ativo_id) values ('Alerta legado', 'ATIVO', 9001)");
            case "OUTRO_SUBTIPO" -> jdbc.update("insert into subestacao (id) values (9001)");
            default -> {
                long ativoFonte = 9001;
                if (!tipo.equals("FONTE")) { ativo(jdbc, 9002, "Outro ativo"); ativoFonte = 9002; }
                jdbc.update("insert into fonte_medicao (id, identificador, ativo_id, origem, monitorada_desde) values (9201, 'FONTE-V13', ?, 'SIMULADA', CURRENT_TIMESTAMP)", ativoFonte);
                if (tipo.equals("FONTE")) {
                    jdbc.update("insert into medicao_linha (fonte_id, medido_em, recebido_em, tensao_kv) values (9201, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 138)");
                    jdbc.update("insert into medicao_transformador (fonte_id, medido_em, recebido_em, potencia_aparente_mva, capacidade_referencia_mva) values (9201, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 5, 10)");
                }
                if (tipo.equals("SOBRECARGA")) {
                    jdbc.update("insert into medicao_transformador (id, fonte_id, medido_em, recebido_em, potencia_aparente_mva, capacidade_referencia_mva) values (9301, 9201, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 11, 10)");
                    jdbc.update("insert into alerta_sobrecarga (ativo_id, medicao_id, carga_percentual, limiar_percentual, status, gerado_em, criticidade) values (9001, 9301, 110, 100, 'ATIVO', CURRENT_TIMESTAMP, 'ALTA')");
                }
                if (tipo.equals("TENSAO")) {
                    jdbc.update("insert into medicao_linha (id, fonte_id, medido_em, recebido_em, tensao_kv) values (9301, 9201, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 90)");
                    jdbc.update("insert into alerta_tensao (ativo_id, medicao_id, tipo, tensao_medida_kv, tensao_referencia_kv, limite_inferior_kv, limite_superior_kv, status, gerado_em, criticidade) values (9001, 9301, 'SUBTENSAO', 90, 100, 95, 105, 'ATIVO', CURRENT_TIMESTAMP, 'ALTA')");
                }
                if (tipo.equals("COMUNICACAO")) jdbc.update("insert into alerta_comunicacao (fonte_id, ativo_id, monitorada_desde, tolerancia_segundos, detectado_em, status, criticidade) values (9201, 9001, CURRENT_TIMESTAMP, 300, CURRENT_TIMESTAMP, 'ATIVO', 'MEDIA')");
            }
        }
    }

    private void usina(JdbcTemplate jdbc) {
        ativo(jdbc, 9001, "Usina legada");
        jdbc.update("insert into usina_geradora (id, tipo_geracao, capacidade_maxima_mw) values (9001, 'TIPO_LEGADO', null)");
    }

    private void ativo(JdbcTemplate jdbc, long id, String nome) {
        jdbc.update("insert into ativo_rede (id, nome, estado_operacional) values (?, ?, 'OPERACIONAL')", id, nome);
    }

    private void migrar(SingleConnectionDataSource datasource, String versao) {
        Flyway.configure().dataSource(datasource).target(versao).load().migrate();
    }

    private SingleConnectionDataSource datasource() {
        return new SingleConnectionDataSource("jdbc:h2:mem:usinas_v13_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=0", "sa", "", true);
    }
}
