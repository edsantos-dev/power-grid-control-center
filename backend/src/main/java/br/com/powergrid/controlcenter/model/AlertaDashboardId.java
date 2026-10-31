package br.com.powergrid.controlcenter.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AlertaDashboardId implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long idOrigem;
    private TipoAlertaDashboard tipoAlerta;
}
