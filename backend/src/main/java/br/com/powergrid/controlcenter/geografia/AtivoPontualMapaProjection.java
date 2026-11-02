package br.com.powergrid.controlcenter.geografia;

import java.math.BigDecimal;
import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;

public record AtivoPontualMapaProjection(Long ativoId, String nome, EstadoOperacional estadoOperacional,
        Class<? extends AtivoRede> classeAtivo, BigDecimal latitude, BigDecimal longitude) {
    public CoordenadaGeografica coordenada() { return new CoordenadaGeografica(latitude, longitude); }
}
