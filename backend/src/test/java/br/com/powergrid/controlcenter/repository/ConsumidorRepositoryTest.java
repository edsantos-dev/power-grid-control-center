package br.com.powergrid.controlcenter.repository;

import java.sql.SQLException;
import br.com.powergrid.controlcenter.model.Consumidor;
import br.com.powergrid.controlcenter.model.Consumidor.TipoPessoa;
import jakarta.persistence.EntityManager;
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
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.check_nullability=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class ConsumidorRepositoryTest {

    @Autowired private ConsumidorRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @CsvSource({"PF, 01234567890", "PJ, 00123456000199", "PJ, 00ABCDEF000199"})
    void devePersistirTitularEConsultarExistenciaDoDocumento(TipoPessoa tipoPessoa, String documento) {
        assertThat(repository.existsByDocumento(documento)).isFalse();
        Consumidor consumidor = novo(tipoPessoa, documento);
        repository.saveAndFlush(consumidor);
        entityManager.clear();

        Consumidor salvo = repository.findById(consumidor.getId()).orElseThrow();
        assertThat(salvo.getId()).isPositive();
        assertThat(salvo).isNotSameAs(consumidor);
        assertThat(salvo.getNomeRazaoSocial()).isEqualTo(consumidor.getNomeRazaoSocial());
        assertThat(salvo.getTipoPessoa()).isEqualTo(tipoPessoa);
        assertThat(salvo.getDocumento()).isEqualTo(documento);
        assertThat(salvo.getEnderecoCorrespondencia()).isEqualTo(consumidor.getEnderecoCorrespondencia());
        assertThat(repository.existsByDocumento(documento)).isTrue();
        assertThat(repository.existsByDocumento("DOCUMENTO_INEXISTENTE")).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT tipo_pessoa FROM consumidor WHERE id = ?", String.class, salvo.getId()))
                .isEqualTo(tipoPessoa.name());
    }

    @Test
    void deveRejeitarDocumentoDuplicadoNoBanco() {
        repository.saveAndFlush(novo(TipoPessoa.PF, "01234567890"));
        Consumidor duplicado = novo(TipoPessoa.PF, "01234567890");
        duplicado.setNomeRazaoSocial("Outro titular");
        assertViolacao(() -> repository.saveAndFlush(duplicado), "23505", "uq_consumidor_documento");
    }

    @ParameterizedTest
    @ValueSource(strings = {"XX", "pf", ""})
    void deveRejeitarTipoPessoaInvalidoDiretamenteNoBanco(String tipoPessoa) {
        assertViolacao(() -> jdbcTemplate.update(
                "INSERT INTO consumidor (nome_razao_social, tipo_pessoa, documento) VALUES (?, ?, ?)",
                "Titular inválido", tipoPessoa, "01234567890"), "23513", "ck_consumidor_tipo_pessoa");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tipo_pessoa", "documento"})
    void deveRejeitarCampoObrigatorioNulo(String campo) {
        Consumidor consumidor = novo(TipoPessoa.PF, "01234567890");
        if (campo.equals("tipo_pessoa")) consumidor.setTipoPessoa(null);
        else consumidor.setDocumento(null);
        assertViolacao(() -> repository.saveAndFlush(consumidor), "23502", campo);
    }

    @Test
    void devePermitirEnderecoOpcionalSemMedidores() {
        Consumidor consumidor = novo(TipoPessoa.PJ, "00ABCDEF000199");
        consumidor.setEnderecoCorrespondencia(null);
        repository.saveAndFlush(consumidor);
        entityManager.clear();
        assertThat(repository.findById(consumidor.getId()).orElseThrow().getEnderecoCorrespondencia()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM medidor_inteligente WHERE consumidor_id = ?", Long.class, consumidor.getId()))
                .isZero();
    }

    private Consumidor novo(TipoPessoa tipoPessoa, String documento) {
        Consumidor consumidor = new Consumidor();
        consumidor.setNomeRazaoSocial("Titular de teste");
        consumidor.setTipoPessoa(tipoPessoa);
        consumidor.setDocumento(documento);
        consumidor.setEnderecoCorrespondencia("Rua das Flores, 100, Curitiba - PR");
        return consumidor;
    }

    private void assertViolacao(Runnable operacao, String sqlState, String constraint) {
        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class, operacao::run);
        assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(sqlState);
            assertThat(cause.getMessage()).containsIgnoringCase(constraint);
        });
    }
}
