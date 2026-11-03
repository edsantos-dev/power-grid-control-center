package br.com.powergrid.controlcenter.support;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.*;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

public final class DesignacaoEquipeFixture {
    private DesignacaoEquipeFixture() { }

    public static Dados criar(EntityManager em) {
        var evento = EventosTransicaoFixture.evento(em, PerfilAcesso.OPERADOR);
        var operador = evento.getResponsavelUsuario();
        var tecnico = EventosTransicaoFixture.usuario(em, PerfilAcesso.TECNICO_MANUTENCAO);
        var gerente = EventosTransicaoFixture.usuario(em, PerfilAcesso.GERENTE_OPERACOES);
        var primeira = equipe(em, tecnico, SituacaoEquipe.DISPONIVEL);
        var segunda = equipe(em, tecnico, SituacaoEquipe.EM_ATENDIMENTO);
        em.flush();
        return new Dados(evento.getId(), evento.getAtivo().getId(), primeira.getId(), segunda.getId(),
                operador.getId(), tecnico.getId(), gerente.getId());
    }

    private static EquipeManutencao equipe(EntityManager em, Usuario integrante, SituacaoEquipe situacao) {
        var equipe = new EquipeManutencao(); equipe.setNome("Equipe de designação"); equipe.setSituacao(situacao);
        em.persist(equipe);
        var vinculo = new VinculoEquipe(); vinculo.setEquipe(equipe); vinculo.setUsuario(integrante);
        vinculo.setInicioVigencia(OffsetDateTime.parse("2026-01-01T00:00:00Z")); em.persist(vinculo);
        return equipe;
    }

    public static void limpar(JdbcTemplate jdbc, Dados d) {
        jdbc.update("DELETE FROM historico_designacao_equipe WHERE evento_operacional_id = ?", d.eventoId());
        jdbc.update("DELETE FROM historico_evento_operacional WHERE evento_id = ?", d.eventoId());
        jdbc.update("DELETE FROM evento_operacional WHERE id = ?", d.eventoId());
        for (Long id : new Long[] {d.equipeAId(), d.equipeBId()}) {
            jdbc.update("DELETE FROM vinculo_equipe WHERE equipe_id = ?", id);
            jdbc.update("DELETE FROM equipe_manutencao WHERE id = ?", id);
        }
        jdbc.update("DELETE FROM subestacao WHERE id = ?", d.ativoId());
        jdbc.update("DELETE FROM ativo_rede WHERE id = ?", d.ativoId());
    }

    public record Dados(Long eventoId, Long ativoId, Long equipeAId, Long equipeBId,
            Long operadorId, Long tecnicoId, Long gerenteId) { }
}
