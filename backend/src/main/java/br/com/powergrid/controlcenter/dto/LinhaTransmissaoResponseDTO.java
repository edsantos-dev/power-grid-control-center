package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;

public record LinhaTransmissaoResponseDTO(
		Long id,
		String nome,
		BigDecimal latitude,
		BigDecimal longitude,
		BigDecimal tensaoOperacaoKv,
		Long subestacaoOrigemId,
		Long subestacaoDestinoId,
		BigDecimal extensaoKm,
		LocalDate dataInstalacao,
		EstadoOperacional estadoOperacional) {

}
