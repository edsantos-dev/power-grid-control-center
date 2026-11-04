package br.com.powergrid.controlcenter.mapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.dto.EventoOperacionalResponseDTO;
import br.com.powergrid.controlcenter.model.EventoOperacional;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;

public final class EventoOperacionalMapper {
    private EventoOperacionalMapper() { }
    public static EventoOperacionalResponseDTO toResponse(EventoOperacional e, EstadoOperacional estadoAposReparo) {
        return new EventoOperacionalResponseDTO(e.getId(), e.getAtivo().getId(), e.getCategoria(), e.getDescricao(),
                e.getCriticidade(), e.getSituacao(), e.getRegistradorUsuario().getId(), e.getResponsavelUsuario().getId(),
                utc(e.getInicioPrevisto()), utc(e.getTerminoPrevisto()), utc(e.getInicioEfetivo()), utc(e.getTerminoEfetivo()),
                e.getEquipeDesignada() == null ? null : e.getEquipeDesignada().getId(), e.getVersaoDesignacao(), estadoAposReparo);
    }
    private static OffsetDateTime utc(OffsetDateTime instante) { return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC); }
}
