package br.com.powergrid.controlcenter.geografia;

import java.math.BigDecimal;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;

public record LinhaMapaProjection(Long ativoId, String nome, EstadoOperacional estadoOperacional,
        Long subestacaoOrigemId, Long subestacaoDestinoId,
        BigDecimal latitudeOrigem, BigDecimal longitudeOrigem, BigDecimal latitudeDestino, BigDecimal longitudeDestino) {
    public CoordenadaGeografica origem() { return new CoordenadaGeografica(latitudeOrigem, longitudeOrigem); }
    public CoordenadaGeografica destino() { return new CoordenadaGeografica(latitudeDestino, longitudeDestino); }
}
