package br.com.powergrid.controlcenter.dto.geojson;

import br.com.powergrid.controlcenter.geografia.TipoAtivoMapa;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import com.fasterxml.jackson.annotation.JsonInclude;

public record GeoJsonProperties(Long ativoId, TipoAtivoMapa tipoAtivo, String nome, EstadoOperacional estadoOperacional,
        String detalheUri,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long subestacaoOrigemId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long subestacaoDestinoId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String tipoRepresentacao) {
}
