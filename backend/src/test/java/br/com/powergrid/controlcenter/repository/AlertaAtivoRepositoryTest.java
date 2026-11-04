package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.projection.AlertaAtivoProjection;
import br.com.powergrid.controlcenter.support.AlertasDashboardFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static br.com.powergrid.controlcenter.support.AlertasDashboardFixture.INSTANTE;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(AlertaAtivoRepository.class)
class AlertaAtivoRepositoryTest {
    @Autowired private AlertaAtivoRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;

    @BeforeEach
    void preparar() { AlertasDashboardFixture.inserir(jdbc); }

    @ParameterizedTest
    @CsvSource({"GENERICO,ABERTO,true", "GENERICO,RECONHECIDO,true", "GENERICO,RESOLVIDO,false",
            "SOBRECARGA,ATIVO,true", "SOBRECARGA,RECONHECIDO,false", "TENSAO,ATIVO,true",
            "TENSAO,RECONHECIDO,false", "COMUNICACAO,ATIVO,true", "COMUNICACAO,RESTABELECIDO,false"})
    void deveAplicarElegibilidadeDeCadaCicloDeVida(TipoAlertaDashboard tipo, String status, boolean elegivel) {
        if (tipo == TipoAlertaDashboard.GENERICO) {
            jdbc.update("UPDATE alerta SET situacao = ? WHERE id = 1301", status);
        } else if (tipo == TipoAlertaDashboard.COMUNICACAO) {
            jdbc.update("UPDATE alerta_comunicacao SET status = ?, restabelecido_em = ? WHERE id = 1301",
                    status, status.equals("ATIVO") ? null : INSTANTE.plusMinutes(1));
        } else {
            String tabela = tipo == TipoAlertaDashboard.TENSAO ? "alerta_tensao" : "alerta_sobrecarga";
            boolean reconhecido = status.equals("RECONHECIDO");
            jdbc.update("UPDATE " + tabela + " SET status = ?, reconhecido_em = ?, reconhecido_por_usuario_id = ? WHERE id = 1301",
                    status, reconhecido ? INSTANTE.plusMinutes(1) : null,
                    reconhecido ? jdbc.queryForObject("SELECT MIN(id) FROM usuario", Long.class) : null);
        }
        var pagina = buscar(tipo, null, null, null, null, null, null, null, null);
        assertThat(pagina.getTotalElements()).isEqualTo(elegivel ? 1 : 0);
        assertThat(pagina.getContent()).hasSize(elegivel ? 1 : 0);
        pagina.forEach(p -> assertThat(p.alerta().getStatusOrigem()).isEqualTo(status));
    }

