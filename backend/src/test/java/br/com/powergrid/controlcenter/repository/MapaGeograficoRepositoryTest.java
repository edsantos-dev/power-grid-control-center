package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.util.Set;
import br.com.powergrid.controlcenter.geografia.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class MapaGeograficoRepositoryTest {
    private static final BoundingBox CAIXA = new BoundingBox(n("0"), n("10"), n("0"), n("10"));
    private static final Set<EstadoOperacional> ESTADOS = Set.of(EstadoOperacional.values());
    private static final Set<TipoAtivoMapa> TIPOS = Set.of(TipoAtivoMapa.values());
    @Autowired private AtivoRedeRepository ativos;
    @Autowired private LinhaTransmissaoRepository linhas;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;

    @Test
    void deveProjetarTresTiposPontuaisSemIncluirOutrosSubtipos() {
        var sub = sub("0", "0");
        var usina = new UsinaGeradora(); usina.setSubestacao(sub); usina.setTipoGeracao(TipoGeracao.SOLAR); usina.setCapacidadeMaximaMw(n("10"));
        coordenadas(usina, "10", "10"); em.persist(usina);
        var alimentador = alimentador(sub);
        var transformador = new Transformador(); transformador.setAlimentador(alimentador); transformador.setCapacidadeMva(n("5"));
        coordenadas(transformador, "5", "5"); em.persist(transformador);
        var medidor = new MedidorInteligente(); medidor.setNumeroSerie("MAPA-ESTOQUE"); coordenadas(medidor, "5", "5"); em.persist(medidor);
        linha(sub, sub("20", "20")); em.flush(); em.clear();
        var resultado = ativos.buscarPontos(CAIXA, TIPOS, ESTADOS, 0, Pageable.unpaged());
        assertThat(resultado).extracting(AtivoPontualMapaProjection::ativoId).containsExactly(sub.getId(), usina.getId(), transformador.getId());
        assertThat(resultado).extracting(AtivoPontualMapaProjection::classeAtivo).containsExactly(Subestacao.class, UsinaGeradora.class, Transformador.class);
        assertThat(resultado.get(0).coordenada()).isEqualTo(new CoordenadaGeografica(n("0.0000000"), n("0.0000000")));
    }

    @Test
    void deveFiltrarTipoEstadoEAreaNaMesmaConsulta() {
        var sub = sub("5", "5"); sub.setEstadoOperacional(EstadoOperacional.MANUTENCAO);
        var outra = sub("6", "6"); outra.setEstadoOperacional(EstadoOperacional.FALHA);
        sub("7", "7"); var fora = sub("10.0000001", "5"); fora.setEstadoOperacional(EstadoOperacional.FALHA); em.flush(); em.clear();
        var resultado = ativos.buscarPontos(CAIXA, Set.of(TipoAtivoMapa.SUBESTACAO), Set.of(EstadoOperacional.FALHA, EstadoOperacional.MANUTENCAO), 0, Pageable.unpaged());
        assertThat(resultado).extracting(AtivoPontualMapaProjection::ativoId).containsExactly(sub.getId(), outra.getId());
        assertThat(ativos.buscarPontos(CAIXA, Set.of(TipoAtivoMapa.USINA), ESTADOS, 0, Pageable.unpaged())).isEmpty();
    }

    @Test
    void deveOmitirPontosSemCoordenadasSemInventarOrigemZero() {
        sub(null, "5"); sub("5", null); sub(null, null);
        var zero = sub("0", "0"); em.flush(); em.clear();
        assertThat(ativos.buscarPontos(CAIXA, Set.of(TipoAtivoMapa.SUBESTACAO), ESTADOS, 0, Pageable.unpaged()))
                .extracting(AtivoPontualMapaProjection::ativoId).containsExactly(zero.getId());
    }

    @Test
    void devePermitirAvancoPorIdSemDescartarProjecoesElegiveis() {
        var a = sub("1", "1"); var b = sub("2", "2"); em.flush(); em.clear();
        assertThat(ativos.buscarPontos(CAIXA, TIPOS, ESTADOS, 0, PageRequest.of(0, 1)))
                .extracting(AtivoPontualMapaProjection::ativoId).containsExactly(a.getId());
        assertThat(ativos.buscarPontos(CAIXA, TIPOS, ESTADOS, a.getId(), PageRequest.of(0, 1)))
                .extracting(AtivoPontualMapaProjection::ativoId).containsExactly(b.getId());
    }

    @Test
    void deveTratarFiltrosVaziosSemGerarInVazio() {
        assertThat(ativos.buscarPontos(CAIXA, Set.of(), ESTADOS, 0, Pageable.unpaged())).isEmpty();
        assertThat(ativos.buscarPontos(CAIXA, Set.of(TipoAtivoMapa.LINHA), ESTADOS, 0, Pageable.unpaged())).isEmpty();
        assertThat(ativos.buscarPontos(CAIXA, TIPOS, Set.of(), 0, Pageable.unpaged())).isEmpty();
        assertThat(linhas.buscarCandidatas(CAIXA, Set.of(), 0, Pageable.unpaged())).isEmpty();
    }

    @Test
    void deveTrazerLinhasCruzandoCaixaComAmbasExtremidadesFora() {
        var l = linha(sub("5", "-5"), sub("5", "15")); l.setLatitude(n("-50")); l.setLongitude(n("-50"));
        var fora = linha(sub("20", "20"), sub("30", "30")); em.flush(); em.clear();
        var resultado = linhas.buscarCandidatas(CAIXA, ESTADOS, 0, Pageable.unpaged());
        assertThat(resultado).extracting(LinhaMapaProjection::ativoId).containsExactly(l.getId());
        var projecao = resultado.getFirst();
        assertThat(CAIXA.contem(projecao.origem())).isFalse(); assertThat(CAIXA.contem(projecao.destino())).isFalse();
        assertThat(IntersecaoSegmentoRetangulo.intersecta(CAIXA, projecao.origem(), projecao.destino())).isTrue();
        assertThat(projecao.subestacaoOrigemId()).isEqualTo(l.getSubestacaoOrigem().getId());
        assertThat(projecao.subestacaoDestinoId()).isEqualTo(l.getSubestacaoDestino().getId());
    }

    @Test
    void deveManterCandidataDeEnvelopeQueTesteExatoRejeita() {
        var l = linha(sub("-5", "9"), sub("9", "20")); em.flush(); em.clear();
        var resultado = linhas.buscarCandidatas(CAIXA, ESTADOS, 0, Pageable.unpaged());
        assertThat(resultado).extracting(LinhaMapaProjection::ativoId).containsExactly(l.getId());
        assertThat(IntersecaoSegmentoRetangulo.intersecta(CAIXA, resultado.getFirst().origem(), resultado.getFirst().destino())).isFalse();
    }

    @Test
    void deveOmitirLinhasSemDuasCoordenadasValidasOuComExtremidadesCoincidentes() {
        linha(sub(null, "5"), sub("10", "10")); linha(sub("0", "0"), sub("5", null));
        linha(sub("5", "5"), sub("5", "5"));
        var valida = linha(sub("0", "0"), sub("10", "10")); em.flush(); em.clear();
        assertThat(linhas.buscarCandidatas(CAIXA, ESTADOS, 0, Pageable.unpaged()))
                .extracting(LinhaMapaProjection::ativoId).containsExactly(valida.getId());
    }

    @Test
    void deveUsarEstadoDaLinhaEIncluirTangenciaDaBorda() {
        var origem = sub("0", "-5"); origem.setEstadoOperacional(EstadoOperacional.FALHA);
        var l = linha(origem, sub("0", "15"));
        var falha = linha(sub("5", "-5"), sub("5", "15")); falha.setEstadoOperacional(EstadoOperacional.FALHA); em.flush(); em.clear();
        assertThat(linhas.buscarCandidatas(CAIXA, Set.of(EstadoOperacional.OPERACIONAL), 0, Pageable.unpaged()))
                .extracting(LinhaMapaProjection::ativoId).containsExactly(l.getId());
        assertThat(linhas.buscarCandidatas(CAIXA, Set.of(EstadoOperacional.FALHA), 0, Pageable.unpaged()))
                .extracting(LinhaMapaProjection::ativoId).containsExactly(falha.getId());
    }

    @Test
    void deveProjetarSemConsultasExtrasAoAcessarGeometriaENome() {
        sub("5", "5"); linha(sub("0", "0"), sub("10", "10")); linha(sub("5", "-5"), sub("5", "15"));
        em.flush(); em.clear(); var stats = emf.unwrap(SessionFactory.class).getStatistics();
        boolean anterior = stats.isStatisticsEnabled(); stats.setStatisticsEnabled(true);
        try {
            long antes = stats.getPrepareStatementCount();
            var pontos = ativos.buscarPontos(CAIXA, TIPOS, ESTADOS, 0, Pageable.unpaged());
            for (var p : pontos) { assertThat(p.coordenada()).isNotNull(); assertThat(p.nome()).isNotBlank(); }
            assertThat(stats.getPrepareStatementCount() - antes).isEqualTo(1);
            antes = stats.getPrepareStatementCount();
            var candidatas = linhas.buscarCandidatas(CAIXA, ESTADOS, 0, Pageable.unpaged());
            for (var l : candidatas) { assertThat(l.origem()).isNotNull(); assertThat(l.destino()).isNotNull(); }
            assertThat(stats.getPrepareStatementCount() - antes).isEqualTo(1);
        } finally { stats.setStatisticsEnabled(anterior); }
    }

    @Test
    void deveAvancarCandidatasPorIdMesmoQuandoPrimeiraNaoIntersecta() {
        var falsa = linha(sub("-5", "9"), sub("9", "20"));
        var verdadeira = linha(sub("5", "-5"), sub("5", "15")); em.flush(); em.clear();
        assertThat(linhas.buscarCandidatas(CAIXA, ESTADOS, 0, PageRequest.of(0, 1)))
                .extracting(LinhaMapaProjection::ativoId).containsExactly(falsa.getId());
        var seguinte = linhas.buscarCandidatas(CAIXA, ESTADOS, falsa.getId(), PageRequest.of(0, 1));
        assertThat(seguinte).extracting(LinhaMapaProjection::ativoId).containsExactly(verdadeira.getId());
        assertThat(IntersecaoSegmentoRetangulo.intersecta(CAIXA, seguinte.getFirst().origem(), seguinte.getFirst().destino())).isTrue();
    }

    private Subestacao sub(String latitude, String longitude) {
        var s = new Subestacao(); coordenadas(s, latitude, longitude); em.persist(s); return s;
    }
    private Alimentador alimentador(Subestacao s) {
        var a = new Alimentador(); a.setSubestacao(s); coordenadas(a, "5", "5"); em.persist(a); return a;
    }
    private LinhaTransmissao linha(Subestacao origem, Subestacao destino) {
        var l = new LinhaTransmissao(); l.setNome("Conexão simplificada"); l.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        l.setSubestacaoOrigem(origem); l.setSubestacaoDestino(destino); em.persist(l); return l;
    }
    private void coordenadas(AtivoRede a, String latitude, String longitude) {
        a.setNome("Ativo geográfico"); a.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        a.setLatitude(latitude == null ? null : n(latitude)); a.setLongitude(longitude == null ? null : n(longitude));
    }
    private static BigDecimal n(String valor) { return new BigDecimal(valor); }
}
