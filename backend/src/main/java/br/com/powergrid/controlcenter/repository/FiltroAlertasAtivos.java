package br.com.powergrid.controlcenter.repository;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.Criticidade;
import br.com.powergrid.controlcenter.model.EscopoAlerta;
import br.com.powergrid.controlcenter.model.TipoAlertaDashboard;
import br.com.powergrid.controlcenter.model.TipoAtivoAlerta;

public record FiltroAlertasAtivos(TipoAlertaDashboard tipoAlerta, Criticidade criticidade,
        EscopoAlerta escopo, Long ativoId, Long fonteId, TipoAtivoAlerta tipoAtivo,
        Long subestacaoId, OffsetDateTime dataInicio, OffsetDateTime dataFim) {
    public static FiltroAlertasAtivos semFiltros() {
        return new FiltroAlertasAtivos(null, null, null, null, null, null, null, null, null);
    }
}
