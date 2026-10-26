package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.LinhaTransmissao;
import br.com.powergrid.controlcenter.model.Subestacao;
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
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.check_nullability=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class LinhaTransmissaoRepositoryTest {
    @Autowired private LinhaTransmissaoRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @EnumSource(EstadoOperacional.class)
    void devePersistirERecuperarLinhaEntreSubestacoesDistintas(EstadoOperacional estado) {
        Subestacao origem = salvarSubestacao("Origem");
        Subestacao destino = salvarSubestacao("Destino");
        LinhaTransmissao linha = nova(origem, destino);
        linha.setEstadoOperacional(estado);
        repository.saveAndFlush(linha);
        entityManager.clear();
        LinhaTransmissao salvo = repository.findById(linha.getId()).orElseThrow();
        assertThat(salvo.getId()).isPositive();
        assertThat(salvo.getNome()).isEqualTo(linha.getNome());
        assertThat(salvo.getEstadoOperacional()).isEqualTo(estado);
        assertThat(salvo.getLatitude()).isEqualByComparingTo(linha.getLatitude());
        assertThat(salvo.getLongitude()).isEqualByComparingTo(linha.getLongitude());
        assertThat(salvo.getDataInstalacao()).isEqualTo(linha.getDataInstalacao());
        assertThat(salvo.getExtensaoKm()).isEqualByComparingTo("123.123456");
        assertThat(salvo.getTensaoOperacaoKv()).isEqualByComparingTo("138.123456");
        assertThat(Hibernate.isInitialized(salvo.getSubestacaoOrigem())).isFalse();
        assertThat(Hibernate.isInitialized(salvo.getSubestacaoDestino())).isFalse();
        assertThat(salvo.getSubestacaoOrigem().getId()).isEqualTo(origem.getId());
        assertThat(salvo.getSubestacaoDestino().getId()).isEqualTo(destino.getId());
        assertThat(salvo.getSubestacaoOrigem().getNome()).isEqualTo("Origem");
        assertThat(salvo.getSubestacaoDestino().getNome()).isEqualTo("Destino");
        assertThat(jdbcTemplate.queryForObject("SELECT id FROM ativo_rede WHERE id = ?", Long.class, salvo.getId()))
                .isEqualTo(salvo.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT id FROM linha_transmissao WHERE id = ?", Long.class, salvo.getId()))
                .isEqualTo(salvo.getId());
        entityManager.clear();
        assertThat(entityManager.find(AtivoRede.class, salvo.getId())).isInstanceOf(LinhaTransmissao.class);
    }

    @Test
    void deveRejeitarMesmaSubestacaoNasDuasExtremidades() {
        Subestacao subestacao = salvarSubestacao("Única");
        assertViolacao(nova(subestacao, subestacao), "23513", "ck_linha_subestacoes_distintas");
    }

    @ParameterizedTest
    @ValueSource(strings = {"origem", "destino"})
    void deveRejeitarSubestacaoInexistente(String extremidade) {
        Subestacao real = salvarSubestacao("Existente");
        Subestacao inexistente = entityManager.getReference(Subestacao.class, Long.MAX_VALUE);
        LinhaTransmissao linha = extremidade.equals("origem") ? nova(inexistente, real) : nova(real, inexistente);
        assertViolacao(linha, "23506", "fk_linha_subestacao_" + extremidade);
    }

    @ParameterizedTest
    @ValueSource(strings = {"extensao_km", "tensao_operacao_kv"})
    void deveRejeitarExcessoDeDigitosInteiros(String campo) {
        LinhaTransmissao linha = nova(salvarSubestacao("Origem"), salvarSubestacao("Destino"));
        BigDecimal excedido = new BigDecimal("10000000000000");
        if (campo.equals("extensao_km")) linha.setExtensaoKm(excedido);
        else linha.setTensaoOperacaoKv(excedido);
        assertViolacao(linha, "22001", campo);
    }

    @Test
    void deveAceitarLimiteDeTrezeInteirosESeisDecimais() {
        LinhaTransmissao linha = nova(salvarSubestacao("Origem"), salvarSubestacao("Destino"));
        BigDecimal limite = new BigDecimal("9999999999999.999999");
        linha.setExtensaoKm(limite);
        linha.setTensaoOperacaoKv(limite);
        repository.saveAndFlush(linha);
        entityManager.clear();
        LinhaTransmissao salvo = repository.findById(linha.getId()).orElseThrow();
        assertThat(salvo.getExtensaoKm()).isEqualByComparingTo(limite);
        assertThat(salvo.getTensaoOperacaoKv()).isEqualByComparingTo(limite);
    }

    @ParameterizedTest
    @ValueSource(strings = {"origem", "destino"})
    void deveRejeitarExtremidadeNula(String extremidade) {
        Subestacao real = salvarSubestacao("Existente");
        LinhaTransmissao linha = extremidade.equals("origem") ? nova(null, real) : nova(real, null);
        assertViolacao(linha, "23502", "subestacao_" + extremidade + "_id");
    }

    private Subestacao salvarSubestacao(String nome) {
        Subestacao subestacao = new Subestacao();
        subestacao.setNome(nome);
        subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        subestacao.setTensaoNominalKv(new BigDecimal("138"));
        subestacao.setCapacidadeMva(new BigDecimal("100"));
        entityManager.persist(subestacao);
        entityManager.flush();
        return subestacao;
    }

    private LinhaTransmissao nova(Subestacao origem, Subestacao destino) {
        LinhaTransmissao linha = new LinhaTransmissao();
        linha.setNome("Linha Central");
        linha.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        linha.setLatitude(new BigDecimal("-25.4284001"));
        linha.setLongitude(new BigDecimal("-49.2733001"));
        linha.setDataInstalacao(LocalDate.of(2026, 1, 1));
        linha.setExtensaoKm(new BigDecimal("123.123456"));
        linha.setTensaoOperacaoKv(new BigDecimal("138.123456"));
        linha.setSubestacaoOrigem(origem);
        linha.setSubestacaoDestino(destino);
        return linha;
    }

    private void assertViolacao(LinhaTransmissao linha, String sqlState, String constraint) {
        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(linha));
        assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(sqlState);
            assertThat(cause.getMessage()).containsIgnoringCase(constraint);
        });
    }
}
