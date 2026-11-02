package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
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
class MedicaoConsumoMedidorRepositoryTest {
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-01-02T10:00:00Z");
    @Autowired private MedicaoConsumoMedidorRepository repository;
    @Autowired private EntityManager em;
    private int sequencia;

    @ParameterizedTest
    @ValueSource(strings = {"0", "100.123456", "9999999999999.999999"})
    void devePersistirConsumoComPrecisaoESnapshotsObrigatorios(String consumo) {
        var c = cenario(); var leitura = nova(c); leitura.setConsumoKwh(new BigDecimal(consumo));
        repository.saveAndFlush(leitura); em.clear();
        var salva = repository.findById(leitura.getId()).orElseThrow();
        assertThat(salva.getId()).isPositive(); assertThat(salva.getConsumoKwh()).isEqualByComparingTo(consumo);
        assertThat(salva.getInicioPeriodo()).isEqualTo(INICIO); assertThat(salva.getFimPeriodo()).isEqualTo(INICIO.plusHours(1));
        assertThat(salva.getRecebidoEm()).isEqualTo(INICIO.plusHours(2));
        assertThat(Hibernate.isInitialized(salva.getFonte())).isFalse();
        assertThat(salva.getFonte().getId()).isEqualTo(c.fonte().getId());
        assertThat(salva.getFonte().getAtivo().getId()).isEqualTo(c.medidor().getId());
        conferirSnapshots(salva, c);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fonte", "inicio", "fim", "recebido", "consumo", "consumidor", "transformador", "alimentador", "subestacao"})
    void deveRejeitarColunasObrigatoriasNulas(String campo) {
        var leitura = nova(cenario());
        switch (campo) {
            case "fonte" -> leitura.setFonte(null);
            case "inicio" -> leitura.setInicioPeriodo(null);
            case "fim" -> leitura.setFimPeriodo(null);
            case "recebido" -> leitura.setRecebidoEm(null);
            case "consumo" -> leitura.setConsumoKwh(null);
            case "consumidor" -> leitura.setConsumidorId(null);
            case "transformador" -> leitura.setTransformadorId(null);
            case "alimentador" -> leitura.setAlimentadorId(null);
            case "subestacao" -> leitura.setSubestacaoId(null);
        }
        violacao(leitura, "23502");
    }

    @ParameterizedTest
    @ValueSource(strings = {"fonte", "consumidor", "transformador", "alimentador", "subestacao"})
    void deveRejeitarReferenciasInexistentes(String campo) {
        var leitura = nova(cenario());
        switch (campo) {
            case "fonte" -> leitura.setFonte(em.getReference(FonteMedicao.class, Long.MAX_VALUE));
            case "consumidor" -> leitura.setConsumidorId(Long.MAX_VALUE);
            case "transformador" -> leitura.setTransformadorId(Long.MAX_VALUE);
            case "alimentador" -> leitura.setAlimentadorId(Long.MAX_VALUE);
            case "subestacao" -> leitura.setSubestacaoId(Long.MAX_VALUE);
        }
        violacao(leitura, "23506");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-0.000001"})
    void deveRejeitarConsumoNegativo(String consumo) {
        var leitura = nova(cenario()); leitura.setConsumoKwh(new BigDecimal(consumo)); violacao(leitura, "23513");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void deveRejeitarPeriodoVazioOuInvertido(int horas) {
        var leitura = nova(cenario()); leitura.setFimPeriodo(INICIO.plusHours(horas)); violacao(leitura, "23513");
    }

    @Test
    void deveRejeitarPeriodoIdenticoNaMesmaFonte() {
        var c = cenario(); repository.saveAndFlush(nova(c)); violacao(nova(c), "23505");
    }

    @Test
    void devePreservarSnapshotsAposMudancaDosVinculosCadastrais() {
        var original = cenario(); var outra = cenario(); var leitura = repository.saveAndFlush(nova(original));
        original.medidor().setConsumidor(outra.consumidor()); original.medidor().setTransformador(outra.transformador());
        original.transformador().setAlimentador(outra.alimentador());
        em.flush(); em.clear();
        var salva = repository.findById(leitura.getId()).orElseThrow(); conferirSnapshots(salva, original);
        assertThat(salva.getFonte().getAtivo().getId()).isEqualTo(original.medidor().getId());
        assertThat(em.find(MedidorInteligente.class, original.medidor().getId()).getConsumidor().getId()).isEqualTo(outra.consumidor().getId());
        assertThat(em.find(Transformador.class, original.transformador().getId()).getAlimentador().getId()).isEqualTo(outra.alimentador().getId());
    }

    @Test
    void deveManterFonteESnapshotsImutaveisNoMapeamentoJpa() {
        var original = cenario(); var outra = cenario(); var leitura = repository.saveAndFlush(nova(original));
        leitura.setFonte(outra.fonte()); leitura.setConsumidorId(outra.consumidor().getId());
        leitura.setTransformadorId(outra.transformador().getId()); leitura.setAlimentadorId(outra.alimentador().getId());
        leitura.setSubestacaoId(outra.subestacao().getId()); repository.saveAndFlush(leitura); em.clear();
        var salva = repository.findById(leitura.getId()).orElseThrow(); conferirSnapshots(salva, original);
        assertThat(salva.getFonte().getId()).isEqualTo(original.fonte().getId());
    }

    @ParameterizedTest
    @CsvSource({"09:00,10:00,false", "11:00,12:00,false", "08:00,09:00,false", "12:00,13:00,false",
            "10:00,11:00,true", "09:30,10:30,true", "10:30,11:30,true", "10:15,10:45,true", "09:00,12:00,true"})
    void deveDetectarSobreposicaoSemBloquearPeriodosAdjacentes(String inicio, String fim, boolean esperado) {
        var c = cenario(); repository.saveAndFlush(nova(c)); em.clear();
        assertThat(repository.existeSobreposicaoPorMedidor(c.medidor().getId(), instante(inicio), instante(fim))).isEqualTo(esperado);
    }

    @Test
    void deveDetectarSobreposicaoEntreFontesDoMesmoMedidor() {
        var c = cenario(); repository.saveAndFlush(nova(c));
        var segunda = nova(c); segunda.setFonte(fonte(c.medidor()));
        segunda.setInicioPeriodo(INICIO.plusHours(2)); segunda.setFimPeriodo(INICIO.plusHours(3)); repository.saveAndFlush(segunda); em.clear();
        assertThat(repository.existeSobreposicaoPorMedidor(c.medidor().getId(), INICIO.plusMinutes(15), INICIO.plusMinutes(45))).isTrue();
        assertThat(repository.existeSobreposicaoPorMedidor(c.medidor().getId(), INICIO.plusHours(2), INICIO.plusHours(3))).isTrue();
        assertThat(repository.existeSobreposicaoPorMedidor(c.medidor().getId(), INICIO.plusHours(1), INICIO.plusHours(2))).isFalse();
    }

    @Test
    void deveIsolarMedidoresNaConsultaDeSobreposicao() {
        var a = cenario(); var b = cenario(); repository.saveAndFlush(nova(b)); em.clear();
        assertThat(repository.existeSobreposicaoPorMedidor(a.medidor().getId(), INICIO, INICIO.plusHours(1))).isFalse();
        assertThat(repository.existeSobreposicaoPorMedidor(b.medidor().getId(), INICIO, INICIO.plusHours(1))).isTrue();
        assertThat(repository.existeSobreposicaoPorMedidor(Long.MAX_VALUE, INICIO, INICIO.plusHours(1))).isFalse();
    }

    @Test
    void deveCompararFusosEIntersecoesDeUmMicrossegundo() {
        var c = cenario(); repository.saveAndFlush(nova(c)); em.clear();
        assertThat(repository.existeSobreposicaoPorMedidor(c.medidor().getId(),
                OffsetDateTime.parse("2026-01-02T07:30:00-03:00"), OffsetDateTime.parse("2026-01-02T08:30:00-03:00"))).isTrue();
        assertThat(repository.existeSobreposicaoPorMedidor(c.medidor().getId(), INICIO.plusHours(1), INICIO.plusHours(2))).isFalse();
        assertThat(repository.existeSobreposicaoPorMedidor(c.medidor().getId(), INICIO.plusHours(1).minusNanos(1000), INICIO.plusHours(2))).isTrue();
    }

    private void conferirSnapshots(MedicaoConsumoMedidor leitura, Cenario c) {
        assertThat(leitura.getConsumidorId()).isEqualTo(c.consumidor().getId());
        assertThat(leitura.getTransformadorId()).isEqualTo(c.transformador().getId());
        assertThat(leitura.getAlimentadorId()).isEqualTo(c.alimentador().getId());
        assertThat(leitura.getSubestacaoId()).isEqualTo(c.subestacao().getId());
    }

    private Cenario cenario() {
        int numero = ++sequencia;
        var sub = new Subestacao(); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub);
        var alimentador = new Alimentador(); alimentador.setSubestacao(sub); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(alimentador);
        var transformador = new Transformador(); transformador.setAlimentador(alimentador);
        transformador.setCapacidadeMva(BigDecimal.TEN); transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(transformador);
        var consumidor = new Consumidor(); consumidor.setNomeRazaoSocial("Titular do consumo " + numero);
        consumidor.setTipoPessoa(Consumidor.TipoPessoa.PF); consumidor.setDocumento(String.format("%011d", numero)); em.persist(consumidor);
        var medidor = new MedidorInteligente(); medidor.setNumeroSerie("CONSUMO-MEDIDOR-" + numero); medidor.setNome("Medidor de consumo");
        medidor.setEstadoOperacional(EstadoOperacional.OPERACIONAL); medidor.setConsumidor(consumidor); medidor.setTransformador(transformador);
        medidor.setDataInstalacao(LocalDate.of(2026, 1, 1)); em.persist(medidor);
        var fonte = fonte(medidor); em.flush();
        return new Cenario(sub, alimentador, transformador, consumidor, medidor, fonte);
    }

    private FonteMedicao fonte(MedidorInteligente medidor) {
        var fonte = new FonteMedicao(); fonte.setAtivo(medidor); fonte.setOrigem(OrigemMedicao.SIMULADA);
        fonte.setIdentificador("CONSUMO-FONTE-" + (++sequencia)); em.persist(fonte); return fonte;
    }

    private MedicaoConsumoMedidor nova(Cenario c) {
        var leitura = new MedicaoConsumoMedidor(); leitura.setFonte(c.fonte()); leitura.setInicioPeriodo(INICIO);
        leitura.setFimPeriodo(INICIO.plusHours(1)); leitura.setRecebidoEm(INICIO.plusHours(2)); leitura.setConsumoKwh(new BigDecimal("100.123456"));
        leitura.setConsumidorId(c.consumidor().getId()); leitura.setTransformadorId(c.transformador().getId());
        leitura.setAlimentadorId(c.alimentador().getId()); leitura.setSubestacaoId(c.subestacao().getId()); return leitura;
    }

    private OffsetDateTime instante(String horario) { return OffsetDateTime.parse("2026-01-02T" + horario + ":00Z"); }

    private void violacao(MedicaoConsumoMedidor leitura, String sqlState) {
        var erro = assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(leitura));
        assertThat(erro.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
                cause -> assertThat(cause.getSQLState()).isEqualTo(sqlState));
    }

    private record Cenario(Subestacao subestacao, Alimentador alimentador, Transformador transformador,
            Consumidor consumidor, MedidorInteligente medidor, FonteMedicao fonte) { }
}
