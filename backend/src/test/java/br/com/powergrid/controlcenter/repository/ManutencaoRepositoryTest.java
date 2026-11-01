package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.EventoOperacional.Categoria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import static org.assertj.core.api.Assertions.assertThat;
import static br.com.powergrid.controlcenter.model.SituacaoEventoOperacional.*;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class ManutencaoRepositoryTest {
    @Autowired private EventoOperacionalRepository eventos;
    @Autowired private HistoricoEventoOperacionalRepository historicos;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    private Subestacao ativo;
    private Subestacao outroAtivo;
    private Usuario operador;
    private Usuario tecnico;
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-11-01T12:00:00.123456Z");

    @BeforeEach
    void preparar() {
        ativo = subestacao("Principal"); outroAtivo = subestacao("Outro");
        operador = usuario(PerfilAcesso.OPERADOR); tecnico = usuario(PerfilAcesso.TECNICO_MANUTENCAO);
    }

    @Test
    void deveIsolarAtivoECategoriaManutencao() {
        var m1 = evento(ativo, operador, "EM_ANDAMENTO", INICIO, Categoria.MANUTENCAO);
        var m2 = evento(ativo, tecnico, "ABERTO", null, Categoria.MANUTENCAO);
        evento(outroAtivo, operador, "EM_ANDAMENTO", INICIO, Categoria.MANUTENCAO);
        evento(ativo, operador, "EM_ANDAMENTO", INICIO, Categoria.FALHA_EQUIPAMENTO);
        var pagina = consultar(ativo.getId(), null, null, null, null, 0, 20);
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent()).extracting(EventoOperacional::getId).containsExactly(m1.getId(), m2.getId());
        assertThat(pagina.getContent()).allSatisfy(e -> {
            assertThat(e.getCategoria()).isEqualTo(Categoria.MANUTENCAO); assertThat(e.getAtivo().getId()).isEqualTo(ativo.getId());
        });
        assertThat(consultar(Long.MAX_VALUE, null, null, null, null, 0, 20)).isEmpty();
    }

    @Test
    void deveOrdenarPorInicioDescendenteComNulosAoFinalEDesempatePorId() {
        var nuloAntigo = evento(ativo, operador, "ABERTO", null, Categoria.MANUTENCAO);
        var antigo = evento(ativo, operador, "EM_ANDAMENTO", INICIO, Categoria.MANUTENCAO);
        var recente1 = evento(ativo, operador, "EM_ANDAMENTO", INICIO.plusHours(1), Categoria.MANUTENCAO);
        var recente2 = evento(ativo, operador, "EM_ANDAMENTO", INICIO.plusHours(1), Categoria.MANUTENCAO);
        var nuloNovo = evento(ativo, operador, "ABERTO", null, Categoria.MANUTENCAO);
        em.clear();
        var pagina = eventos.buscarManutencoesPorAtivo(ativo.getId(), null, null, null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id")));
        assertThat(pagina.getContent()).extracting(EventoOperacional::getId)
                .containsExactly(recente2.getId(), recente1.getId(), antigo.getId(), nuloNovo.getId(), nuloAntigo.getId());
    }

    @Test
    void devePaginarNoBancoComContagemConsistenteEPaginaDistante() {
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 5; i++) ids.add(evento(ativo, operador, "EM_ANDAMENTO", INICIO, Categoria.MANUTENCAO).getId());
        var primeira = consultar(ativo.getId(), null, null, null, null, 0, 2);
        var segunda = consultar(ativo.getId(), null, null, null, null, 1, 2);
        var ultima = consultar(ativo.getId(), null, null, null, null, 2, 2);
        assertThat(primeira.getTotalElements()).isEqualTo(5); assertThat(primeira.getTotalPages()).isEqualTo(3);
        assertThat(primeira.getContent()).extracting(EventoOperacional::getId).containsExactly(ids.get(4), ids.get(3));
        assertThat(segunda.getContent()).extracting(EventoOperacional::getId).containsExactly(ids.get(2), ids.get(1));
        assertThat(ultima.getContent()).extracting(EventoOperacional::getId).containsExactly(ids.get(0));
        assertThat(ultima.isLast()).isTrue();
        var distante = consultar(ativo.getId(), null, null, null, null, Integer.MAX_VALUE, 100);
        assertThat(distante.getTotalElements()).isEqualTo(5); assertThat(distante.getContent()).isEmpty();
    }

    @Test
    void deveCombinarPeriodoResponsavelESituacaoAntesDePaginar() {
        var limiteInicial = evento(ativo, operador, "EM_ANDAMENTO", INICIO, Categoria.MANUTENCAO);
        evento(ativo, tecnico, "EM_ANDAMENTO", INICIO.plusSeconds(1), Categoria.MANUTENCAO);
        var dentro = evento(ativo, operador, "EM_ANDAMENTO", INICIO.plusSeconds(2), Categoria.MANUTENCAO);
        evento(ativo, operador, "EM_ANDAMENTO", INICIO.plusSeconds(3), Categoria.MANUTENCAO);
        evento(ativo, operador, "CONCLUIDO", INICIO.plusSeconds(2), Categoria.MANUTENCAO);
        evento(ativo, operador, "ABERTO", null, Categoria.MANUTENCAO);
        evento(outroAtivo, operador, "EM_ANDAMENTO", INICIO.plusSeconds(2), Categoria.MANUTENCAO);
        evento(ativo, operador, "EM_ANDAMENTO", INICIO.plusSeconds(2), Categoria.MANOBRA);
        var inicioOutroFuso = INICIO.withOffsetSameInstant(ZoneOffset.ofHours(-3));
        var primeira = consultar(ativo.getId(), "EM_ANDAMENTO", operador.getId(), inicioOutroFuso, INICIO.plusSeconds(3), 0, 1);
        var segunda = consultar(ativo.getId(), "EM_ANDAMENTO", operador.getId(), inicioOutroFuso, INICIO.plusSeconds(3), 1, 1);
        assertThat(primeira.getTotalElements()).isEqualTo(2); assertThat(primeira.getTotalPages()).isEqualTo(2);
        assertThat(primeira.getContent()).extracting(EventoOperacional::getId).containsExactly(dentro.getId());
        assertThat(segunda.getContent()).extracting(EventoOperacional::getId).containsExactly(limiteInicial.getId());
    }

    @Test
    void deveAplicarFiltrosOpcionaisIsoladamente() {
        var iniciado = evento(ativo, operador, "EM_ANDAMENTO", INICIO, Categoria.MANUTENCAO);
        var concluido = evento(ativo, tecnico, "CONCLUIDO", INICIO.plusSeconds(1), Categoria.MANUTENCAO);
        evento(ativo, operador, "ABERTO", null, Categoria.MANUTENCAO);
        assertThat(consultar(ativo.getId(), "CONCLUIDO", null, null, null, 0, 20).getContent())
                .extracting(EventoOperacional::getId).containsExactly(concluido.getId());
        assertThat(consultar(ativo.getId(), null, tecnico.getId(), null, null, 0, 20).getContent())
                .extracting(EventoOperacional::getId).containsExactly(concluido.getId());
        assertThat(consultar(ativo.getId(), null, null, INICIO.plusSeconds(1), null, 0, 20).getContent())
                .extracting(EventoOperacional::getId).containsExactly(concluido.getId());
        assertThat(consultar(ativo.getId(), null, null, null, INICIO.plusSeconds(1), 0, 20).getContent())
                .extracting(EventoOperacional::getId).containsExactly(iniciado.getId());
        assertThat(consultar(ativo.getId(), null, Long.MAX_VALUE, null, null, 0, 20)).isEmpty();
    }

    @Test
    void periodoDeveUsarInicioEfetivoSemIncluirPlanejamentoSemInicio() {
        var planejamento = evento(ativo, operador, "ABERTO", null, Categoria.MANUTENCAO);
        planejamento.setInicioPrevisto(INICIO); planejamento.setTerminoPrevisto(INICIO.plusHours(1)); em.flush();
        var executado = evento(ativo, operador, "EM_ANDAMENTO", INICIO, Categoria.MANUTENCAO);
        assertThat(consultar(ativo.getId(), null, null, null, null, 0, 20).getTotalElements()).isEqualTo(2);
        assertThat(consultar(ativo.getId(), null, null, INICIO, INICIO.plusSeconds(1), 0, 20).getContent())
                .extracting(EventoOperacional::getId).containsExactly(executado.getId());
    }

    @Test
    void deveBuscarApenasUltimaTransicaoPorEventoUsandoInstanteEId() {
        var a = evento(ativo, operador, "CONCLUIDO", INICIO, Categoria.MANUTENCAO);
        var b = evento(ativo, tecnico, "CONCLUIDO", INICIO, Categoria.MANUTENCAO);
        var semHistorico = evento(ativo, operador, "ABERTO", null, Categoria.MANUTENCAO);
        var foraDaLista = evento(outroAtivo, operador, "EM_ANDAMENTO", INICIO, Categoria.MANUTENCAO);
        var maisRecenteA = historico(a, EM_ANDAMENTO, CONCLUIDO, INICIO.plusSeconds(60));
        var inseridoDepoisMasMaisAntigo = historico(a, ABERTO, EM_ANDAMENTO, INICIO);
        historico(b, ABERTO, EM_ANDAMENTO, INICIO);
        var desempateB = historico(b, EM_ANDAMENTO, CONCLUIDO, INICIO);
        historico(foraDaLista, ABERTO, EM_ANDAMENTO, INICIO.plusDays(1));
        em.clear();
        var ultimas = historicos.buscarUltimasTransicoes(List.of(a.getId(), b.getId(), semHistorico.getId(), a.getId()));
        assertThat(ultimas).extracting(HistoricoEventoOperacional::getId).containsExactly(maisRecenteA.getId(), desempateB.getId());
        assertThat(ultimas).extracting(h -> h.getEvento().getId()).containsExactly(a.getId(), b.getId());
        assertThat(ultimas).extracting(HistoricoEventoOperacional::getId).doesNotContain(inseridoDepoisMasMaisAntigo.getId());
        assertThat(historicos.buscarUltimasTransicoes(List.of(Long.MAX_VALUE))).isEmpty();
    }

    @Test
    void listaVaziaDeEventosNaoDeveExecutarConsultaDeHistorico() {
        em.flush(); em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics(); boolean habilitado = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long antes = stats.getPrepareStatementCount();
            assertThat(historicos.buscarUltimasTransicoes(List.of())).isEmpty();
            assertThat(stats.getPrepareStatementCount() - antes).isZero();
        } finally { stats.setStatisticsEnabled(habilitado); }
    }

    @Test
    void deveMontarPaginaEUltimasTransicoesComQuantidadeConstanteDeConsultas() {
        for (int i = 0; i < 8; i++) {
            var evento = evento(ativo, operador, "CONCLUIDO", INICIO.plusMinutes(i), Categoria.MANUTENCAO);
            historico(evento, ABERTO, EM_ANDAMENTO, INICIO.plusMinutes(i));
            historico(evento, EM_ANDAMENTO, CONCLUIDO, INICIO.plusMinutes(i).plusSeconds(30));
        }
        em.flush(); em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics(); boolean habilitado = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            long antes = stats.getPrepareStatementCount();
            var pagina = consultar(ativo.getId(), null, null, null, null, 0, 3);
            var ultimas = historicos.buscarUltimasTransicoes(pagina.getContent().stream().map(EventoOperacional::getId).toList());
            assertThat(pagina.getTotalElements()).isEqualTo(8); assertThat(ultimas).hasSize(3);
            assertThat(ultimas).allSatisfy(h -> {
                assertThat(h.getSituacaoNova()).isEqualTo(CONCLUIDO);
                assertThat(h.getUsuario().getId()).isEqualTo(operador.getId());
            });
            assertThat(stats.getPrepareStatementCount() - antes).isEqualTo(3);
        } finally { stats.setStatisticsEnabled(habilitado); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBESTACAO", "ALIMENTADOR", "TRANSFORMADOR", "LINHA"})
    void deveConsultarManutencoesPelaIdentidadeDoAtivoIndependentementeDoSubtipo(String tipo) {
        AtivoRede alvo = ativo;
        if (tipo.equals("ALIMENTADOR") || tipo.equals("TRANSFORMADOR")) {
            var alimentador = new Alimentador(); alimentador.setNome("Alimentador"); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            alimentador.setSubestacao(ativo); em.persist(alimentador); alvo = alimentador;
            if (tipo.equals("TRANSFORMADOR")) {
                var trafo = new Transformador(); trafo.setNome("Transformador"); trafo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
                trafo.setAlimentador(alimentador); trafo.setCapacidadeMva(java.math.BigDecimal.TEN); em.persist(trafo); alvo = trafo;
            }
        } else if (tipo.equals("LINHA")) {
            var linha = new LinhaTransmissao(); linha.setNome("Linha"); linha.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            linha.setSubestacaoOrigem(ativo); linha.setSubestacaoDestino(outroAtivo); em.persist(linha); alvo = linha;
        }
        var manutencao = evento(alvo, operador, "ABERTO", null, Categoria.MANUTENCAO);
        assertThat(consultar(alvo.getId(), null, null, null, null, 0, 20).getContent())
                .extracting(EventoOperacional::getId).containsExactly(manutencao.getId());
    }

    private Page<EventoOperacional> consultar(Long ativoId, String situacao, Long responsavel,
            OffsetDateTime inicio, OffsetDateTime fim, int page, int size) {
        return eventos.buscarManutencoesPorAtivo(ativoId, situacao, responsavel, inicio, fim, PageRequest.of(page, size));
    }

    private EventoOperacional evento(AtivoRede alvo, Usuario responsavel, String situacao, OffsetDateTime inicio, Categoria categoria) {
        var evento = new EventoOperacional(); evento.setAtivo(alvo); evento.setCategoria(categoria);
        evento.setRegistradorUsuario(operador); evento.setResponsavelUsuario(responsavel); evento.setSituacao(situacao);
        evento.setDescricao("Intervenção de teste"); evento.setInicioEfetivo(inicio);
        if (situacao.equals("CONCLUIDO") && inicio != null) evento.setTerminoEfetivo(inicio.plusSeconds(60));
        em.persist(evento); em.flush(); return evento;
    }

    private HistoricoEventoOperacional historico(EventoOperacional evento, SituacaoEventoOperacional anterior,
            SituacaoEventoOperacional nova, OffsetDateTime alteradoEm) {
        var h = new HistoricoEventoOperacional(); h.setEvento(evento); h.setUsuario(operador);
        h.setSituacaoAnterior(anterior); h.setSituacaoNova(nova); h.setAlteradoEm(alteradoEm); h.setComentario("Registro da intervenção");
        return historicos.saveAndFlush(h);
    }

    private Subestacao subestacao(String nome) {
        var sub = new Subestacao(); sub.setNome(nome); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub); em.flush(); return sub;
    }

    private Usuario usuario(PerfilAcesso perfil) {
        return em.createQuery("select u from Usuario u where u.perfil = :perfil", Usuario.class).setParameter("perfil", perfil).getSingleResult();
    }
}
