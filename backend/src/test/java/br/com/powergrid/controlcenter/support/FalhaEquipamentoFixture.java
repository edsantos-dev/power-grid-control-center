package br.com.powergrid.controlcenter.support;

import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

public final class FalhaEquipamentoFixture {
    private FalhaEquipamentoFixture() { }
    public record Dados(Long ativoId, Long usuarioId, String email) { }
    public static Dados criar(EntityManager em) {
        var usuario = EventosTransicaoFixture.usuario(em, PerfilAcesso.GERENTE_OPERACOES);
        var sub = new Subestacao(); sub.setNome("Equipamento da transação de falha");
        sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub); em.flush();
        return new Dados(sub.getId(), usuario.getId(), usuario.getEmail());
    }
    public static void limpar(JdbcTemplate jdbc, Dados d) {
        jdbc.update("DELETE FROM historico_evento_operacional WHERE evento_id IN (SELECT id FROM evento_operacional WHERE ativo_id = ?)", d.ativoId());
        jdbc.update("DELETE FROM falha_equipamento WHERE evento_operacional_id IN (SELECT id FROM evento_operacional WHERE ativo_id = ?)", d.ativoId());
        jdbc.update("DELETE FROM evento_operacional WHERE ativo_id = ?", d.ativoId());
        jdbc.update("DELETE FROM subestacao WHERE id = ?", d.ativoId());
        jdbc.update("DELETE FROM ativo_rede WHERE id = ?", d.ativoId());
    }
}
