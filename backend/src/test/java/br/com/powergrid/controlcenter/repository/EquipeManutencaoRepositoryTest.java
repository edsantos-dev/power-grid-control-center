package br.com.powergrid.controlcenter.repository;

import java.util.List;
import br.com.powergrid.controlcenter.model.EquipeManutencao;
import br.com.powergrid.controlcenter.model.SituacaoEquipe;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class EquipeManutencaoRepositoryTest {
    @Autowired private EquipeManutencaoRepository repository;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(SituacaoEquipe.class)
    void devePersistirEquipeEAlterarSituacaoManualmente(SituacaoEquipe destino) {
        EquipeManutencao equipe = nova("Equipe Norte", SituacaoEquipe.FORA_DE_SERVICO);
        equipe.setResponsabilidades("Inspecionar equipamentos e executar manutenção preventiva.");
        equipe.setEspecialidades("Subestações e transformadores");
        repository.saveAndFlush(equipe);
        entityManager.clear();

        EquipeManutencao salva = repository.findById(equipe.getId()).orElseThrow();
        assertThat(salva.getId()).isPositive();
        assertThat(salva.getNome()).isEqualTo(equipe.getNome());
        assertThat(salva.getResponsabilidades()).isEqualTo(equipe.getResponsabilidades());
        assertThat(salva.getEspecialidades()).isEqualTo(equipe.getEspecialidades());
        salva.setSituacao(destino);
        repository.saveAndFlush(salva);
        entityManager.clear();
        assertThat(repository.findById(equipe.getId()).orElseThrow().getSituacao()).isEqualTo(destino);
    }

    @Test
    void deveFiltrarNomeSemDiferenciarMaiusculasECombinarSituacao() {
        EquipeManutencao norte = repository.saveAndFlush(nova("Brigada NORTE", SituacaoEquipe.DISPONIVEL));
        repository.saveAndFlush(nova("Brigada Norte reserva", SituacaoEquipe.FORA_DE_SERVICO));
        repository.saveAndFlush(nova("Brigada Sul", SituacaoEquipe.DISPONIVEL));
        var pagina = repository.buscarFiltradas("  nOrTe  ", SituacaoEquipe.DISPONIVEL, pagina(0, 20));
        assertThat(pagina.getContent()).extracting(EquipeManutencao::getId).containsExactly(norte.getId());
        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }

    @Test
    void devePermitirFiltrosOpcionaisEPaginarSemPerderTotal() {
        List<EquipeManutencao> equipes = repository.saveAllAndFlush(List.of(
                nova("Equipe A", SituacaoEquipe.FORA_DE_SERVICO),
                nova("Equipe B", SituacaoEquipe.EM_ATENDIMENTO),
                nova("Equipe C", SituacaoEquipe.DISPONIVEL)));
        var primeira = repository.buscarFiltradas(null, null, pagina(0, 2));
        var segunda = repository.buscarFiltradas(" ", null, pagina(1, 2));
        assertThat(primeira.getContent()).extracting(EquipeManutencao::getId)
                .containsExactly(equipes.get(0).getId(), equipes.get(1).getId());
        assertThat(segunda.getContent()).extracting(EquipeManutencao::getId).containsExactly(equipes.get(2).getId());
        assertThat(primeira.getTotalElements()).isEqualTo(3);
        assertThat(segunda.getTotalPages()).isEqualTo(2);
        assertThat(repository.buscarFiltradas(null, SituacaoEquipe.EM_ATENDIMENTO, pagina(0, 20)).getContent())
                .extracting(EquipeManutencao::getId).containsExactly(equipes.get(1).getId());
        assertThat(repository.buscarFiltradas("equipe", null, pagina(0, 20)).getTotalElements()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    void deveTratarCaracteresDeLikeComoTextoLiteral(String trecho) {
        EquipeManutencao equipe = repository.saveAndFlush(nova("Equipe " + trecho, SituacaoEquipe.FORA_DE_SERVICO));
        repository.saveAndFlush(nova("Equipe comum", SituacaoEquipe.FORA_DE_SERVICO));
        assertThat(repository.buscarFiltradas(trecho, null, pagina(0, 20)).getContent())
                .extracting(EquipeManutencao::getId).containsExactly(equipe.getId());
    }

    @Test
    void deveRetornarPaginaVaziaQuandoNaoHaCorrespondencia() {
        repository.saveAndFlush(nova("Equipe Norte", SituacaoEquipe.FORA_DE_SERVICO));
        assertThat(repository.buscarFiltradas("Sul", null, pagina(0, 20))).isEmpty();
        assertThat(repository.buscarFiltradas(null, SituacaoEquipe.DISPONIVEL, pagina(0, 20))).isEmpty();
    }

    @Test
    void devePreservarTotalEmPaginaAlemDoLimiteDeOffsetInteiro() {
        repository.saveAndFlush(nova("Equipe Norte", SituacaoEquipe.FORA_DE_SERVICO));
        var resultado = repository.buscarFiltradas(null, null, pagina(Integer.MAX_VALUE, 100));
        assertThat(resultado.getContent()).isEmpty();
        assertThat(resultado.getTotalElements()).isEqualTo(1);
    }

    private PageRequest pagina(int numero, int tamanho) {
        return PageRequest.of(numero, tamanho, Sort.by("id").ascending());
    }

    private EquipeManutencao nova(String nome, SituacaoEquipe situacao) {
        EquipeManutencao equipe = new EquipeManutencao();
        equipe.setNome(nome);
        equipe.setSituacao(situacao);
        return equipe;
    }
}
