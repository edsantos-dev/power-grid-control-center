package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import br.com.powergrid.controlcenter.model.Alerta;
import br.com.powergrid.controlcenter.model.HistoricoAlerta;
import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.model.SituacaoAlerta;
import br.com.powergrid.controlcenter.model.Usuario;
import br.com.powergrid.controlcenter.repository.AlertaRepository;
import br.com.powergrid.controlcenter.repository.HistoricoAlertaRepository;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import br.com.powergrid.controlcenter.util.NormalizadorEmail;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TratativaAlertaService {
    private final AlertaRepository alertas;
    private final HistoricoAlertaRepository historicos;
    private final UsuarioRepository usuarios;
    private final EntityManager em;
    private final Clock clock;

    public TratativaAlertaService(AlertaRepository alertas, HistoricoAlertaRepository historicos,
            UsuarioRepository usuarios, EntityManager em, Clock clock) {
        this.alertas = alertas;
        this.historicos = historicos;
        this.usuarios = usuarios;
        this.em = em;
        this.clock = clock;
    }

    @Transactional
    public Alerta reconhecer(Long alertaId, Long usuarioId, String comentario) {
        return transicionar(alertaId, usuarioId, comentario, SituacaoAlerta.ABERTO, SituacaoAlerta.RECONHECIDO);
    }

    @Transactional
    public Alerta resolver(Long alertaId, Long usuarioId, String comentario) {
        return transicionar(alertaId, usuarioId, comentario, SituacaoAlerta.RECONHECIDO, SituacaoAlerta.RESOLVIDO);
    }

    private Alerta transicionar(Long alertaId, Long usuarioId, String comentario,
            SituacaoAlerta origem, SituacaoAlerta destino) {
        validarEntrada(alertaId, usuarioId, comentario);
        var usuario = usuarioAutorizado(usuarioId);
        var alerta = bloquear(alertaId);
        if (alerta.getSituacao() == destino) return alerta;
        if (alerta.getSituacao() != origem) {
            throw conflito("Transição de alerta não permitida: " + alerta.getSituacao() + " → " + destino + ".");
        }
        var agora = clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        alerta.setSituacao(destino);
        alertas.flush();
        var historico = new HistoricoAlerta();
        historico.setAlerta(alerta);
        historico.setUsuario(usuario);
        historico.setSituacaoAnterior(origem);
        historico.setSituacaoNova(destino);
        historico.setAlteradoEm(agora);
        historico.setComentario(comentario);
        historicos.saveAndFlush(historico);
        return alerta;
    }

    private Alerta bloquear(Long id) {
        // O estado bruto é bloqueado antes de converter registros legados para o enum.
        var estado = alertas.bloquearEstadoParaTratativa(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta não encontrado."));
        if (estado.getSituacao() == null) {
            throw conflito("Alerta sem situação exige regularização antes da tratativa.");
        }
        try {
            SituacaoAlerta.valueOf(estado.getSituacao());
        } catch (IllegalArgumentException ex) {
            throw conflito("Alerta com situação desconhecida exige regularização antes da tratativa.");
        }
        var alerta = em.getReference(Alerta.class, id);
        em.refresh(alerta, LockModeType.PESSIMISTIC_WRITE);
        return alerta;
    }

    private Usuario usuarioAutorizado(Long id) {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()
                || autenticacao instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var usuario = usuarios.findByEmail(NormalizadorEmail.normalizar(autenticacao.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!usuario.getId().equals(id)) {
            throw new AccessDeniedException("O usuário informado não corresponde à sessão autenticada.");
        }
        if (usuario.getPerfil() != PerfilAcesso.GERENTE_OPERACOES && usuario.getPerfil() != PerfilAcesso.OPERADOR) {
            throw new AccessDeniedException("Somente gerente e operador podem tratar alertas genéricos.");
        }
        return usuario;
    }

    private void validarEntrada(Long alertaId, Long usuarioId, String comentario) {
        if (alertaId == null || alertaId <= 0 || usuarioId == null || usuarioId <= 0
                || comentario == null || comentario.isBlank() || comentario.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe alerta, usuário autenticado e comentário não vazio de até 1.000 caracteres.");
        }
    }

    private ResponseStatusException conflito(String mensagem) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
    }
}
