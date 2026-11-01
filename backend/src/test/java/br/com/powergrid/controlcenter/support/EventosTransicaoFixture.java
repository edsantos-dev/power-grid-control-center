package br.com.powergrid.controlcenter.support;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;

public final class EventosTransicaoFixture {
    private EventosTransicaoFixture() {
    }

    public static Usuario usuario(EntityManager em, PerfilAcesso perfil) {
        return em.createQuery("select u from Usuario u where u.perfil = :perfil", Usuario.class)
                .setParameter("perfil", perfil).getSingleResult();
    }

    public static EventoOperacional evento(EntityManager em, PerfilAcesso responsavel) {
        var sub = new Subestacao(); sub.setNome("Subestação de transições");
        sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub);
        var evento = new EventoOperacional(); evento.setAtivo(sub); evento.setCategoria(EventoOperacional.Categoria.MANUTENCAO);
        evento.setRegistradorUsuario(usuario(em, PerfilAcesso.OPERADOR)); evento.setResponsavelUsuario(usuario(em, responsavel));
        evento.setSituacao("ABERTO"); evento.setInicioPrevisto(OffsetDateTime.parse("2026-01-01T10:00:00Z"));
        evento.setTerminoPrevisto(OffsetDateTime.parse("2026-01-01T11:00:00Z"));
        em.persist(evento); em.flush(); return evento;
    }

    public static void autenticar(String email) {
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, "credencial-de-teste",
                List.of(new SimpleGrantedAuthority("ROLE_GERENTE_OPERACOES"))));
        SecurityContextHolder.setContext(contexto);
    }
}
