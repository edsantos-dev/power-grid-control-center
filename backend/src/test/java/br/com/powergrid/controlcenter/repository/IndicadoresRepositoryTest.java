package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.EventoOperacional.Categoria;
import br.com.powergrid.controlcenter.repository.projection.InventarioIndicadoresProjection;
import jakarta.persistence.EntityManager;
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
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(IndicadoresRepository.class)
class IndicadoresRepositoryTest {
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-10-01T00:00:00Z");
    private static final OffsetDateTime FIM = INICIO.plusHours(1);
    @Autowired private IndicadoresRepository repository;
    @Autowired private EntityManager em;

    @Test
    void deveAgruparInventarioAtualPorTipoEEstadoSemIncluirMedidores() {
        var c = topologia();
        var medidor = new MedidorInteligente(); preencher(medidor); medidor.setNumeroSerie("KPI-ESTOQUE"); em.persist(medidor);
        em.flush(); em.clear();
        var inventario = repository.consultarInventario(null, null, null);
        assertThat(inventario).extracting(InventarioIndicadoresProjection::tipoAtivo)
                .containsExactly(TipoAtivoIndicador.ALIMENTADOR, TipoAtivoIndicador.LINHA,
                        TipoAtivoIndicador.SUBESTACAO, TipoAtivoIndicador.TRANSFORMADOR,
                        TipoAtivoIndicador.TRANSFORMADOR, TipoAtivoIndicador.USINA);
        assertThat(inventario).filteredOn(p -> p.tipoAtivo() == TipoAtivoIndicador.SUBESTACAO)
                .extracting(InventarioIndicadoresProjection::quantidade).containsExactly(2L);
        assertThat(inventario.stream().mapToLong(InventarioIndicadoresProjection::quantidade).sum()).isEqualTo(8);
        assertThat(repository.consultarInventario(c.transformadorA().getId(), null, null))
                .containsExactly(new InventarioIndicadoresProjection(TipoAtivoIndicador.TRANSFORMADOR, EstadoOperacional.FALHA, 1));
    }

