package br.com.powergrid.controlcenter.repository.projection;

import br.com.powergrid.controlcenter.model.TipoAtivoIndicador;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;

public record InventarioIndicadoresProjection(TipoAtivoIndicador tipoAtivo,
        EstadoOperacional estadoOperacional, long quantidade) { }
