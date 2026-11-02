package br.com.powergrid.controlcenter.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class ConsumoMedidorFixture {
    private ConsumoMedidorFixture() { }

    public static Dados criar(EntityManager em, boolean comAlerta) {
        var sub = new Subestacao(); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub);
        var alimentador = new Alimentador(); alimentador.setSubestacao(sub); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(alimentador);
        var transformador = new Transformador(); transformador.setAlimentador(alimentador); transformador.setCapacidadeMva(BigDecimal.TEN);
        transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(transformador);
        var consumidor = new Consumidor(); consumidor.setNomeRazaoSocial("Titular da telemetria"); consumidor.setTipoPessoa(Consumidor.TipoPessoa.PJ);
        consumidor.setDocumento(UUID.randomUUID().toString().replace("-", "")); em.persist(consumidor);
        var medidor = new MedidorInteligente(); medidor.setNome("Medidor da telemetria"); medidor.setNumeroSerie("TESTE-" + UUID.randomUUID());
        medidor.setEstadoOperacional(EstadoOperacional.OPERACIONAL); medidor.setConsumidor(consumidor); medidor.setTransformador(transformador);
        medidor.setDataInstalacao(LocalDate.of(2026, 1, 1)); em.persist(medidor);
        var primeira = fonte(em, medidor); var segunda = fonte(em, medidor);
        Long alertaId = null;
        if (comAlerta) { var alerta = alerta(em, primeira); em.flush(); alertaId = alerta.getId(); }
        em.flush();
        return new Dados(sub.getId(), alimentador.getId(), transformador.getId(), consumidor.getId(), medidor.getId(), primeira.getId(), segunda.getId(), alertaId);
    }

    public static FonteMedicao fonte(EntityManager em, MedidorInteligente medidor) {
        var fonte = new FonteMedicao(); fonte.setAtivo(medidor); fonte.setOrigem(OrigemMedicao.SIMULADA);
        fonte.setIdentificador("CONSUMO-TESTE-" + UUID.randomUUID()); fonte.setMonitoradaDesde(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        em.persist(fonte); return fonte;
    }

    public static AlertaComunicacao alerta(EntityManager em, FonteMedicao fonte) {
        var alerta = new AlertaComunicacao(); alerta.setFonte(fonte); alerta.setAtivo(fonte.getAtivo());
        alerta.setMonitoradaDesde(fonte.getMonitoradaDesde()); alerta.setUltimaComunicacaoEm(fonte.getUltimaComunicacaoEm());
        alerta.setToleranciaSegundos(300); alerta.setDetectadoEm(OffsetDateTime.parse("2026-01-02T00:00:00Z"));
        alerta.setStatus(StatusComunicacao.ATIVO); em.persist(alerta); return alerta;
    }

    public static void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("gerente@powergrid.local", "",
                List.of(new SimpleGrantedAuthority("ROLE_GERENTE_OPERACOES"))));
    }

    public static void limpar(JdbcTemplate jdbc, Dados d) {
        jdbc.update("DELETE FROM medicao_consumo_medidor WHERE fonte_id IN (SELECT id FROM fonte_medicao WHERE ativo_id = ?)", d.medidorId());
        jdbc.update("DELETE FROM alerta_comunicacao WHERE ativo_id = ?", d.medidorId());
        jdbc.update("DELETE FROM fonte_medicao WHERE ativo_id = ?", d.medidorId());
        jdbc.update("DELETE FROM medidor_inteligente WHERE id = ?", d.medidorId()); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", d.medidorId());
        jdbc.update("DELETE FROM transformador WHERE id = ?", d.transformadorId()); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", d.transformadorId());
        jdbc.update("DELETE FROM alimentador WHERE id = ?", d.alimentadorId()); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", d.alimentadorId());
        jdbc.update("DELETE FROM subestacao WHERE id = ?", d.subestacaoId()); jdbc.update("DELETE FROM ativo_rede WHERE id = ?", d.subestacaoId());
        jdbc.update("DELETE FROM consumidor WHERE id = ?", d.consumidorId());
    }

    public record Dados(Long subestacaoId, Long alimentadorId, Long transformadorId, Long consumidorId,
            Long medidorId, Long fonteId, Long segundaFonteId, Long alertaId) { }
}
