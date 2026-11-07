package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.powergrid.controlcenter.dto.AtivoSnapshotDTO.Tipo;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MonitoramentoSnapshotRepository {
    private static final String LINHAS = """
            SELECT a.id, a.nome, 'LINHA' AS tipo,
                   l.subestacao_origem_id AS subestacao_id, l.subestacao_destino_id,
                   l.tensao_operacao_kv AS tensao_operacao
              FROM linha_transmissao l JOIN ativo_rede a ON a.id = l.id
            """;
    private static final String TRANSFORMADORES = """
            SELECT a.id, a.nome, 'TRANSFORMADOR' AS tipo,
                   al.subestacao_id, CAST(NULL AS BIGINT) AS subestacao_destino_id,
                   al.tensao_nominal_kv AS tensao_operacao
              FROM transformador t JOIN ativo_rede a ON a.id = t.id
              JOIN alimentador al ON al.id = t.alimentador_id
            """;
    private static final String ULTIMAS_GRANDEZAS_LINHA = """
            SELECT medicao_id, ativo_id, fonte_id, medido_em, recebido_em, origem_leitura, terminal_referencia_id, grandeza, valor, reativa
              FROM (
                    SELECT m.id AS medicao_id, f.ativo_id, m.fonte_id, m.medido_em, m.recebido_em, g.grandeza,
                           f.origem AS origem_leitura, f.subestacao_referencia_id AS terminal_referencia_id,
                           CASE g.grandeza
                               WHEN 'TENSAO' THEN m.tensao_kv
                               WHEN 'CORRENTE' THEN m.corrente_a
                               WHEN 'FREQUENCIA' THEN m.frequencia_hz
                               WHEN 'REATIVA' THEN m.potencia_reativa_mvar
                               ELSE m.potencia_ativa_mw
                           END AS valor,
                           m.potencia_reativa_mvar AS reativa,
                           ROW_NUMBER() OVER (PARTITION BY f.ativo_id, g.grandeza
                                               ORDER BY m.medido_em DESC, m.id DESC) AS posicao
                      FROM fonte_medicao f JOIN medicao_linha m ON m.fonte_id = f.id
                     CROSS JOIN (VALUES ('TENSAO'), ('CORRENTE'), ('FREQUENCIA'),
                                        ('ATIVA'), ('REATIVA'), ('FATOR')) AS g(grandeza)
                     WHERE f.ativo_id IN (:ids) AND m.medido_em <= :ate
                       AND CASE g.grandeza
                           WHEN 'TENSAO' THEN m.tensao_kv IS NOT NULL
                           WHEN 'CORRENTE' THEN m.corrente_a IS NOT NULL
                           WHEN 'FREQUENCIA' THEN m.frequencia_hz IS NOT NULL
                           WHEN 'ATIVA' THEN m.potencia_ativa_mw IS NOT NULL
                           WHEN 'REATIVA' THEN m.potencia_reativa_mvar IS NOT NULL
                           ELSE m.potencia_ativa_mw IS NOT NULL AND m.potencia_reativa_mvar IS NOT NULL
                       END
                   ) leituras
             WHERE posicao = 1
            """;
    private static final String ULTIMA_LEITURA_TRANSFORMADOR = """
            SELECT medicao_id, ativo_id, fonte_id, medido_em, recebido_em, origem_leitura, terminal_referencia_id,
                   potencia_aparente_mva, capacidade_referencia_mva
              FROM (
                    SELECT m.id AS medicao_id, f.ativo_id, m.fonte_id, m.medido_em, m.recebido_em,
                           f.origem AS origem_leitura, CAST(NULL AS BIGINT) AS terminal_referencia_id,
                           m.potencia_aparente_mva, m.capacidade_referencia_mva,
                           ROW_NUMBER() OVER (PARTITION BY f.ativo_id ORDER BY m.medido_em DESC, m.id DESC) AS posicao
                      FROM fonte_medicao f JOIN medicao_transformador m ON m.fonte_id = f.id
                     WHERE f.ativo_id IN (:ids) AND m.medido_em <= :ate
                   ) leituras
             WHERE posicao = 1
            """;

    private final JdbcClient jdbc;

    public MonitoramentoSnapshotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PaginaAtivos buscarAtivos(Set<Tipo> tipos, Long ativoId, Long subestacaoId, Pageable pagina) {
        var consultas = new ArrayList<String>();
        var parametros = new HashMap<String, Object>();
        if (ativoId != null) {
            parametros.put("ativoId", ativoId);
        }
        if (subestacaoId != null) {
            parametros.put("subestacaoId", subestacaoId);
        }
        String filtroAtivo = ativoId == null ? " WHERE 1 = 1" : " WHERE a.id = :ativoId";
        if (tipos.contains(Tipo.LINHA)) {
            consultas.add(LINHAS + filtroAtivo + (subestacaoId == null ? ""
                    : " AND (l.subestacao_origem_id = :subestacaoId OR l.subestacao_destino_id = :subestacaoId)"));
        }
        if (tipos.contains(Tipo.TRANSFORMADOR)) {
            consultas.add(TRANSFORMADORES + filtroAtivo
                    + (subestacaoId == null ? "" : " AND al.subestacao_id = :subestacaoId"));
        }
        String origem = " FROM (" + String.join(" UNION ALL ", consultas) + ") ativos";
        long total = jdbc.sql("SELECT COUNT(*)" + origem).params(parametros).query(Long.class).single();
        if (pagina.getOffset() >= total) {
            return new PaginaAtivos(List.of(), total);
        }
        parametros.put("limite", pagina.getPageSize());
        parametros.put("offset", pagina.getOffset());
        var ativos = jdbc.sql("SELECT *" + origem + " ORDER BY id LIMIT :limite OFFSET :offset")
                .params(parametros)
                .query((rs, n) -> new Ativo(rs.getLong("id"), rs.getString("nome"), Tipo.valueOf(rs.getString("tipo")),
                        rs.getLong("subestacao_id"), rs.getObject("subestacao_destino_id", Long.class),
                        rs.getBigDecimal("tensao_operacao")))
                .list();
        return new PaginaAtivos(ativos, total);
    }

    public List<GrandezaLinha> buscarGrandezasLinha(List<Long> ids, Instant ate) {
        return jdbc.sql(ULTIMAS_GRANDEZAS_LINHA).param("ids", ids).param("ate", ate.atOffset(ZoneOffset.UTC))
                .query((rs, n) -> new GrandezaLinha(rs.getLong("ativo_id"), Grandeza.valueOf(rs.getString("grandeza")),
                        referencia(rs), rs.getBigDecimal("valor"), rs.getBigDecimal("reativa")))
                .list();
    }

    public List<LeituraTransformador> buscarLeiturasTransformador(List<Long> ids, Instant ate) {
        return jdbc.sql(ULTIMA_LEITURA_TRANSFORMADOR).param("ids", ids).param("ate", ate.atOffset(ZoneOffset.UTC))
                .query((rs, n) -> new LeituraTransformador(rs.getLong("ativo_id"), referencia(rs),
                        rs.getBigDecimal("potencia_aparente_mva"), rs.getBigDecimal("capacidade_referencia_mva")))
                .list();
    }

    private Referencia referencia(ResultSet rs) throws SQLException {
        return new Referencia(rs.getLong("medicao_id"), rs.getString("origem_leitura"),
                rs.getObject("terminal_referencia_id", Long.class), rs.getLong("fonte_id"), rs.getObject("medido_em", OffsetDateTime.class).toInstant(),
                rs.getObject("recebido_em", OffsetDateTime.class).toInstant());
    }

    public enum Grandeza {
        TENSAO, CORRENTE, FREQUENCIA, ATIVA, REATIVA, FATOR
    }

    public record Ativo(Long id, String nome, Tipo tipo, Long subestacaoId, Long subestacaoDestinoId, BigDecimal tensaoOperacao) {}
    public record PaginaAtivos(List<Ativo> ativos, long total) {}
    public record Referencia(Long medicaoId, String origemLeitura, Long terminalReferenciaId, Long fonteId,
            Instant medidoEm, Instant recebidoEm) {}
    public record GrandezaLinha(Long ativoId, Grandeza grandeza, Referencia referencia, BigDecimal valor, BigDecimal reativa) {}
    public record LeituraTransformador(Long ativoId, Referencia referencia, BigDecimal potencia, BigDecimal capacidade) {}
}
