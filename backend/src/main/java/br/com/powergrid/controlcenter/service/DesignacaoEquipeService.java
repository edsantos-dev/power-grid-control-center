package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.*;
import br.com.powergrid.controlcenter.util.NormalizadorEmail;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DesignacaoEquipeService {
    private final EventoOperacionalRepository eventos;
    private final EquipeManutencaoRepository equipes;
    private final VinculoEquipeRepository vinculos;
    private final HistoricoDesignacaoEquipeRepository historicos;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public DesignacaoEquipeService(EventoOperacionalRepository eventos, EquipeManutencaoRepository equipes,
            VinculoEquipeRepository vinculos, HistoricoDesignacaoEquipeRepository historicos,
            UsuarioRepository usuarios, Clock clock) {
        this.eventos = eventos;
        this.equipes = equipes;
        this.vinculos = vinculos;
        this.historicos = historicos;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Transactional
    public EventoOperacional designar(Long eventoId, Long novaEquipeId, Integer versaoDesignacaoEsperada,
            String comentario, Long usuarioAutenticadoId) {
        validarEntrada(eventoId, novaEquipeId, versaoDesignacaoEsperada, comentario);
        var usuario = usuarioAutenticado(usuarioAutenticadoId);
        var evento = eventos.buscarPorIdParaDesignacao(eventoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado."));
        autorizar(usuario, evento);
        if (!versaoDesignacaoEsperada.equals(evento.getVersaoDesignacao())) {
            throw conflito("A versão da designação diverge da versão esperada. Consulte o evento novamente.");
        }
        if (!SituacaoEventoOperacional.ABERTO.name().equals(evento.getSituacao())
                && !SituacaoEventoOperacional.EM_ANDAMENTO.name().equals(evento.getSituacao())) {
            throw conflito("A designação só pode ser alterada em eventos ABERTO ou EM_ANDAMENTO.");
        }
        var anterior = evento.getEquipeDesignada();
        Long anteriorId = anterior == null ? null : anterior.getId();
        if (Objects.equals(anteriorId, novaEquipeId)) return evento;

        EquipeManutencao nova = null;
        if (novaEquipeId != null) {
            nova = equipes.buscarPorIdParaAlteracao(novaEquipeId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe não encontrada."));
            if (nova.getSituacao() != SituacaoEquipe.DISPONIVEL && nova.getSituacao() != SituacaoEquipe.EM_ATENDIMENTO) {
                throw conflito("A equipe precisa estar DISPONIVEL ou EM_ATENDIMENTO para receber uma designação.");
            }
            if (vinculos.countByEquipeIdAndFimVigenciaIsNull(novaEquipeId) == 0) {
                throw conflito("A equipe precisa de ao menos um integrante ativo para receber uma designação.");
            }
        }
        if (evento.getVersaoDesignacao() == Integer.MAX_VALUE) {
            throw conflito("A versão da designação atingiu o limite suportado.");
        }
        var agora = clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        evento.setEquipeDesignada(nova);
        evento.setVersaoDesignacao(evento.getVersaoDesignacao() + 1);
        eventos.flush();

        var historico = new HistoricoDesignacaoEquipe();
        historico.setEventoOperacional(evento);
        historico.setEquipeAnterior(anterior);
        historico.setEquipeNova(nova);
        historico.setUsuario(usuario);
        historico.setRegistradoEm(agora);
        historico.setComentario(comentario);
        historico.setVersaoDesignacao(evento.getVersaoDesignacao());
        historicos.saveAndFlush(historico);
        return evento;
    }

    private void autorizar(Usuario usuario, EventoOperacional evento) {
        boolean gerente = usuario.getPerfil() == PerfilAcesso.GERENTE_OPERACOES;
        boolean executor = usuario.getPerfil() == PerfilAcesso.OPERADOR
                || usuario.getPerfil() == PerfilAcesso.TECNICO_MANUTENCAO;
        boolean responsavel = evento.getResponsavelUsuario() != null
                && usuario.getId().equals(evento.getResponsavelUsuario().getId());
        if (!gerente && !(executor && responsavel)) {
            throw new AccessDeniedException("Somente o responsável ou um gerente pode alterar a designação.");
        }
    }

    private Usuario usuarioAutenticado(Long id) {
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
        return usuario;
    }

    private void validarEntrada(Long eventoId, Long equipeId, Integer versao, String comentario) {
        if (eventoId == null || eventoId <= 0 || (equipeId != null && equipeId <= 0)
                || versao == null || versao < 0 || comentario == null || comentario.isBlank() || comentario.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe evento, equipe opcional, versão não negativa e comentário não vazio de até 1.000 caracteres.");
        }
    }

    private ResponseStatusException conflito(String mensagem) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
    }
}
