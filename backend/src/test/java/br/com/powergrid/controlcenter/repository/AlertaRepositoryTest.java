package br.com.powergrid.controlcenter.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.Alerta;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Criticidade;
import br.com.powergrid.controlcenter.model.EscopoAlerta;
import br.com.powergrid.controlcenter.model.SituacaoAlerta;
import br.com.powergrid.controlcenter.model.Subestacao;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class AlertaRepositoryTest {

    @Autowired private AlertaRepository repository;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(EscopoAlerta.class)
    void devePersistirERecuperarEscoposValidos(EscopoAlerta escopo) {
        Alerta alerta = novo(Criticidade.ALTA, SituacaoAlerta.ABERTO, "2026-10-25T10:00:00-03:00");
        alerta.setEscopo(escopo);
        Long ativoId = null;
        if (escopo == EscopoAlerta.ATIVO) {
            Subestacao ativo = salvarAtivo();
            alerta.setAtivo(ativo);
            ativoId = ativo.getId();
        }
        repository.saveAndFlush(alerta);
        entityManager.clear();
        Alerta salvo = repository.findById(alerta.getId()).orElseThrow();
        assertThat(salvo.getId()).isPositive();
        assertThat(salvo.getTipo()).isEqualTo(alerta.getTipo());
        assertThat(salvo.getCriticidade()).isEqualTo(Criticidade.ALTA);
        assertThat(salvo.getSituacao()).isEqualTo(SituacaoAlerta.ABERTO);
        assertThat(salvo.getEscopo()).isEqualTo(escopo);
        assertThat(salvo.getGeradoEm().toInstant()).isEqualTo(alerta.getGeradoEm().toInstant());
        if (escopo == EscopoAlerta.ATIVO) {
            assertThat(salvo.getAtivo().getId()).isEqualTo(ativoId);
            assertThat(salvo.getAtivo().getNome()).isEqualTo("Subestação de teste");
        } else {
            assertThat(salvo.getAtivo()).isNull();
        }
    }

    @Test
    void deveRejeitarEscopoAtivoSemAtivo() {
        Alerta alerta = novo(Criticidade.ALTA, SituacaoAlerta.ABERTO, "2026-10-25T13:00:00Z");
        alerta.setEscopo(EscopoAlerta.ATIVO);
        assertViolacao(alerta, "23513", "ck_alerta_escopo");
    }

    @Test
    void deveRejeitarRedeMonitoradaComAtivo() {
        Alerta alerta = novo(Criticidade.ALTA, SituacaoAlerta.ABERTO, "2026-10-25T13:00:00Z");
        alerta.setAtivo(salvarAtivo());
        assertViolacao(alerta, "23513", "ck_alerta_escopo");
    }

    @Test
    void deveRejeitarEscopoNulo() {
        Alerta alerta = novo(Criticidade.ALTA, SituacaoAlerta.ABERTO, "2026-10-25T13:00:00Z");
        alerta.setEscopo(null);
        assertViolacao(alerta, "23502", "escopo");
    }

    @Test
    void deveRejeitarAtivoInexistente() {
        Alerta alerta = novo(Criticidade.ALTA, SituacaoAlerta.ABERTO, "2026-10-25T13:00:00Z");
        alerta.setEscopo(EscopoAlerta.ATIVO);
        alerta.setAtivo(entityManager.getReference(Subestacao.class, Long.MAX_VALUE));
        assertViolacao(alerta, "23506", "fk_alerta_ativo");
    }

    @Test
    void deveOrdenarAtivosPorCriticidadeInstanteEId() {
        Alerta baixa = salvar(Criticidade.BAIXA, SituacaoAlerta.ABERTO, "2026-10-26T15:00:00Z");
        Alerta criticaAntiga = salvar(Criticidade.CRITICA, SituacaoAlerta.RECONHECIDO, "2026-10-24T13:00:00Z");
        Alerta alta = salvar(Criticidade.ALTA, SituacaoAlerta.ABERTO, "2026-10-26T14:00:00Z");
        Alerta criticaNova = salvar(Criticidade.CRITICA, SituacaoAlerta.ABERTO, "2026-10-25T10:00:00-03:00");
        Alerta media = salvar(Criticidade.MEDIA, SituacaoAlerta.RECONHECIDO, "2026-10-26T15:00:00Z");
        Alerta criticaEmpatada = salvar(Criticidade.CRITICA, SituacaoAlerta.RECONHECIDO, "2026-10-25T13:00:00Z");
        salvar(Criticidade.CRITICA, SituacaoAlerta.RESOLVIDO, "2026-10-27T13:00:00Z");
        entityManager.clear();
        assertThat(repository.buscarAtivos()).extracting(Alerta::getId).containsExactly(
                criticaEmpatada.getId(), criticaNova.getId(), criticaAntiga.getId(),
                alta.getId(), media.getId(), baixa.getId());
    }

    @ParameterizedTest
    @EnumSource(SituacaoAlerta.class)
    void deveFiltrarSituacoes(SituacaoAlerta situacao) {
        Alerta alerta = salvar(Criticidade.MEDIA, situacao, "2026-10-25T13:00:00Z");
        entityManager.clear();
        if (situacao == SituacaoAlerta.RESOLVIDO) {
            assertThat(repository.buscarAtivos()).isEmpty();
        } else {
            assertThat(repository.buscarAtivos()).extracting(Alerta::getId).containsExactly(alerta.getId());
        }
    }

    @Test
    void deveRetornarListaVaziaSemAlertas() {
        assertThat(repository.buscarAtivos()).isEmpty();
    }

    private Alerta salvar(Criticidade criticidade, SituacaoAlerta situacao, String instante) {
        return repository.saveAndFlush(novo(criticidade, situacao, instante));
    }

    private Alerta novo(Criticidade criticidade, SituacaoAlerta situacao, String instante) {
        Alerta alerta = new Alerta();
        alerta.setTipo("Falha de comunicação");
        alerta.setCriticidade(criticidade);
        alerta.setSituacao(situacao);
        alerta.setEscopo(EscopoAlerta.REDE_MONITORADA);
        alerta.setGeradoEm(OffsetDateTime.parse(instante));
        return alerta;
    }

    private Subestacao salvarAtivo() {
        Subestacao ativo = new Subestacao();
        ativo.setNome("Subestação de teste");
        ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        ativo.setCapacidadeMva(new BigDecimal("100"));
        ativo.setTensaoNominalKv(new BigDecimal("138"));
        entityManager.persist(ativo);
        entityManager.flush();
        return ativo;
    }

    private void assertViolacao(Alerta alerta, String sqlState, String constraint) {
        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(alerta));
        assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
            assertThat(cause.getSQLState()).isEqualTo(sqlState);
            assertThat(cause.getMessage()).containsIgnoringCase(constraint);
        });
    }
}