    @Test
    void deveAplicarFiltrosCombinadosEDerivarSubestacaoDoTransformador() {
        var c = topologia(); em.flush(); em.clear();
        assertThat(repository.consultarInventario(null, Set.of(TipoAtivoIndicador.TRANSFORMADOR), c.subA().getId()))
                .containsExactly(new InventarioIndicadoresProjection(TipoAtivoIndicador.TRANSFORMADOR, EstadoOperacional.FALHA, 1));
        assertThat(repository.consultarInventario(c.transformadorB().getId(), null, c.subA().getId())).isEmpty();
        assertThat(repository.consultarInventario(c.usina().getId(), Set.of(TipoAtivoIndicador.LINHA), null)).isEmpty();
        assertThat(repository.consultarInventario(null, Set.of(), null)).isEmpty();
        assertThat(repository.consultarInventario(Long.MAX_VALUE, null, null)).isEmpty();
        assertThat(repository.consultarInventario(null, null, Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void deveIncluirLinhaPorQualquerExtremidadeSemDuplicarNoInventario() {
        var c = topologia(); var mesmaSub = linha(c.subA(), sub()); em.flush(); em.clear();
        assertThat(repository.consultarInventario(null, Set.of(TipoAtivoIndicador.LINHA), c.subA().getId()))
                .extracting(InventarioIndicadoresProjection::quantidade).containsExactly(1L, 1L);
        assertThat(repository.consultarInventario(c.linha().getId(), null, c.subB().getId()))
                .extracting(InventarioIndicadoresProjection::quantidade).containsExactly(1L);
        assertThat(repository.consultarInventario(mesmaSub.getId(), null, c.subB().getId())).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"100, 700, 600", "-100, 600, 600", "3000, 4000, 600", "-100, 4000, 3600",
            "-100, null, 3600", "3000, null, 600", "0, 3600, 3600"})
    void deveRecortarInterrupcaoNasFronteirasDaJanela(int inicio, String fim, String segundos) {
        var ativo = sub(); evento(ativo, inicio, fim.equals("null") ? null : Integer.valueOf(fim)); em.flush(); em.clear();
        var resultado = repository.consultarTempoInterrompido(ativo.getId(), null, null, INICIO, FIM);
        assertThat(resultado.totalSegundos()).isEqualByComparingTo(segundos);
        assertThat(resultado.ativosComInterrupcao()).isEqualTo(1);
        assertThat(resultado.intervalosUnificados()).isEqualTo(1);
    }

    @Test
    void deveFundirIntervalosAninhadosSobrepostosDuplicadosEAdjacentes() {
        var ativo = sub(); evento(ativo, 100, 1000); evento(ativo, 200, 300); evento(ativo, 900, 1200);
        evento(ativo, 100, 1000); evento(ativo, 1200, 1500); evento(ativo, 1800, 1900); em.flush(); em.clear();
        var resultado = repository.consultarTempoInterrompido(null, null, null, INICIO, FIM);
        assertThat(resultado.totalSegundos()).isEqualByComparingTo("1500");
        assertThat(resultado.intervalosUnificados()).isEqualTo(2);
        assertThat(resultado.ativosComInterrupcao()).isEqualTo(1);
    }

    @Test
    void deveSomarTemposDeAtivosDistintosSemFundirEquipamentos() {
        evento(sub(), 100, 200); evento(sub(), 100, 200); em.flush(); em.clear();
        var resultado = repository.consultarTempoInterrompido(null, null, null, INICIO, FIM);
        assertThat(resultado.totalSegundos()).isEqualByComparingTo("200");
        assertThat(resultado.ativosComInterrupcao()).isEqualTo(2);
        assertThat(resultado.intervalosUnificados()).isEqualTo(2);
    }

    @Test
    void deveExcluirEventosApenasTangentesAJanelaEDuracaoNula() {
        var ativo = sub(); evento(ativo, -100, 0); evento(ativo, 3600, 4000); evento(ativo, 0, 0);
        em.flush(); em.clear();
        var resultado = repository.consultarTempoInterrompido(null, null, null, INICIO, FIM);
        assertThat(resultado.totalSegundos()).isEqualByComparingTo("0");
        assertThat(resultado.ativosComInterrupcao()).isZero(); assertThat(resultado.intervalosUnificados()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Categoria.class, names = "INTERRUPCAO_FORNECIMENTO", mode = EnumSource.Mode.EXCLUDE)
    void deveIgnorarOutrasCategoriasMesmoComDatasEfetivas(Categoria categoria) {
        var evento = evento(sub(), 0, 600); evento.setCategoria(categoria); em.flush(); em.clear();
        assertThat(repository.consultarTempoInterrompido(null, null, null, INICIO, FIM).totalSegundos())
                .isEqualByComparingTo("0");
        assertThat(repository.consultarRestabelecimento(null, null, null, INICIO, FIM).interrupcoesConcluidas()).isZero();
    }

    @Test
    void deveFiltrarInterrupcoesPorTopologiaTipoEAtivoSemPropagarParaDependentes() {
        var c = topologia(); evento(c.transformadorA(), 0, 600); evento(c.transformadorB(), 0, 900);
        evento(c.linha(), 0, 100); em.flush(); em.clear();
        var tipos = Set.of(TipoAtivoIndicador.TRANSFORMADOR);
        assertThat(repository.consultarTempoInterrompido(null, tipos, c.subA().getId(), INICIO, FIM).totalSegundos())
                .isEqualByComparingTo("600");
        assertThat(repository.consultarTempoInterrompido(c.transformadorB().getId(), tipos, c.subA().getId(), INICIO, FIM).totalSegundos())
                .isEqualByComparingTo("0");
        assertThat(repository.consultarRestabelecimento(null, tipos, c.subA().getId(), INICIO, FIM).tempoMedioSegundos())
                .isEqualByComparingTo("600");
        assertThat(repository.consultarTempoInterrompido(null, Set.of(TipoAtivoIndicador.LINHA), c.subB().getId(), INICIO, FIM).totalSegundos())
                .isEqualByComparingTo("100");
    }

    @Test
    void deveCalcularMediaPelaDuracaoFisicaCompletaDosRestabelecimentosDoPeriodo() {
        var ativo = sub(); evento(ativo, -3600, 600); evento(ativo, 0, 1200);
        evento(ativo, 0, 3600); evento(ativo, -200, -100); evento(ativo, 0, null);
        var naoConcluido = evento(ativo, 0, 1800); naoConcluido.setSituacao("EM_ANDAMENTO");
        em.flush(); em.clear();
        var resultado = repository.consultarRestabelecimento(null, null, null, INICIO, FIM);
        assertThat(resultado.interrupcoesConcluidas()).isEqualTo(2);
        assertThat(resultado.duracaoTotalSegundos()).isEqualByComparingTo("5400");
        assertThat(resultado.tempoMedioSegundos()).isEqualByComparingTo("2700");
    }

    @Test
    void deveIncluirRestabelecimentoNoInicioEExcluirNoFimDaJanela() {
        var ativo = sub(); evento(ativo, -100, 0); evento(ativo, 3500, 3600); em.flush(); em.clear();
        var resultado = repository.consultarRestabelecimento(null, null, null, INICIO, FIM);
        assertThat(resultado.interrupcoesConcluidas()).isEqualTo(1);
        assertThat(resultado.tempoMedioSegundos()).isEqualByComparingTo("100");
    }

    @Test
    void devePreservarMicrossegundosEFusosSemTruncarDuracoes() {
        var ativo = sub(); var evento = evento(ativo, 0, 1);
        evento.setInicioEfetivo(OffsetDateTime.parse("2026-09-30T21:00:00.000001-03:00"));
        evento.setTerminoEfetivo(OffsetDateTime.parse("2026-10-01T02:00:00.000003+02:00"));
        em.flush(); em.clear();
        assertThat(repository.consultarTempoInterrompido(null, null, null, INICIO, FIM).totalSegundos())
                .isEqualByComparingTo("0.000002");
        assertThat(repository.consultarRestabelecimento(null, null, null, INICIO, FIM).tempoMedioSegundos())
                .isEqualByComparingTo("0.000002");
    }

    @Test
    void deveRetornarMediaNulaSemRestabelecimentosEIgnorarLegadoSemInicio() {
        var evento = evento(sub(), 0, 600); evento.setInicioEfetivo(null); em.flush(); em.clear();
        var media = repository.consultarRestabelecimento(null, null, null, INICIO, FIM);
        assertThat(media.interrupcoesConcluidas()).isZero(); assertThat(media.tempoMedioSegundos()).isNull();
        assertThat(media.duracaoTotalSegundos()).isEqualByComparingTo("0");
        assertThat(repository.consultarTempoInterrompido(null, null, null, INICIO, FIM).totalSegundos())
                .isEqualByComparingTo("0");
    }

    @Test
    void deveTratarEscopoVazioSemInterrupcoesElegiveis() {
        evento(sub(), 0, 600); em.flush(); em.clear();
        assertThat(repository.consultarTempoInterrompido(null, Set.of(), null, INICIO, FIM).totalSegundos()).isEqualByComparingTo("0");
        assertThat(repository.consultarRestabelecimento(Long.MAX_VALUE, null, null, INICIO, FIM).tempoMedioSegundos()).isNull();
    }

    @Test
    void deveRejeitarPeriodoAusenteIgualOuInvertido() {
        assertThatIllegalArgumentException().isThrownBy(() -> repository.consultarTempoInterrompido(null, null, null, INICIO, INICIO));
        assertThatIllegalArgumentException().isThrownBy(() -> repository.consultarRestabelecimento(null, null, null, FIM, INICIO));
        assertThatIllegalArgumentException().isThrownBy(() -> repository.consultarRestabelecimento(null, null, null, null, FIM));
    }

    @Test
    void deveRetornarTotaisVaziosSemAtivos() {
        assertThat(repository.consultarInventario(null, null, null)).isEmpty();
        var tempo = repository.consultarTempoInterrompido(null, null, null, INICIO, FIM);
        assertThat(tempo.totalSegundos()).isEqualByComparingTo("0");
        assertThat(tempo.ativosComInterrupcao()).isZero();
        assertThat(repository.consultarRestabelecimento(null, null, null, INICIO, FIM).tempoMedioSegundos()).isNull();
    }

    @Test
    void deveExcluirInterrupcoesDeMedidoresDoEscopoDeEquipamentos() {
        var medidor = new MedidorInteligente(); preencher(medidor); medidor.setNumeroSerie("KPI-IGNORADO"); em.persist(medidor);
        evento(medidor, 0, 600); em.flush(); em.clear();
        assertThat(repository.consultarTempoInterrompido(null, null, null, INICIO, FIM).totalSegundos()).isEqualByComparingTo("0");
        assertThat(repository.consultarRestabelecimento(null, null, null, INICIO, FIM).interrupcoesConcluidas()).isZero();
    }

    private Cenario topologia() {
        var a = sub(); var b = sub(); var alA = alimentador(a); var alB = alimentador(b);
        var tA = transformador(alA); tA.setEstadoOperacional(EstadoOperacional.FALHA);
        var tB = transformador(alB); var linha = linha(a, b); linha.setEstadoOperacional(EstadoOperacional.MANUTENCAO);
        var usina = new UsinaGeradora(); preencher(usina); usina.setSubestacao(a);
        usina.setTipoGeracao(TipoGeracao.SOLAR); usina.setCapacidadeMaximaMw(BigDecimal.TEN); em.persist(usina);
        return new Cenario(a, b, tA, tB, linha, usina);
    }

    private Subestacao sub() {
        var s = new Subestacao(); preencher(s); em.persist(s); return s;
    }

    private Alimentador alimentador(Subestacao sub) {
        var a = new Alimentador(); preencher(a); a.setSubestacao(sub); em.persist(a); return a;
    }

    private Transformador transformador(Alimentador alimentador) {
        var t = new Transformador(); preencher(t); t.setAlimentador(alimentador); t.setCapacidadeMva(BigDecimal.TEN); em.persist(t); return t;
    }

    private LinhaTransmissao linha(Subestacao origem, Subestacao destino) {
        var l = new LinhaTransmissao(); preencher(l); l.setSubestacaoOrigem(origem); l.setSubestacaoDestino(destino); em.persist(l); return l;
    }

    private EventoOperacional evento(AtivoRede ativo, int inicio, Integer fim) {
        var usuario = em.createQuery("select u from Usuario u where u.email = :email", Usuario.class)
                .setParameter("email", "operador@powergrid.local").getSingleResult();
        var e = new EventoOperacional(); e.setAtivo(ativo); e.setCategoria(Categoria.INTERRUPCAO_FORNECIMENTO);
        e.setRegistradorUsuario(usuario); e.setResponsavelUsuario(usuario);
        e.setInicioEfetivo(INICIO.plusSeconds(inicio)); e.setTerminoEfetivo(fim == null ? null : INICIO.plusSeconds(fim));
        e.setSituacao(fim == null ? "ABERTO" : "CONCLUIDO"); em.persist(e); return e;
    }

    private void preencher(AtivoRede ativo) {
        ativo.setNome("Ativo de indicadores"); ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
    }

    private record Cenario(Subestacao subA, Subestacao subB, Transformador transformadorA,
            Transformador transformadorB, LinhaTransmissao linha, UsinaGeradora usina) { }
}
