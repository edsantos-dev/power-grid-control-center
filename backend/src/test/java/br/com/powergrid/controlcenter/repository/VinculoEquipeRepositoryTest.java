package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.*;
import jakarta.persistence.EntityManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.check_nullability=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class VinculoEquipeRepositoryTest {
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-01-01T10:00:00Z");
    @Autowired private VinculoEquipeRepository repository;
    @Autowired private EquipeManutencaoRepository equipes;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePersistirVinculoParaQualquerPerfilSemCarregarAssociacoes(PerfilAcesso perfil) {
        EquipeManutencao equipe = equipe("Equipe Norte");
        Usuario usuario = usuario(perfil);
        VinculoEquipe vinculo = repository.saveAndFlush(novo(equipe, usuario));
        entityManager.clear();

        VinculoEquipe salvo = repository.findById(vinculo.getId()).orElseThrow();
        var persistence = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(persistence.isLoaded(salvo, "equipe")).isFalse();
        assertThat(persistence.isLoaded(salvo, "usuario")).isFalse();
        assertThat(salvo.getEquipe().getId()).isEqualTo(equipe.getId());
        assertThat(salvo.getUsuario().getId()).isEqualTo(usuario.getId());
        assertThat(salvo.getInicioVigencia()).isEqualTo(INICIO);
        assertThat(salvo.getFimVigencia()).isNull();
        assertThat(repository.existsByEquipeIdAndUsuarioIdAndFimVigenciaIsNull(equipe.getId(), usuario.getId())).isTrue();
    }

    @Test
    void deveEncerrarVinculoPreservandoHistoricoEPermitirNovoPeriodo() {
        EquipeManutencao equipe = equipe("Equipe Norte");
        Usuario usuario = usuario(PerfilAcesso.OPERADOR);
        VinculoEquipe anterior = repository.saveAndFlush(novo(equipe, usuario));
        anterior.setFimVigencia(INICIO.plusDays(1));
        repository.saveAndFlush(anterior);
        entityManager.clear();
        assertThat(repository.findById(anterior.getId()).orElseThrow().getFimVigencia()).isEqualTo(INICIO.plusDays(1));
        assertThat(repository.findByEquipeIdAndFimVigenciaIsNullOrderByIdAsc(equipe.getId())).isEmpty();
        assertThat(repository.existsByEquipeIdAndUsuarioIdAndFimVigenciaIsNull(equipe.getId(), usuario.getId())).isFalse();

        VinculoEquipe atual = novo(equipe, usuario);
        atual.setInicioVigencia(INICIO.plusDays(2));
        repository.saveAndFlush(atual);
        assertThat(repository.findByEquipeIdAndFimVigenciaIsNullOrderByIdAsc(equipe.getId()))
                .extracting(VinculoEquipe::getId).containsExactly(atual.getId());
        assertThat(repository.findById(anterior.getId())).isPresent();
    }

    @Test
    void devePermitirUsuarioEmMultiplasEquipesEIsolarVinculosAtivosPorEquipe() {
        EquipeManutencao norte = equipe("Norte");
        EquipeManutencao sul = equipe("Sul");
        Usuario usuario = usuario(PerfilAcesso.TECNICO_MANUTENCAO);
        VinculoEquipe primeiro = repository.saveAndFlush(novo(norte, usuario));
        VinculoEquipe segundo = repository.saveAndFlush(novo(sul, usuario));
        VinculoEquipe encerrado = novo(norte, usuario(PerfilAcesso.GERENTE_OPERACOES));
        encerrado.setFimVigencia(INICIO.plusHours(1));
        repository.saveAndFlush(encerrado);
        entityManager.clear();
        assertThat(repository.findByEquipeIdAndFimVigenciaIsNullOrderByIdAsc(norte.getId()))
                .extracting(VinculoEquipe::getId).containsExactly(primeiro.getId());
        assertThat(repository.findByEquipeIdAndFimVigenciaIsNullOrderByIdAsc(sul.getId()))
                .extracting(VinculoEquipe::getId).containsExactly(segundo.getId());
        assertThat(repository.existsByEquipeIdAndUsuarioIdAndFimVigenciaIsNull(norte.getId(), usuario.getId())).isTrue();
        assertThat(repository.existsByEquipeIdAndUsuarioIdAndFimVigenciaIsNull(sul.getId(), usuario.getId())).isTrue();
        assertThat(repository.existsByEquipeIdAndUsuarioIdAndFimVigenciaIsNull(norte.getId(), encerrado.getUsuario().getId())).isFalse();
    }

    @Test
    void devePaginarApenasIntegrantesComVinculoAberto() {
        EquipeManutencao equipe = equipe("Norte");
        VinculoEquipe primeiro = repository.saveAndFlush(novo(equipe, usuario(PerfilAcesso.OPERADOR)));
        VinculoEquipe segundo = repository.saveAndFlush(novo(equipe, usuario(PerfilAcesso.TECNICO_MANUTENCAO)));
        VinculoEquipe encerrado = novo(equipe, usuario(PerfilAcesso.GERENTE_OPERACOES));
        encerrado.setFimVigencia(INICIO.plusDays(1));
        repository.saveAndFlush(encerrado);
        var pagina = repository.findByEquipeIdAndFimVigenciaIsNull(equipe.getId(), PageRequest.of(1, 1, Sort.by("id")));
        assertThat(pagina.getContent()).extracting(VinculoEquipe::getId).containsExactly(segundo.getId());
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(repository.findByEquipeIdAndFimVigenciaIsNullOrderByIdAsc(Long.MAX_VALUE)).isEmpty();
        assertThat(repository.existsByEquipeIdAndUsuarioIdAndFimVigenciaIsNull(Long.MAX_VALUE, primeiro.getUsuario().getId())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"equipe", "usuario", "inicioVigencia"})
    void deveRejeitarCamposObrigatoriosNoMapeamentoJpa(String campo) {
        VinculoEquipe vinculo = novo(equipe("Norte"), usuario(PerfilAcesso.OPERADOR));
        if (campo.equals("equipe")) vinculo.setEquipe(null);
        if (campo.equals("usuario")) vinculo.setUsuario(null);
        if (campo.equals("inicioVigencia")) vinculo.setInicioVigencia(null);
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(vinculo));
    }

    @Test
    void deveRejeitarFimAnteriorAoInicioPeloCheckDoBanco() {
        VinculoEquipe vinculo = novo(equipe("Norte"), usuario(PerfilAcesso.OPERADOR));
        vinculo.setFimVigencia(INICIO.minusSeconds(1));
        var erro = assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(vinculo));
        assertThat(erro.getMostSpecificCause().getMessage()).containsIgnoringCase("ck_vinculo_vigencia");
    }

    @Test
    void devePermitirEncerramentoNoMesmoInstanteDoInicio() {
        VinculoEquipe vinculo = novo(equipe("Norte"), usuario(PerfilAcesso.OPERADOR));
        vinculo.setFimVigencia(INICIO);
        repository.saveAndFlush(vinculo);
        entityManager.clear();
        assertThat(repository.findById(vinculo.getId()).orElseThrow().getFimVigencia()).isEqualTo(INICIO);
        assertThat(repository.findByEquipeIdAndFimVigenciaIsNullOrderByIdAsc(vinculo.getEquipe().getId())).isEmpty();
    }

    private EquipeManutencao equipe(String nome) {
        EquipeManutencao equipe = new EquipeManutencao();
        equipe.setNome(nome);
        equipe.setSituacao(SituacaoEquipe.FORA_DE_SERVICO);
        return equipes.saveAndFlush(equipe);
    }

    private Usuario usuario(PerfilAcesso perfil) {
        return usuarios.findAll().stream().filter(usuario -> usuario.getPerfil() == perfil).findFirst().orElseThrow();
    }

    private VinculoEquipe novo(EquipeManutencao equipe, Usuario usuario) {
        VinculoEquipe vinculo = new VinculoEquipe();
        vinculo.setEquipe(equipe);
        vinculo.setUsuario(usuario);
        vinculo.setInicioVigencia(INICIO);
        return vinculo;
    }
}
