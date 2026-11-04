package br.com.powergrid.controlcenter.repository.projection;

import br.com.powergrid.controlcenter.model.AlertaDashboardView;
import br.com.powergrid.controlcenter.model.TipoAtivoAlerta;

public record AlertaAtivoProjection(AlertaDashboardView alerta, TipoAtivoAlerta tipoAtivo) {
    public AlertaAtivoProjection(AlertaDashboardView alerta, String tipoAtivo) {
        this(alerta, tipoAtivo == null ? null : TipoAtivoAlerta.valueOf(tipoAtivo));
    }
}
