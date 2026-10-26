package br.com.powergrid.controlcenter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;

public record MedidorResponseDTO(
        Long id, String nome, String numeroSerie, EstadoOperacional estadoOperacional,
        BigDecimal latitude, BigDecimal longitude, LocalDate dataInstalacao,
        String enderecoFornecimento, Long consumidorId, Long transformadorId,
        Long alimentadorId, Long subestacaoId) {
}
