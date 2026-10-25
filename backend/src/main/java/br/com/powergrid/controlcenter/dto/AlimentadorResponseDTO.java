package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;

public record AlimentadorResponseDTO(
		Long id,
		String nome,
		BigDecimal latitude,
		BigDecimal longitude,
		BigDecimal tensaoNominalKv,
		Long subestacaoId,
		LocalDate dataInstalacao,
		EstadoOperacional estadoOperacional) {

}
