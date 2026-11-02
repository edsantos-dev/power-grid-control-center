package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class HistoricoEnergiaRepositoryTest {
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final OffsetDateTime FIM = INICIO.plusHours(2);
    @Autowired private MedicaoEnergiaAlimentadorRepository repository;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    private int sequencia;

    @ParameterizedTest
    @CsvSource({"0,7200,true", "3600,7200,true", "0,3600,true", "1,7199,true",
            "-1,3600,false", "3600,7201,false", "-1,7201,false", "-3600,0,false", "7200,10800,false"})
    void deveSelecionarSomenteIntervalosIntegralmenteContidos(int inicio, int fim, boolean contido) {
        var alimentador = alimentador();
        leitura(fonte(alimentador), INICIO.plusSeconds(inicio), INICIO.plusSeconds(fim), "100", "3.5");
        em.flush(); em.clear();
        var resultado = repository.buscarParaHistorico(alimentador.getId(), INICIO, FIM);
        assertThat(resultado).hasSize(contido ? 1 : 0);
        if (contido) {
            assertThat(resultado.getFirst().inicioPeriodo().toInstant()).isEqualTo(INICIO.plusSeconds(inicio).toInstant());
            assertThat(resultado.getFirst().fimPeriodo().toInstant()).isEqualTo(INICIO.plusSeconds(fim).toInstant());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveExcluirIntervaloQueExcedeABordaPorUmMicrossegundo(boolean excedeFim) {
        var alimentador = alimentador();
        leitura(fonte(alimentador), excedeFim ? INICIO : INICIO.minusNanos(1000),
                excedeFim ? FIM.plusNanos(1000) : FIM, "100", "3.5");
        em.flush(); em.clear();
        assertThat(repository.buscarParaHistorico(alimentador.getId(), INICIO, FIM)).isEmpty();
    }

    @Test
    void deveTrazerMultiplasFontesEmOrdemCronologicaSemMisturarAlimentadores() {
        var alimentador = alimentador(); var primeira = fonte(alimentador); var segunda = fonte(alimentador);
        leitura(segunda, INICIO.plusHours(1), FIM, "200", "5");
        leitura(primeira, INICIO, INICIO.plusHours(1), "100", "3.5");
        leitura(fonte(alimentador()), INICIO, FIM, "999", "99");
        em.flush(); em.clear();
        var resultado = repository.buscarParaHistorico(alimentador.getId(), INICIO, FIM);
        assertThat(resultado).hasSize(2);
        assertThat(resultado).extracting(p -> p.energiaInjetadaMwh().stripTrailingZeros())
                .containsExactly(new BigDecimal("100").stripTrailingZeros(), new BigDecimal("200").stripTrailingZeros());
        assertThat(resultado).extracting(p -> p.percentualPerdaReferencia().stripTrailingZeros())
                .containsExactly(new BigDecimal("3.5"), new BigDecimal("5"));
    }

    @Test
    void devePreservarValoresESnapshotSemCarregarEntidadesOuCausarNMaisUm() {
        var alimentador = alimentador(); var fonte = fonte(alimentador);
        leitura(fonte, INICIO, INICIO.plusHours(1), "100.123456", "3.123456");
        leitura(fonte, INICIO.plusHours(1), FIM, "0.000001", "99.999999");
        alimentador.setPercentualPerdaTecnica(new BigDecimal("9")); em.flush(); em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        boolean anterior = stats.isStatisticsEnabled(); stats.setStatisticsEnabled(true);
        try {
            long consultas = stats.getPrepareStatementCount(); long carregadas = stats.getEntityLoadCount();
            var resultado = repository.buscarParaHistorico(alimentador.getId(), INICIO, FIM);
            assertThat(resultado).hasSize(2);
            assertThat(resultado.getFirst().energiaInjetadaMwh()).isEqualByComparingTo("100.123456");
            assertThat(resultado.getFirst().percentualPerdaReferencia()).isEqualByComparingTo("3.123456");
            assertThat(resultado.getLast().energiaInjetadaMwh()).isEqualByComparingTo("0.000001");
            assertThat(resultado.getLast().percentualPerdaReferencia()).isEqualByComparingTo("99.999999");
            for (var ponto : resultado) {
                assertThat(ponto.inicioPeriodo()).isNotNull(); assertThat(ponto.fimPeriodo()).isNotNull();
                assertThat(ponto.energiaInjetadaMwh()).isNotNull(); assertThat(ponto.percentualPerdaReferencia()).isNotNull();
            }
            assertThat(stats.getPrepareStatementCount() - consultas).isEqualTo(1);
            assertThat(stats.getEntityLoadCount() - carregadas).isZero();
        } finally { stats.setStatisticsEnabled(anterior); }
    }

    @Test
    void deveCompararInstantesComFusosDistintosNaJanela() {
        var alimentador = alimentador(); leitura(fonte(alimentador), INICIO, FIM, "100", "3.5"); em.flush(); em.clear();
        var resultado = repository.buscarParaHistorico(alimentador.getId(),
                OffsetDateTime.parse("2025-12-31T21:00:00-03:00"), OffsetDateTime.parse("2026-01-01T04:00:00+02:00"));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.getFirst().inicioPeriodo().toInstant()).isEqualTo(INICIO.toInstant());
        assertThat(resultado.getFirst().fimPeriodo().toInstant()).isEqualTo(FIM.toInstant());
    }

    @Test
    void deveManterLeituraQueCruzaDiaQuandoContidaNaJanelaMacroSemRatear() {
        var alimentador = alimentador();
        var inicio = INICIO.plusHours(23); var fim = INICIO.plusDays(1).plusHours(1);
        leitura(fonte(alimentador), inicio, fim, "200", "3.5"); em.flush(); em.clear();
        var resultado = repository.buscarParaHistorico(alimentador.getId(), INICIO, INICIO.plusDays(2));
        assertThat(resultado).hasSize(1);
        assertThat(resultado.getFirst().inicioPeriodo().toInstant()).isEqualTo(inicio.toInstant());
        assertThat(resultado.getFirst().fimPeriodo().toInstant()).isEqualTo(fim.toInstant());
        assertThat(resultado.getFirst().energiaInjetadaMwh()).isEqualByComparingTo("200");
    }

    @Test
    void devePreservarIntervalosLegadosCoincidentesDeFontesDistintasParaValidacaoPosterior() {
        var alimentador = alimentador();
        leitura(fonte(alimentador), INICIO, FIM, "100", "3.5");
        leitura(fonte(alimentador), INICIO, FIM, "200", "5"); em.flush(); em.clear();
        var resultado = repository.buscarParaHistorico(alimentador.getId(), INICIO, FIM);
        assertThat(resultado).hasSize(2);
        assertThat(resultado.getFirst().energiaInjetadaMwh()).isEqualByComparingTo("100");
        assertThat(resultado.getLast().energiaInjetadaMwh()).isEqualByComparingTo("200");
    }

    @Test
    void deveRetornarListaVaziaSemLeiturasNoEscopo() {
        var alimentador = alimentador(); em.flush(); em.clear();
        assertThat(repository.buscarParaHistorico(alimentador.getId(), INICIO, FIM)).isEmpty();
        assertThat(repository.buscarParaHistorico(Long.MAX_VALUE, INICIO, FIM)).isEmpty();
    }

    private Alimentador alimentador() {
        var sub = new Subestacao(); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub);
        var alimentador = new Alimentador(); alimentador.setNome("Alimentador do histórico");
        alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL); alimentador.setSubestacao(sub);
        em.persist(alimentador); return alimentador;
    }

    private FonteMedicao fonte(Alimentador alimentador) {
        var fonte = new FonteMedicao(); fonte.setAtivo(alimentador); fonte.setOrigem(OrigemMedicao.SIMULADA);
        fonte.setIdentificador("HISTORICO-ENERGIA-" + (++sequencia)); em.persist(fonte); return fonte;
    }

    private void leitura(FonteMedicao fonte, OffsetDateTime inicio, OffsetDateTime fim, String energia, String percentual) {
        var medicao = new MedicaoEnergiaAlimentador(); medicao.setFonte(fonte);
        medicao.setInicioPeriodo(inicio); medicao.setFimPeriodo(fim); medicao.setRecebidoEm(fim.plusHours(1));
        medicao.setEnergiaInjetadaMwh(new BigDecimal(energia)); medicao.setPercentualPerdaReferencia(new BigDecimal(percentual));
        em.persist(medicao);
    }
}
