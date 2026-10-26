package br.com.powergrid.controlcenter.dto;

import java.time.OffsetDateTime;
import br.com.powergrid.controlcenter.model.Criticidade;
import br.com.powergrid.controlcenter.model.EscopoAlerta;
import br.com.powergrid.controlcenter.model.SituacaoAlerta;

public record AlertaResponseDTO(
        Long id, String tipo, Criticidade criticidade, EscopoAlerta escopo,
        SituacaoAlerta situacao, OffsetDateTime geradoEm, Long ativoId) {
}
