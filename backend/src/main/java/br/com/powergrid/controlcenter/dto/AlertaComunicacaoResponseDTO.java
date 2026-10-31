package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.StatusComunicacao;

public record AlertaComunicacaoResponseDTO(Long id, Long fonteId, Long ativoId,
        OffsetDateTime monitoradaDesde, OffsetDateTime ultimaComunicacaoEm,
        Integer toleranciaSegundos, OffsetDateTime detectadoEm,
        StatusComunicacao status, OffsetDateTime restabelecidoEm) {
}
