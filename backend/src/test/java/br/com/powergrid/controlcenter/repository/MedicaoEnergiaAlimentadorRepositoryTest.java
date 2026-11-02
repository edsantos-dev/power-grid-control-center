package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.check_nullability=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class MedicaoEnergiaAlimentadorRepositoryTest {
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-01-01T10:00:00Z");
    @Autowired private MedicaoEnergiaAlimentadorRepository repository;
    @Autowired private EntityManager em;
    @Autowired private AlimentadorRepository alimentadores;
    private int sequencia;

    @ParameterizedTest
    @ValueSource(strings = {"0", "3.123456", "99.999999"})
    void devePersistirDadosDeEnergiaEPercentuaisSemPerderPrecisao(String percentual) {
        var alimentador = alimentador(); alimentador.setPercentualPerdaTecnica(new BigDecimal(percentual)); em.flush();
        var fonte = fonte(alimentador); var medicao = nova(fonte); medicao.setPercentualPerdaReferencia(new BigDecimal(percentual));
        repository.saveAndFlush(medicao); em.clear();
        var salva = repository.findById(medicao.getId()).orElseThrow();
        assertThat(salva.getId()).isPositive(); assertThat(Hibernate.isInitialized(salva.getFonte())).isFalse();
        assertThat(salva.getFonte().getId()).isEqualTo(fonte.getId());
        assertThat(salva.getInicioPeriodo()).isEqualTo(INICIO);
        assertThat(salva.getFimPeriodo()).isEqualTo(INICIO.plusHours(1));
        assertThat(salva.getRecebidoEm()).isEqualTo(INICIO.plusHours(2));
        assertThat(salva.getEnergiaInjetadaMwh()).isEqualByComparingTo("100.123456");
        assertThat(salva.getPercentualPerdaReferencia()).isEqualByComparingTo(percentual);
        assertThat(em.find(Alimentador.class, alimentador.getId()).getPercentualPerdaTecnica()).isEqualByComparingTo(percentual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "9999999999999.999999"})
    void deveAceitarEnergiaZeroELimiteDoNumeric(String energia) {
        var medicao = nova(fonte(alimentador())); medicao.setEnergiaInjetadaMwh(new BigDecimal(energia));
        repository.saveAndFlush(medicao); em.clear();
        assertThat(repository.findById(medicao.getId()).orElseThrow().getEnergiaInjetadaMwh()).isEqualByComparingTo(energia);
    }

    @Test
    void devePreservarPercentualDeReferenciaAposAlteracaoDoParametro() {
        var alimentador = alimentador(); alimentador.setPercentualPerdaTecnica(new BigDecimal("3.123456")); em.flush();
        var medicao = nova(fonte(alimentador)); medicao.setPercentualPerdaReferencia(alimentador.getPercentualPerdaTecnica());
        repository.saveAndFlush(medicao);
        alimentador.setPercentualPerdaTecnica(new BigDecimal("8.654321")); em.flush(); em.clear();
        assertThat(repository.findById(medicao.getId()).orElseThrow().getPercentualPerdaReferencia()).isEqualByComparingTo("3.123456");
        assertThat(em.find(Alimentador.class, alimentador.getId()).getPercentualPerdaTecnica()).isEqualByComparingTo("8.654321");
    }

    @Test
    void devePermitirParametroCadastralAindaNaoConfigurado() {
        var alimentador = alimentador(); em.clear();
        assertThat(em.find(Alimentador.class, alimentador.getId()).getPercentualPerdaTecnica()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"fonte", "inicio", "fim", "recebido", "energia", "percentual"})
    void deveRejeitarNulosObrigatoriosNoBanco(String campo) {
        var medicao = nova(fonte(alimentador()));
        switch (campo) {
            case "fonte" -> medicao.setFonte(null);
            case "inicio" -> medicao.setInicioPeriodo(null);
            case "fim" -> medicao.setFimPeriodo(null);
            case "recebido" -> medicao.setRecebidoEm(null);
            case "energia" -> medicao.setEnergiaInjetadaMwh(null);
            default -> medicao.setPercentualPerdaReferencia(null);
        }
        violacao(() -> repository.saveAndFlush(medicao), "23502");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-0.000001"})
    void deveRejeitarEnergiaNegativa(String energia) {
        var medicao = nova(fonte(alimentador())); medicao.setEnergiaInjetadaMwh(new BigDecimal(energia));
        violacao(() -> repository.saveAndFlush(medicao), "23513");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.000001", "100", "100.000001"})
    void deveRejeitarPercentualDeReferenciaForaDoIntervalo(String percentual) {
        var medicao = nova(fonte(alimentador())); medicao.setPercentualPerdaReferencia(new BigDecimal(percentual));
        violacao(() -> repository.saveAndFlush(medicao), "23513");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.000001", "100", "100.000001"})
    void deveRejeitarParametroDoAlimentadorForaDoIntervalo(String percentual) {
        var alimentador = alimentador(); alimentador.setPercentualPerdaTecnica(new BigDecimal(percentual));
        violacao(() -> alimentadores.saveAndFlush(alimentador), "23513");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void deveRejeitarIntervaloVazioOuInvertido(int horas) {
        var medicao = nova(fonte(alimentador())); medicao.setFimPeriodo(INICIO.plusHours(horas));
        violacao(() -> repository.saveAndFlush(medicao), "23513");
    }

    @Test
    void deveRejeitarFonteInexistente() {
        var medicao = nova(em.getReference(FonteMedicao.class, Long.MAX_VALUE));
        violacao(() -> repository.saveAndFlush(medicao), "23506");
    }

    @Test
    void deveRejeitarIntervaloDuplicadoNaMesmaFonte() {
        var fonte = fonte(alimentador()); repository.saveAndFlush(nova(fonte));
        violacao(() -> repository.saveAndFlush(nova(fonte)), "23505");
    }

    @ParameterizedTest
    @CsvSource({"09:00,10:00,false", "11:00,12:00,false", "08:00,09:00,false", "12:00,13:00,false",
            "10:00,11:00,true", "09:30,10:30,true", "10:30,11:30,true", "10:15,10:45,true", "09:00,12:00,true"})
    void deveDetectarSobreposicaoSemConfundirIntervalosAdjacentes(String inicio, String fim, boolean esperado) {
        var alimentador = alimentador(); repository.saveAndFlush(nova(fonte(alimentador)));
        assertThat(repository.existeSobreposicaoPorAlimentador(alimentador.getId(), instante(inicio), instante(fim))).isEqualTo(esperado);
    }

    @Test
    void deveDetectarSobreposicaoEntreFontesDiferentesDoMesmoAlimentador() {
        var alimentador = alimentador();
        repository.saveAndFlush(nova(fonte(alimentador)));
        var segunda = nova(fonte(alimentador)); segunda.setInicioPeriodo(INICIO.plusHours(2)); segunda.setFimPeriodo(INICIO.plusHours(3));
        repository.saveAndFlush(segunda); em.clear();
        assertThat(repository.existeSobreposicaoPorAlimentador(alimentador.getId(), INICIO.plusMinutes(15), INICIO.plusMinutes(45))).isTrue();
        assertThat(repository.existeSobreposicaoPorAlimentador(alimentador.getId(), INICIO.plusHours(2).plusMinutes(15), INICIO.plusHours(2).plusMinutes(45))).isTrue();
    }

    @Test
    void deveIsolarAlimentadoresNaConsultaDeSobreposicao() {
        var a = alimentador(); var b = alimentador(); repository.saveAndFlush(nova(fonte(b)));
        assertThat(repository.existeSobreposicaoPorAlimentador(a.getId(), INICIO, INICIO.plusHours(1))).isFalse();
        assertThat(repository.existeSobreposicaoPorAlimentador(b.getId(), INICIO, INICIO.plusHours(1))).isTrue();
        assertThat(repository.existeSobreposicaoPorAlimentador(Long.MAX_VALUE, INICIO, INICIO.plusHours(1))).isFalse();
    }

    @Test
    void deveCompararInstantesComFusosDistintos() {
        var alimentador = alimentador(); repository.saveAndFlush(nova(fonte(alimentador)));
        assertThat(repository.existeSobreposicaoPorAlimentador(alimentador.getId(),
                OffsetDateTime.parse("2026-01-01T07:30:00-03:00"), OffsetDateTime.parse("2026-01-01T08:30:00-03:00"))).isTrue();
        assertThat(repository.existeSobreposicaoPorAlimentador(alimentador.getId(),
                OffsetDateTime.parse("2026-01-01T08:00:00-03:00"), OffsetDateTime.parse("2026-01-01T09:00:00-03:00"))).isFalse();
    }

    private OffsetDateTime instante(String horario) { return OffsetDateTime.parse("2026-01-01T" + horario + ":00Z"); }

    private Alimentador alimentador() {
        var sub = new Subestacao(); sub.setNome("Subestação"); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub);
        var alimentador = new Alimentador(); alimentador.setNome("Alimentador"); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        alimentador.setSubestacao(sub); em.persist(alimentador); em.flush(); return alimentador;
    }

    private FonteMedicao fonte(Alimentador alimentador) {
        var fonte = new FonteMedicao(); fonte.setIdentificador("ENERGIA-FONTE-" + (++sequencia));
        fonte.setAtivo(alimentador); fonte.setOrigem(OrigemMedicao.SIMULADA); em.persist(fonte); em.flush(); return fonte;
    }

    private MedicaoEnergiaAlimentador nova(FonteMedicao fonte) {
        var medicao = new MedicaoEnergiaAlimentador(); medicao.setFonte(fonte);
        medicao.setInicioPeriodo(INICIO); medicao.setFimPeriodo(INICIO.plusHours(1)); medicao.setRecebidoEm(INICIO.plusHours(2));
        medicao.setEnergiaInjetadaMwh(new BigDecimal("100.123456")); medicao.setPercentualPerdaReferencia(new BigDecimal("3.123456"));
        return medicao;
    }

    private void violacao(Runnable operacao, String estadoSql) {
        var erro = assertThrows(DataIntegrityViolationException.class, operacao::run);
        assertThat(erro.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
                cause -> assertThat(cause.getSQLState()).isEqualTo(estadoSql));
    }
}
