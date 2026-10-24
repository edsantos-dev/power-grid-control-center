package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;

public record SubestacaoResponseDTO(
		Long id,
		String nome,
		BigDecimal latitude,
		BigDecimal longitude,
		BigDecimal tensaoNominalKv,
		BigDecimal capacidadeMva,
		LocalDate dataInstalacao,
		EstadoOperacional estadoOperacional) {

}
