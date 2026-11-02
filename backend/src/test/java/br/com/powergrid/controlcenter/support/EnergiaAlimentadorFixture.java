package br.com.powergrid.controlcenter.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

public final class EnergiaAlimentadorFixture {
    private EnergiaAlimentadorFixture() { }

    public static Long[] criar(EntityManager em) {
        var sub = new Subestacao(); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub);
        var a = new Alimentador(); a.setEstadoOperacional(EstadoOperacional.OPERACIONAL); a.setSubestacao(sub);
        a.setPercentualPerdaTecnica(new BigDecimal("3.123456")); em.persist(a);
        var f1 = fonte(em, a); var f2 = fonte(em, a);
        var alerta = new AlertaComunicacao(); alerta.setFonte(f1); alerta.setAtivo(a);
        alerta.setMonitoradaDesde(f1.getMonitoradaDesde()); alerta.setToleranciaSegundos(300);
        alerta.setDetectadoEm(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        alerta.setStatus(StatusComunicacao.ATIVO); alerta.setCriticidade(Criticidade.MEDIA); em.persist(alerta); em.flush();
        return new Long[] {a.getId(), sub.getId(), f1.getId(), f2.getId(), alerta.getId()};
    }

    private static FonteMedicao fonte(EntityManager em, Alimentador a) {
        var f = new FonteMedicao(); f.setAtivo(a); f.setIdentificador("ENERGIA-TX-" + UUID.randomUUID());
        f.setOrigem(OrigemMedicao.SIMULADA); f.setMonitoradaDesde(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3));
        em.persist(f); return f;
    }

    public static void autenticar() {
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("gerente", "",
                AuthorityUtils.createAuthorityList("ROLE_GERENTE_OPERACOES")));
        SecurityContextHolder.setContext(contexto);
    }

    public static void limpar(JdbcTemplate jdbc, Long[] ids) {
        jdbc.update("delete from medicao_energia_alimentador where fonte_id in (?, ?)", ids[2], ids[3]);
        jdbc.update("delete from alerta_comunicacao where fonte_id in (?, ?)", ids[2], ids[3]);
        jdbc.update("delete from fonte_medicao where id in (?, ?)", ids[2], ids[3]);
        jdbc.update("delete from alimentador where id = ?", ids[0]); jdbc.update("delete from ativo_rede where id = ?", ids[0]);
        jdbc.update("delete from subestacao where id = ?", ids[1]); jdbc.update("delete from ativo_rede where id = ?", ids[1]);
    }
}
