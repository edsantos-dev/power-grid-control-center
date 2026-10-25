package br.com.powergrid.controlcenter.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transformador")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class Transformador extends AtivoRede {

	@Column(name = "capacidade_mva", nullable = false, precision = 19, scale = 6)
	private BigDecimal capacidadeMva;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alimentador_id", nullable = false)
	private Alimentador alimentador;

}
