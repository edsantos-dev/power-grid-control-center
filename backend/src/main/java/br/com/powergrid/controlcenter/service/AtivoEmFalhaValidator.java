package br.com.powergrid.controlcenter.service;

import java.util.Arrays;
import java.util.Objects;
import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.EventoOperacionalRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AtivoEmFalhaValidator {
    private final EntityManager em;
    private final EventoOperacionalRepository eventos;

    public AtivoEmFalhaValidator(EntityManager em, EventoOperacionalRepository eventos) {
        this.em = em; this.eventos = eventos;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bloquear(Long... ids) {
        for (Long id : Arrays.stream(ids).filter(Objects::nonNull).distinct().sorted().toList()) {
            if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID do ativo deve ser positivo.");
            // A trava na raiz compartilhada evita FOR UPDATE sobre os outer joins da herança JOINED.
            var encontrados = em.createNativeQuery("SELECT id FROM ativo_rede WHERE id = :id FOR UPDATE", Long.class)
                    .setParameter("id", id).getResultList();
            if (encontrados.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo de rede não encontrado.");
            var ativo = em.find(AtivoRede.class, id);
            em.refresh(ativo);
        }
    }

    public void validarOperacao(AtivoRede ativo) {
        if (ativo.getEstadoOperacional() == EstadoOperacional.FALHA || eventos.existeFalhaNaoConcluida(ativo.getId())) {
            throw conflito("Ativo em falha impede esta operação.");
        }
    }

    public void validarCriacao(EstadoOperacional estado) {
        if (estado == EstadoOperacional.FALHA) throw conflito("Registre a falha pelo fluxo específico após cadastrar o equipamento.");
    }

    public void validarAtualizacao(AtivoRede ativo, EstadoOperacional solicitado) {
        var destino = solicitado == null ? EstadoOperacional.OPERACIONAL : solicitado;
        if (ativo.getEstadoOperacional() == EstadoOperacional.FALHA) {
            if (destino != EstadoOperacional.FALHA) throw conflito("A falha somente pode ser liberada na conclusão do reparo.");
        } else {
            validarCriacao(destino);
            if (eventos.existeFalhaNaoConcluida(ativo.getId())) throw conflito("Ativo com falha aberta exige regularização pelo fluxo específico.");
        }
    }

    public void validarTopologia(AtivoRede... envolvidos) {
        for (var ativo : envolvidos) if (ativo != null) validarOperacao(ativo);
    }

    public void validarVinculoEstavel(Long anterior, Long atual) {
        if (!Objects.equals(anterior, atual)) throw conflito("A topologia mudou durante a operação. Consulte o ativo novamente.");
    }

    private ResponseStatusException conflito(String mensagem) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
    }
}
