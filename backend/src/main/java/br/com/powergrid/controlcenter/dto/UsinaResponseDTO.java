package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.TipoGeracao;

public record UsinaResponseDTO(Long id, String nome, BigDecimal latitude, BigDecimal longitude,
        TipoGeracao tipoGeracao, BigDecimal capacidadeMaximaMw, Long subestacaoId,
        LocalDate dataInstalacao, EstadoOperacional estadoOperacional) {
}
