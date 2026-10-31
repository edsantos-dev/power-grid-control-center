package br.com.powergrid.controlcenter.repository;

import java.util.List;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.support.AlertasDashboardFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class AlertaDashboardViewRepositoryTest {
    @Autowired private AlertaDashboardViewRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        AlertasDashboardFixture.inserir(jdbc);
    }

    @Test
    void deveUnificarQuatroOrigensComMesmoIdSemColisoesNoContextoJpa() {
        var registros = repository.findAll();
        assertThat(registros).hasSize(4);
        assertThat(registros).extracting(AlertaDashboardView::getIdOrigem).containsOnly(1301L);
        assertThat(registros).extracting(AlertaDashboardView::getTipoAlerta).containsExactlyInAnyOrder(TipoAlertaDashboard.values());
        assertThat(registros).doesNotHaveDuplicates();
        assertThat(registro(TipoAlertaDashboard.GENERICO).getCriticidade()).isEqualTo(Criticidade.BAIXA);
        assertThat(registro(TipoAlertaDashboard.SOBRECARGA).getCriticidade()).isEqualTo(Criticidade.ALTA);
        assertThat(registro(TipoAlertaDashboard.TENSAO).getCriticidade()).isEqualTo(Criticidade.ALTA);
        assertThat(registro(TipoAlertaDashboard.COMUNICACAO).getCriticidade()).isEqualTo(Criticidade.MEDIA);
    }

    @Test
    void devePreservarEscopoGeralEEstadosSemConverterReconhecimentoEmResolucao() {
        var generico = registro(TipoAlertaDashboard.GENERICO);
        assertThat(generico.getEscopo()).isEqualTo(EscopoAlerta.REDE_MONITORADA);
        assertThat(generico.getAtivoId()).isNull(); assertThat(generico.getMensagem()).isEqualTo("Aviso geral");
        assertThat(generico.getStatusOrigem()).isEqualTo("RESOLVIDO");
        assertThat(generico.getReconhecidoEm()).isNull(); assertThat(generico.getRestabelecidoEm()).isNull();
        var sobrecarga = registro(TipoAlertaDashboard.SOBRECARGA);
        assertThat(sobrecarga.getStatusOrigem()).isEqualTo("RECONHECIDO");
        assertThat(sobrecarga.getReconhecidoEm().toInstant()).isEqualTo(AlertasDashboardFixture.INSTANTE.plusMinutes(1).toInstant());
        assertThat(sobrecarga.getReconhecidoPorUsuarioId()).isEqualTo(jdbc.queryForObject("SELECT MIN(id) FROM usuario", Long.class));
        assertThat(sobrecarga.getRestabelecidoEm()).isNull();
        var comunicacao = registro(TipoAlertaDashboard.COMUNICACAO);
        assertThat(comunicacao.getStatusOrigem()).isEqualTo("RESTABELECIDO");
        assertThat(comunicacao.getRestabelecidoEm().toInstant()).isEqualTo(AlertasDashboardFixture.INSTANTE.plusMinutes(1).toInstant());
        assertThat(comunicacao.getReconhecidoEm()).isNull(); assertThat(comunicacao.getReconhecidoPorUsuarioId()).isNull();
    }

    @Test
    void deveProjetarVinculosInstantesESnapshotsTecnicos() {
        var sobrecarga = registro(TipoAlertaDashboard.SOBRECARGA);
        assertThat(sobrecarga.getAtivoId()).isEqualTo(1005L); assertThat(sobrecarga.getFonteId()).isEqualTo(1102L);
        assertThat(sobrecarga.getMedicaoId()).isEqualTo(1202L);
        assertThat(sobrecarga.getCargaPercentual()).isEqualByComparingTo("120");
        assertThat(sobrecarga.getLimiarPercentual()).isEqualByComparingTo("100");
        var tensao = registro(TipoAlertaDashboard.TENSAO);
        assertThat(tensao.getSubtipo()).isEqualTo("SUBTENSAO"); assertThat(tensao.getFonteId()).isEqualTo(1101L);
        assertThat(tensao.getMedicaoId()).isEqualTo(1201L); assertThat(tensao.getTensaoMedidaKv()).isEqualByComparingTo("90");
        assertThat(tensao.getTensaoReferenciaKv()).isEqualByComparingTo("100");
        assertThat(tensao.getLimiteInferiorKv()).isEqualByComparingTo("95");
        assertThat(tensao.getLimiteSuperiorKv()).isEqualByComparingTo("105");
        var comunicacao = registro(TipoAlertaDashboard.COMUNICACAO);
        assertThat(comunicacao.getFonteId()).isEqualTo(1101L); assertThat(comunicacao.getMedicaoId()).isNull();
        assertThat(comunicacao.getToleranciaSegundos()).isEqualTo(300);
        assertThat(comunicacao.getMonitoradaDesde().toInstant()).isEqualTo(AlertasDashboardFixture.INSTANTE.minusDays(1).toInstant());
        assertThat(comunicacao.getUltimaComunicacaoEm().toInstant()).isEqualTo(AlertasDashboardFixture.INSTANTE.minusHours(1).toInstant());
        for (var registro : repository.findAll()) {
            assertThat(registro.getOcorridoEm().toInstant()).isEqualTo(AlertasDashboardFixture.INSTANTE.toInstant());
        }
    }

    @Test
    void deveManterValoresAusentesDeAlertaGenericoLegado() {
        jdbc.update("INSERT INTO alerta (id, escopo) VALUES (1302, 'REDE_MONITORADA')");
        var legado = repository.findById(new AlertaDashboardId(1302L, TipoAlertaDashboard.GENERICO)).orElseThrow();
        assertThat(legado.getCriticidade()).isNull(); assertThat(legado.getOcorridoEm()).isNull();
        assertThat(legado.getStatusOrigem()).isNull(); assertThat(legado.getMensagem()).isNull();
    }

    @Test
    void deveRefletirAlteracaoNaOrigemSemSincronizarTabelaAuxiliar() {
        assertThat(registro(TipoAlertaDashboard.TENSAO).getCriticidade()).isEqualTo(Criticidade.ALTA);
        jdbc.update("UPDATE alerta_tensao SET criticidade = 'CRITICA' WHERE id = 1301");
        entityManager.clear();
        assertThat(registro(TipoAlertaDashboard.TENSAO).getCriticidade()).isEqualTo(Criticidade.CRITICA);
        assertThat(repository.count()).isEqualTo(4);
    }

    @Test
    void devePaginarNoBancoEContarTodasAsOrigens() {
        var pagina = repository.findAll(PageRequest.of(0, 2, Sort.by("tipoAlerta", "idOrigem")));
        assertThat(pagina.getTotalElements()).isEqualTo(4); assertThat(pagina.getTotalPages()).isEqualTo(2);
        assertThat(pagina.getContent()).hasSize(2);
        var segunda = repository.findAll(PageRequest.of(1, 2, Sort.by("tipoAlerta", "idOrigem")));
        assertThat(segunda.getContent()).hasSize(2).doesNotContainAnyElementsOf(pagina.getContent());
        assertThat(repository.findById(new AlertaDashboardId(Long.MAX_VALUE, TipoAlertaDashboard.TENSAO))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Criticidade.class)
    void deveAceitarTodosOsNiveisNasTabelasAutomaticas(Criticidade criticidade) {
        for (String tabela : tabelas()) jdbc.update("UPDATE " + tabela + " SET criticidade = ? WHERE id = 1301", criticidade.name());
        entityManager.clear();
        assertThat(repository.findAll().stream().filter(a -> a.getTipoAlerta() != TipoAlertaDashboard.GENERICO))
                .allSatisfy(a -> assertThat(a.getCriticidade()).isEqualTo(criticidade));
    }

    @ParameterizedTest
    @ValueSource(strings = {"alerta_sobrecarga", "alerta_tensao", "alerta_comunicacao"})
    void deveRejeitarCriticidadeDesconhecida(String tabela) {
        var ex = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE " + tabela + " SET criticidade = 'OUTRA' WHERE id = 1301"));
        assertThat(ex.getMostSpecificCause().getMessage()).containsIgnoringCase("ck_" + tabela + "_criticidade");
    }

    @ParameterizedTest
    @ValueSource(strings = {"alerta_sobrecarga", "alerta_tensao", "alerta_comunicacao"})
    void deveRejeitarCriticidadeNula(String tabela) {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE " + tabela + " SET criticidade = NULL WHERE id = 1301"));
    }

    @Test
    void naoDevePermitirAtualizacaoDiretaDaView() {
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE vw_alertas_dashboard SET criticidade = 'BAIXA' WHERE tipo_alerta = 'TENSAO'"));
    }

    private List<String> tabelas() { return List.of("alerta_sobrecarga", "alerta_tensao", "alerta_comunicacao"); }

    private AlertaDashboardView registro(TipoAlertaDashboard tipo) {
        return repository.findById(new AlertaDashboardId(1301L, tipo)).orElseThrow();
    }
}
