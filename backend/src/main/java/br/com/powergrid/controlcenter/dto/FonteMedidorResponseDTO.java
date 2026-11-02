package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.OrigemMedicao;

public record FonteMedidorResponseDTO(Long id, Long medidorId, String identificador, OrigemMedicao origem,
        OffsetDateTime monitoradaDesde, OffsetDateTime ultimaComunicacaoEm) { }