    @Test
    void deveExcluirTerminaisReconhecidosEletricosESituacaoLegadaAusente() {
        jdbc.update("INSERT INTO alerta (id, escopo) VALUES (1302, 'REDE_MONITORADA')");
        var pagina = repository.buscar(FiltroAlertasAtivos.semFiltros(), PageRequest.of(0, 20));
        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent()).extracting(p -> p.alerta().getTipoAlerta()).containsExactly(TipoAlertaDashboard.TENSAO);
    }

    @Test
    void devePaginarOrigensComMesmoIdRespeitandoCriticidadeSemColisoes() {
        ativarTodos();
        var primeira = repository.buscar(FiltroAlertasAtivos.semFiltros(), PageRequest.of(0, 2, Sort.by("idOrigem")));
        var segunda = repository.buscar(FiltroAlertasAtivos.semFiltros(), PageRequest.of(1, 2));
        assertThat(primeira.getTotalElements()).isEqualTo(4);
        assertThat(primeira.getTotalPages()).isEqualTo(2);
        assertThat(primeira.getContent()).extracting(p -> p.alerta().getTipoAlerta())
                .containsExactly(TipoAlertaDashboard.SOBRECARGA, TipoAlertaDashboard.TENSAO);
        assertThat(segunda.getContent()).extracting(p -> p.alerta().getTipoAlerta())
                .containsExactly(TipoAlertaDashboard.COMUNICACAO, TipoAlertaDashboard.GENERICO);
        assertThat(primeira.getSort().isUnsorted()).isTrue();
        assertThat(segunda.getContent()).doesNotContainAnyElementsOf(primeira.getContent());
        assertThat(primeira.getContent()).extracting(p -> p.alerta().getIdOrigem()).containsOnly(1301L);
    }

    @Test
    void deveOrdenarPorCriticidadeAbsolutaDepoisRecenciaTipoEIdComNulosNoFinal() {
        ativarTodos();
        generico(1401, null, "CRITICA", INSTANTE.minusDays(2));
        generico(1402, null, "ALTA", INSTANTE.plusHours(1));
        generico(1403, null, "ALTA", INSTANTE.plusHours(1));
        generico(1404, null, "ALTA", INSTANTE);
        generico(1405, null, "ALTA", null);
        generico(1406, null, null, INSTANTE.plusYears(1));
        var pagina = repository.buscar(FiltroAlertasAtivos.semFiltros(), PageRequest.of(0, 20));
        assertThat(pagina.getContent()).extracting(p -> p.alerta().getIdOrigem())
                .containsExactly(1401L, 1403L, 1402L, 1404L, 1301L, 1301L, 1405L, 1301L, 1301L, 1406L);
        assertThat(pagina.getContent().subList(4, 6)).extracting(p -> p.alerta().getTipoAlerta())
                .containsExactly(TipoAlertaDashboard.SOBRECARGA, TipoAlertaDashboard.TENSAO);
    }

    @Test
    void deveAplicarJanelaSemiabertaComparandoInstantesComFusosDiferentes() {
        generico(1401, null, "ALTA", INSTANTE.minusNanos(1000));
        generico(1402, null, "ALTA", INSTANTE);
        generico(1403, null, "ALTA", INSTANTE.plusHours(1));
        generico(1404, null, "ALTA", null);
        var inicio = INSTANTE.withOffsetSameInstant(ZoneOffset.ofHours(-3));
        var fim = inicio.plusHours(1);
        assertThat(buscar(TipoAlertaDashboard.GENERICO, null, null, null, null, null, null, inicio, fim).getContent())
                .extracting(p -> p.alerta().getIdOrigem()).containsExactly(1402L);
        assertThat(buscar(TipoAlertaDashboard.GENERICO, null, null, null, null, null, null, inicio, null).getContent())
                .extracting(p -> p.alerta().getIdOrigem()).containsExactly(1403L, 1402L);
        assertThat(buscar(TipoAlertaDashboard.GENERICO, null, null, null, null, null, null, null, fim).getContent())
                .extracting(p -> p.alerta().getIdOrigem()).containsExactly(1402L, 1401L);
    }

    @Test
    void deveCombinarTodosOsFiltrosSemReintroduzirInativos() {
        ativarTodos();
        var pagina = buscar(TipoAlertaDashboard.TENSAO, Criticidade.ALTA, EscopoAlerta.ATIVO,
                1003L, 1101L, TipoAtivoAlerta.LINHA, 1001L, INSTANTE, INSTANTE.plusSeconds(1));
        assertThat(pagina.getTotalElements()).isEqualTo(1);
        var resultado = pagina.getContent().getFirst();
        assertThat(resultado.tipoAtivo()).isEqualTo(TipoAtivoAlerta.LINHA);
        assertThat(resultado.alerta().getTensaoMedidaKv()).isEqualByComparingTo("90");
        assertThat(resultado.alerta().getSubtipo()).isEqualTo("SUBTENSAO");
        jdbc.update("UPDATE alerta_tensao SET status='RECONHECIDO', reconhecido_em=?, reconhecido_por_usuario_id=(SELECT MIN(id) FROM usuario) WHERE id=1301", INSTANTE);
        em.clear();
        assertThat(buscar(TipoAlertaDashboard.TENSAO, Criticidade.ALTA, EscopoAlerta.ATIVO,
                1003L, 1101L, TipoAtivoAlerta.LINHA, 1001L, INSTANTE, INSTANTE.plusSeconds(1)).getTotalElements()).isZero();
    }

    @Test
    void deveFiltrarFonteAtivoEscopoECriticidadeSeparadamente() {
        ativarTodos();
        assertThat(buscar(null, null, null, null, 1101L, null, null, null, null).getTotalElements()).isEqualTo(2);
        assertThat(buscar(null, null, null, null, 1102L, null, null, null, null).getTotalElements()).isEqualTo(1);
        assertThat(buscar(null, null, null, 1003L, null, null, null, null, null).getTotalElements()).isEqualTo(2);
        assertThat(buscar(null, null, EscopoAlerta.REDE_MONITORADA, null, null, null, null, null, null).getContent())
                .allSatisfy(p -> { assertThat(p.alerta().getAtivoId()).isNull(); assertThat(p.tipoAtivo()).isNull(); });
        assertThat(buscar(null, null, EscopoAlerta.ATIVO, null, null, null, null, null, null).getTotalElements()).isEqualTo(3);
        assertThat(buscar(null, Criticidade.MEDIA, null, null, null, null, null, null, null).getContent())
                .extracting(p -> p.alerta().getTipoAlerta()).containsExactly(TipoAlertaDashboard.COMUNICACAO);
        assertThat(buscar(null, null, null, Long.MAX_VALUE, null, null, null, null, null).getTotalElements()).isZero();
        assertThat(buscar(null, null, null, null, Long.MAX_VALUE, null, null, null, null).getTotalElements()).isZero();
    }

    @Test
    void deveDerivarSubestacaoDosSeisTiposSemDuplicarLinhaConectada() {
        topologia();
        assertThat(buscar(TipoAlertaDashboard.GENERICO, null, null, null, null, null, 1001L, null, null).getContent())
                .extracting(p -> p.alerta().getIdOrigem()).containsExactly(1406L, 1405L, 1404L, 1403L, 1402L, 1401L);
        assertThat(buscar(TipoAlertaDashboard.GENERICO, null, null, null, null, null, 1002L, null, null).getContent())
                .extracting(p -> p.alerta().getIdOrigem()).containsExactly(1411L, 1410L, 1409L, 1408L, 1407L, 1406L);
        assertThat(buscar(null, null, null, null, null, null, 1006L, null, null).getTotalElements()).isZero();
        assertThat(buscar(null, null, null, null, null, null, Long.MAX_VALUE, null, null).getTotalElements()).isZero();
    }

    @ParameterizedTest
    @EnumSource(TipoAtivoAlerta.class)
    void deveFiltrarEProjetarCadaTipoDeAtivoComSubestacao(TipoAtivoAlerta tipo) {
        topologia();
        var pagina = buscar(TipoAlertaDashboard.GENERICO, null, null, null, null, tipo, 1001L, null, null);
        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent()).extracting(AlertaAtivoProjection::tipoAtivo).containsExactly(tipo);
    }

    @Test
    void devePreservarMedidorEmEstoqueSemAssociacaoESemIncluirEmSubestacao() {
        topologia();
        var estoque = buscar(TipoAlertaDashboard.GENERICO, null, null, 1011L, null, TipoAtivoAlerta.MEDIDOR, null, null, null);
        assertThat(estoque.getTotalElements()).isEqualTo(1);
        assertThat(estoque.getContent().getFirst().tipoAtivo()).isEqualTo(TipoAtivoAlerta.MEDIDOR);
        assertThat(buscar(TipoAlertaDashboard.GENERICO, null, null, 1011L, null, null, 1001L, null, null).getTotalElements()).isZero();
    }

    @Test
    void deveUsarTopologiaAtualDoMedidorSemAfetarIdentidadeDoAlerta() {
        topologia();
        jdbc.update("UPDATE medidor_inteligente SET transformador_id=1008 WHERE id=1009");
        var a = buscar(TipoAlertaDashboard.GENERICO, null, null, 1009L, null, null, 1001L, null, null);
        var b = buscar(TipoAlertaDashboard.GENERICO, null, null, 1009L, null, null, 1002L, null, null);
        assertThat(a.getTotalElements()).isZero();
        assertThat(b.getContent()).extracting(p -> p.alerta().getIdOrigem()).containsExactly(1404L);
    }

    @Test
    void devePaginarConteudoEContagemAplicandoMesmaDerivacaoTopologica() {
        topologia();
        var filtro = new FiltroAlertasAtivos(TipoAlertaDashboard.GENERICO, null, null, null, null, null, 1001L, null, null);
        var pagina = repository.buscar(filtro, PageRequest.of(1, 2));
        assertThat(pagina.getTotalElements()).isEqualTo(6);
        assertThat(pagina.getTotalPages()).isEqualTo(3);
        assertThat(pagina.getContent()).extracting(p -> p.alerta().getIdOrigem()).containsExactly(1404L, 1403L);
    }

    @Test
    void deveConsultarProjecoesSemNMaisUm() {
        ativarTodos();
        var estatisticas = emf.unwrap(SessionFactory.class).getStatistics();
        boolean habilitado = estatisticas.isStatisticsEnabled();
        estatisticas.setStatisticsEnabled(true);
        try {
            long antes = estatisticas.getPrepareStatementCount();
            var pagina = repository.buscar(FiltroAlertasAtivos.semFiltros(), PageRequest.of(0, 20));
            pagina.forEach(p -> { assertThat(p.alerta().getStatusOrigem()).isNotBlank(); p.alerta().getFonteId(); p.tipoAtivo(); });
            assertThat(estatisticas.getPrepareStatementCount() - antes).isEqualTo(2);
        } finally { estatisticas.setStatisticsEnabled(habilitado); }
    }

    @Test
    void deveRetornarPaginaDistanteVaziaSemOverflowEPreservarTotal() {
        var pagina = repository.buscar(FiltroAlertasAtivos.semFiltros(), PageRequest.of(Integer.MAX_VALUE, 100));
        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent()).isEmpty();
    }

    @Test
    void deveExigirPaginacao() {
        assertThatThrownBy(() -> repository.buscar(FiltroAlertasAtivos.semFiltros(), Pageable.unpaged()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Page<AlertaAtivoProjection> buscar(TipoAlertaDashboard tipo, Criticidade criticidade, EscopoAlerta escopo,
            Long ativo, Long fonte, TipoAtivoAlerta tipoAtivo, Long subestacao, OffsetDateTime inicio, OffsetDateTime fim) {
        return repository.buscar(new FiltroAlertasAtivos(tipo, criticidade, escopo, ativo, fonte, tipoAtivo, subestacao, inicio, fim), PageRequest.of(0, 50));
    }

    private void ativarTodos() {
        jdbc.update("UPDATE alerta SET situacao='ABERTO' WHERE id=1301");
        jdbc.update("UPDATE alerta_sobrecarga SET status='ATIVO', reconhecido_em=NULL, reconhecido_por_usuario_id=NULL WHERE id=1301");
        jdbc.update("UPDATE alerta_comunicacao SET status='ATIVO', restabelecido_em=NULL WHERE id=1301");
    }

    private void generico(long id, Long ativo, String criticidade, OffsetDateTime instante) {
        jdbc.update("INSERT INTO alerta (id, tipo, criticidade, escopo, ativo_id, situacao, gerado_em) VALUES (?, 'Aviso', ?, ?, ?, 'ABERTO', ?)",
                id, criticidade, ativo == null ? "REDE_MONITORADA" : "ATIVO", ativo, instante);
    }

    private void topologia() {
        for (long id = 1006; id <= 1013; id++)
            jdbc.update("INSERT INTO ativo_rede (id, nome, estado_operacional) VALUES (?, ?, 'OPERACIONAL')", id, "Ativo " + id);
        jdbc.update("INSERT INTO subestacao (id) VALUES (1006)");
        jdbc.update("INSERT INTO alimentador (id, subestacao_id) VALUES (1007, 1002)");
        jdbc.update("INSERT INTO transformador (id, alimentador_id, capacidade_mva) VALUES (1008, 1007, 10)");
        jdbc.update("INSERT INTO medidor_inteligente (id, numero_serie, transformador_id) VALUES (1009, 'MAPA-A', 1005), (1011, 'MAPA-ESTOQUE', NULL), (1012, 'MAPA-B', 1008)");
        jdbc.update("INSERT INTO usina_geradora (id, tipo_geracao, capacidade_maxima_mw, subestacao_id) VALUES (1010, 'SOLAR', 10, 1001), (1013, 'EOLICA', 10, 1002)");
        long[] ativos = {1001,1004,1005,1009,1010,1003,1002,1007,1008,1012,1013,1011};
        for (int i = 0; i < ativos.length; i++) generico(1401 + i, ativos[i], "ALTA", INSTANTE);
        generico(1413, null, "ALTA", INSTANTE);
    }
}
