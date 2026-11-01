package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.check_nullability=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class UsinaGeradoraRepositoryTest {
    @Autowired private UsinaGeradoraRepository repository;
    @Autowired private SubestacaoRepository subestacoes;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(TipoGeracao.class)
    void devePersistirHerancaJoinedERecuperarDadosESubestacaoLazy(TipoGeracao tipo) {
        var subestacao = subestacao("Conexão");
        var usina = nova(tipo, subestacao);
        repository.saveAndFlush(usina);
        em.clear();
        assertThat(usina.getId()).isPositive().isNotEqualTo(subestacao.getId());
        assertThat(jdbc.queryForObject("select id from ativo_rede where id = ?", Long.class, usina.getId())).isEqualTo(usina.getId());
        assertThat(jdbc.queryForObject("select tipo_geracao from usina_geradora where id = ?", String.class, usina.getId())).isEqualTo(tipo.name());
        assertThat(jdbc.queryForObject("select subestacao_id from usina_geradora where id = ?", Long.class, usina.getId())).isEqualTo(subestacao.getId());
        var salva = repository.findById(usina.getId()).orElseThrow();
        assertThat(salva).isNotSameAs(usina);
        assertThat(salva.getNome()).isEqualTo(usina.getNome());
        assertThat(salva.getLatitude()).isEqualByComparingTo(usina.getLatitude());
        assertThat(salva.getLongitude()).isEqualByComparingTo(usina.getLongitude());
        assertThat(salva.getEstadoOperacional()).isEqualTo(EstadoOperacional.OPERACIONAL);
        assertThat(salva.getDataInstalacao()).isEqualTo(usina.getDataInstalacao());
        assertThat(salva.getTipoGeracao()).isEqualTo(tipo);
        assertThat(salva.getCapacidadeMaximaMw()).isEqualByComparingTo(usina.getCapacidadeMaximaMw());
        assertThat(Hibernate.isInitialized(salva.getSubestacao())).isFalse();
        assertThat(salva.getSubestacao().getId()).isEqualTo(subestacao.getId());
        assertThat(salva.getSubestacao().getNome()).isEqualTo("Conexão");
        em.clear();
        assertThat(em.find(AtivoRede.class, usina.getId())).isInstanceOf(UsinaGeradora.class);
    }

    @ParameterizedTest
    @EnumSource(EstadoOperacional.class)
    void deveAceitarEstadosOperacionaisExistentesEDataOpcional(EstadoOperacional estado) {
        var usina = nova(TipoGeracao.SOLAR, subestacao("Conexão"));
        usina.setEstadoOperacional(estado); usina.setDataInstalacao(null);
        repository.saveAndFlush(usina); em.clear();
        var salva = repository.findById(usina.getId()).orElseThrow();
        assertThat(salva.getEstadoOperacional()).isEqualTo(estado);
        assertThat(salva.getDataInstalacao()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tipo", "capacidade", "subestacao", "estado"})
    void deveRejeitarCamposObrigatoriosNulosNoBanco(String campo) {
        var usina = nova(TipoGeracao.EOLICA, subestacao("Conexão"));
        if (campo.equals("tipo")) usina.setTipoGeracao(null);
        if (campo.equals("capacidade")) usina.setCapacidadeMaximaMw(null);
        if (campo.equals("subestacao")) usina.setSubestacao(null);
        if (campo.equals("estado")) usina.setEstadoOperacional(null);
        violacao(() -> repository.saveAndFlush(usina), "23502");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.000001", "-100"})
    void deveRejeitarCapacidadeNaoPositivaNoBanco(String valor) {
        var usina = nova(TipoGeracao.TERMICA, subestacao("Conexão"));
        usina.setCapacidadeMaximaMw(new BigDecimal(valor));
        violacao(() -> repository.saveAndFlush(usina), "23513");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALIDO", "solar", ""})
    void deveRejeitarTipoForaDoDominioDiretamenteNoBanco(String tipo) {
        var usina = repository.saveAndFlush(nova(TipoGeracao.SOLAR, subestacao("Conexão")));
        violacao(() -> jdbc.update("update usina_geradora set tipo_geracao = ? where id = ?", tipo, usina.getId()), "23513");
    }

    @Test
    void deveRejeitarSubestacaoInexistenteNoBanco() {
        var usina = nova(TipoGeracao.SOLAR, em.getReference(Subestacao.class, Long.MAX_VALUE));
        violacao(() -> repository.saveAndFlush(usina), "23506");
    }

    @Test
    void deveRejeitarIdDeAtivoQueNaoSejaSubestacao() {
        var sub = subestacao("Conexão");
        var alimentador = new Alimentador(); alimentador.setNome("Alimentador");
        alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL); alimentador.setSubestacao(sub);
        em.persist(alimentador); em.flush();
        var usina = repository.saveAndFlush(nova(TipoGeracao.SOLAR, sub));
        violacao(() -> jdbc.update("update usina_geradora set subestacao_id = ? where id = ?", alimentador.getId(), usina.getId()), "23506");
    }

    @Test
    void deveConsultarPorTipoSubestacaoECombinacaoDeFiltrosComPaginacao() {
        var norte = subestacao("Norte"); var sul = subestacao("Sul");
        var a = repository.saveAndFlush(nova(TipoGeracao.SOLAR, norte));
        var b = repository.saveAndFlush(nova(TipoGeracao.SOLAR, norte));
        var c = repository.saveAndFlush(nova(TipoGeracao.EOLICA, norte));
        var d = repository.saveAndFlush(nova(TipoGeracao.SOLAR, sul));
        var primeira = repository.buscarFiltradas(TipoGeracao.SOLAR, norte.getId(), pagina(0, 1));
        var segunda = repository.buscarFiltradas(TipoGeracao.SOLAR, norte.getId(), pagina(1, 1));
        assertThat(primeira.getTotalElements()).isEqualTo(2); assertThat(primeira.getTotalPages()).isEqualTo(2);
        assertThat(primeira.getContent()).extracting(UsinaGeradora::getId).containsExactly(a.getId());
        assertThat(segunda.getContent()).extracting(UsinaGeradora::getId).containsExactly(b.getId());
        assertThat(repository.buscarFiltradas(null, norte.getId(), pagina(0, 20)).getContent())
                .extracting(UsinaGeradora::getId).containsExactly(a.getId(), b.getId(), c.getId());
        assertThat(repository.buscarFiltradas(TipoGeracao.SOLAR, null, pagina(0, 20)).getContent())
                .extracting(UsinaGeradora::getId).containsExactly(a.getId(), b.getId(), d.getId());
        assertThat(repository.buscarFiltradas(null, null, pagina(0, 20)).getTotalElements()).isEqualTo(4);
        assertThat(repository.buscarFiltradas(TipoGeracao.EOLICA, sul.getId(), pagina(0, 20))).isEmpty();
        assertThat(repository.buscarFiltradas(null, Long.MAX_VALUE, pagina(0, 20))).isEmpty();
        var distante = repository.buscarFiltradas(null, null, pagina(Integer.MAX_VALUE, 100));
        assertThat(distante.getContent()).isEmpty(); assertThat(distante.getTotalElements()).isEqualTo(4);
    }

    private PageRequest pagina(int page, int size) { return PageRequest.of(page, size, Sort.by("id")); }

    private Subestacao subestacao(String nome) {
        var sub = new Subestacao(); sub.setNome(nome); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        return subestacoes.saveAndFlush(sub);
    }

    private UsinaGeradora nova(TipoGeracao tipo, Subestacao subestacao) {
        var usina = new UsinaGeradora(); usina.setNome("Usina de teste");
        usina.setLatitude(new BigDecimal("-25.4284001")); usina.setLongitude(new BigDecimal("-49.2733001"));
        usina.setEstadoOperacional(EstadoOperacional.OPERACIONAL); usina.setDataInstalacao(LocalDate.of(2099, 1, 1));
        usina.setTipoGeracao(tipo); usina.setCapacidadeMaximaMw(new BigDecimal("150.123456"));
        usina.setSubestacao(subestacao); return usina;
    }

    private void violacao(Runnable operacao, String sqlState) {
        var erro = assertThrows(DataIntegrityViolationException.class, operacao::run);
        assertThat(erro.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
                cause -> assertThat(cause.getSQLState()).isEqualTo(sqlState));
    }
}
