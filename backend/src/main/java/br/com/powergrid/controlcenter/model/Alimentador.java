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
@Table(name = "alimentador")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class Alimentador extends AtivoRede {

	@Column(name = "tensao_nominal_kv", precision = 19, scale = 6)
	private BigDecimal tensaoNominalKv;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subestacao_id", nullable = false)
	private Subestacao subestacao;

}
