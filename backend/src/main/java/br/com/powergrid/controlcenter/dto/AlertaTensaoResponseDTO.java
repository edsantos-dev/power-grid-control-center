package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.StatusAlerta;
import br.com.powergrid.controlcenter.model.TipoAlertaTensao;

public record AlertaTensaoResponseDTO(Long id, Long ativoId, Long medicaoId, TipoAlertaTensao tipo,
        BigDecimal tensaoMedidaKv, BigDecimal tensaoReferenciaKv, BigDecimal limiteInferiorKv,
        BigDecimal limiteSuperiorKv, StatusAlerta status, OffsetDateTime geradoEm,
        OffsetDateTime reconhecidoEm, Long reconhecidoPorUsuarioId) {
}
