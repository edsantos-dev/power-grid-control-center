package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.projection.ConsumoMedidoHistoricoProjection;
import br.com.powergrid.controlcenter.support.ConsumoMedidorFixture;
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
class HistoricoConsumoMedidoRepositoryTest {
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-01-02T10:00:00Z");
    private static final OffsetDateTime FIM = INICIO.plusHours(2);
    @Autowired private MedicaoConsumoMedidorRepository repository;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;

    @ParameterizedTest
    @CsvSource({"-3600,0,false", "7200,10800,false", "-1,1,true", "7199,7201,true", "-1,7201,true",
            "0,7200,true", "3600,7200,true", "-7200,-3600,false", "10800,14400,false"})
    void deveUsarIntersecaoSemiabertaSemTrazerIntervalosApenasAdjacentes(int inicio, int fim, boolean esperado) {
        var d = ConsumoMedidorFixture.criar(em, false);
        leitura(d, d.fonteId(), INICIO.plusSeconds(inicio), INICIO.plusSeconds(fim), "150.123456"); em.flush(); em.clear();
        var resultado = repository.buscarParaHistorico(d.alimentadorId(), INICIO, FIM);
        assertThat(resultado).hasSize(esperado ? 1 : 0);
        if (esperado) {
            assertThat(resultado.getFirst().medidorId()).isEqualTo(d.medidorId());
            assertThat(resultado.getFirst().inicioPeriodo().toInstant()).isEqualTo(INICIO.plusSeconds(inicio).toInstant());
            assertThat(resultado.getFirst().fimPeriodo().toInstant()).isEqualTo(INICIO.plusSeconds(fim).toInstant());
            assertThat(resultado.getFirst().consumoKwh()).isEqualByComparingTo("150.123456");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void devePreservarIntersecoesParciaisDeUmMicrossegundoSemRecortarAProjecao(boolean bordaFinal) {
        var d = ConsumoMedidorFixture.criar(em, false);
        var inicio = bordaFinal ? FIM.minusNanos(1000) : INICIO.minusSeconds(1);
        var fim = bordaFinal ? FIM.plusSeconds(1) : INICIO.plusNanos(1000);
        leitura(d, d.fonteId(), inicio, fim, "0.000001"); em.flush(); em.clear();
        var resultado = repository.buscarParaHistorico(d.alimentadorId(), INICIO, FIM);
        assertThat(resultado).hasSize(1);
        assertThat(resultado.getFirst().inicioPeriodo().toInstant()).isEqualTo(inicio.toInstant());
        assertThat(resultado.getFirst().fimPeriodo().toInstant()).isEqualTo(fim.toInstant());
        assertThat(resultado.getFirst().consumoKwh()).isEqualByComparingTo("0.000001");
    }

    @Test
    void deveFiltrarPeloSnapshotMesmoAposMudancaDoMedidorParaOutraTopologia() {
        var original = ConsumoMedidorFixture.criar(em, false); var atual = ConsumoMedidorFixture.criar(em, false);
        leitura(original, original.fonteId(), INICIO, INICIO.plusHours(1), "10");
        var medidor = em.find(MedidorInteligente.class, original.medidorId());
        medidor.setConsumidor(em.find(Consumidor.class, atual.consumidorId()));
        medidor.setTransformador(em.find(Transformador.class, atual.transformadorId()));
        leitura(atual, original.fonteId(), INICIO.plusHours(1), FIM, "20"); em.flush(); em.clear();
        var antigo = repository.buscarParaHistorico(original.alimentadorId(), INICIO, FIM);
        var novo = repository.buscarParaHistorico(atual.alimentadorId(), INICIO, FIM);
        assertThat(antigo).hasSize(1); assertThat(novo).hasSize(1);
        assertThat(antigo.getFirst().medidorId()).isEqualTo(original.medidorId());
        assertThat(novo.getFirst().medidorId()).isEqualTo(original.medidorId());
        assertThat(antigo.getFirst().consumoKwh()).isEqualByComparingTo("10");
        assertThat(novo.getFirst().consumoKwh()).isEqualByComparingTo("20");
    }

    @Test
    void deveManterHistoricoQuandoTransformadorMudaDeAlimentador() {
        var original = ConsumoMedidorFixture.criar(em, false); var atual = ConsumoMedidorFixture.criar(em, false);
        leitura(original, original.fonteId(), INICIO, FIM, "30");
        em.find(Transformador.class, original.transformadorId()).setAlimentador(em.find(Alimentador.class, atual.alimentadorId()));
        em.flush(); em.clear();
        assertThat(repository.buscarParaHistorico(original.alimentadorId(), INICIO, FIM)).hasSize(1);
        assertThat(repository.buscarParaHistorico(atual.alimentadorId(), INICIO, FIM)).isEmpty();
    }

    @Test
    void deveRetornarCadaLeituraUmaVezComMultiplosMedidoresEFontesSemNMaisUm() {
        var a = ConsumoMedidorFixture.criar(em, false); var b = ConsumoMedidorFixture.criar(em, false);
        em.find(Transformador.class, b.transformadorId()).setAlimentador(em.find(Alimentador.class, a.alimentadorId()));
        var bHistorico = new ConsumoMedidorFixture.Dados(a.subestacaoId(), a.alimentadorId(), b.transformadorId(),
                b.consumidorId(), b.medidorId(), b.fonteId(), b.segundaFonteId(), null);
        leitura(bHistorico, b.segundaFonteId(), INICIO.plusHours(1), FIM, "40");
        leitura(a, a.segundaFonteId(), INICIO.plusHours(1), FIM, "20");
        leitura(bHistorico, b.fonteId(), INICIO, INICIO.plusHours(1), "30");
        leitura(a, a.fonteId(), INICIO, INICIO.plusHours(1), "10");
        var fora = ConsumoMedidorFixture.criar(em, false); leitura(fora, fora.fonteId(), INICIO, FIM, "999");
        em.flush(); em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        boolean anterior = stats.isStatisticsEnabled(); stats.setStatisticsEnabled(true);
        try {
            long consultas = stats.getPrepareStatementCount(), entidades = stats.getEntityLoadCount();
            var resultado = repository.buscarParaHistorico(a.alimentadorId(), INICIO, FIM);
            assertThat(resultado).hasSize(4);
            assertThat(resultado).extracting(ConsumoMedidoHistoricoProjection::medidorId)
                    .containsExactly(a.medidorId(), a.medidorId(), b.medidorId(), b.medidorId());
            assertThat(resultado).extracting(p -> p.consumoKwh().stripTrailingZeros()).containsExactly(
                    new BigDecimal("1E+1"), new BigDecimal("2E+1"), new BigDecimal("3E+1"), new BigDecimal("4E+1"));
            for (var leitura : resultado) { assertThat(leitura.inicioPeriodo()).isNotNull(); assertThat(leitura.fimPeriodo()).isNotNull(); }
            assertThat(stats.getPrepareStatementCount() - consultas).isEqualTo(1);
            assertThat(stats.getEntityLoadCount() - entidades).isZero();
        } finally { stats.setStatisticsEnabled(anterior); }
    }

    @Test
    void devePreservarLeiturasCoincidentesDeFontesDistintasParaVerificacaoDeCobertura() {
        var d = ConsumoMedidorFixture.criar(em, false);
        leitura(d, d.fonteId(), INICIO, FIM, "10"); leitura(d, d.segundaFonteId(), INICIO, FIM, "10"); em.flush(); em.clear();
        var resultado = repository.buscarParaHistorico(d.alimentadorId(), INICIO, FIM);
        assertThat(resultado).hasSize(2);
        assertThat(resultado).extracting(ConsumoMedidoHistoricoProjection::medidorId).containsOnly(d.medidorId());
        assertThat(resultado.getFirst().consumoKwh()).isEqualByComparingTo("10");
        assertThat(resultado.getLast().consumoKwh()).isEqualByComparingTo("10");
    }

    @Test
    void deveCompararJanelaComOffsetsDiferentesPeloInstante() {
        var d = ConsumoMedidorFixture.criar(em, false); leitura(d, d.fonteId(), INICIO, FIM, "10"); em.flush(); em.clear();
        var resultado = repository.buscarParaHistorico(d.alimentadorId(), OffsetDateTime.parse("2026-01-02T07:00:00-03:00"),
                OffsetDateTime.parse("2026-01-02T14:00:00+02:00"));
        assertThat(resultado).hasSize(1); assertThat(resultado.getFirst().medidorId()).isEqualTo(d.medidorId());
    }

    @Test
    void deveRetornarListaVaziaSemMedicoesOuAlimentadorInexistente() {
        var d = ConsumoMedidorFixture.criar(em, false); em.flush(); em.clear();
        assertThat(repository.buscarParaHistorico(d.alimentadorId(), INICIO, FIM)).isEmpty();
        assertThat(repository.buscarParaHistorico(Long.MAX_VALUE, INICIO, FIM)).isEmpty();
    }

    private void leitura(ConsumoMedidorFixture.Dados snapshot, Long fonteId, OffsetDateTime inicio, OffsetDateTime fim, String consumo) {
        var m = new MedicaoConsumoMedidor(); m.setFonte(em.getReference(FonteMedicao.class, fonteId));
        m.setInicioPeriodo(inicio); m.setFimPeriodo(fim); m.setRecebidoEm(FIM.plusHours(1)); m.setConsumoKwh(new BigDecimal(consumo));
        m.setConsumidorId(snapshot.consumidorId()); m.setTransformadorId(snapshot.transformadorId());
        m.setAlimentadorId(snapshot.alimentadorId()); m.setSubestacaoId(snapshot.subestacaoId()); em.persist(m);
    }
}
