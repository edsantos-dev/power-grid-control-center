package br.com.powergrid.controlcenter.support;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

public final class TratativaAlertaFixture {
    private TratativaAlertaFixture() { }

    public static Dados criar(EntityManager em, SituacaoAlerta situacao, boolean comAtivo) {
        Subestacao ativo = null;
        if (comAtivo) {
            ativo = new Subestacao(); ativo.setNome("Subestação de tratativa"); ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            em.persist(ativo);
        }
        var alerta = new Alerta(); alerta.setTipo("Ocorrência operacional"); alerta.setCriticidade(Criticidade.ALTA);
        alerta.setEscopo(comAtivo ? EscopoAlerta.ATIVO : EscopoAlerta.REDE_MONITORADA);
        alerta.setAtivo(ativo); alerta.setSituacao(situacao);
        alerta.setGeradoEm(OffsetDateTime.parse("2026-11-01T10:00:00Z")); em.persist(alerta); em.flush();
        return new Dados(alerta.getId(), ativo == null ? null : ativo.getId(),
                EventosTransicaoFixture.usuario(em, PerfilAcesso.OPERADOR).getId(),
                EventosTransicaoFixture.usuario(em, PerfilAcesso.GERENTE_OPERACOES).getId(),
                EventosTransicaoFixture.usuario(em, PerfilAcesso.TECNICO_MANUTENCAO).getId());
    }

    public static void registrarReconhecimento(EntityManager em, Dados d) {
        var h = new HistoricoAlerta(); h.setAlerta(em.find(Alerta.class, d.alertaId()));
        h.setUsuario(em.find(Usuario.class, d.operadorId()));
        h.setSituacaoAnterior(SituacaoAlerta.ABERTO); h.setSituacaoNova(SituacaoAlerta.RECONHECIDO);
        h.setAlteradoEm(OffsetDateTime.parse("2026-11-02T10:00:00Z")); h.setComentario("Reconhecimento anterior");
        em.persist(h); em.flush();
    }

    public static void limpar(JdbcTemplate jdbc, Dados d) {
        jdbc.update("DELETE FROM historico_alerta WHERE alerta_id = ?", d.alertaId());
        jdbc.update("DELETE FROM alerta WHERE id = ?", d.alertaId());
        if (d.ativoId() != null) {
            jdbc.update("DELETE FROM subestacao WHERE id = ?", d.ativoId());
            jdbc.update("DELETE FROM ativo_rede WHERE id = ?", d.ativoId());
        }
    }

    public record Dados(Long alertaId, Long ativoId, Long operadorId, Long gerenteId, Long tecnicoId) { }
}
