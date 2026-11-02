package br.com.powergrid.controlcenter.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "medicao_energia_alimentador", uniqueConstraints = @UniqueConstraint(
        name = "uq_medicao_energia_fonte_periodo", columnNames = {"fonte_id", "inicio_periodo", "fim_periodo"}))
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MedicaoEnergiaAlimentador {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fonte_id", nullable = false)
    private FonteMedicao fonte;

    @Column(name = "inicio_periodo", nullable = false)
    private OffsetDateTime inicioPeriodo;

    @Column(name = "fim_periodo", nullable = false)
    private OffsetDateTime fimPeriodo;

    @Column(name = "recebido_em", nullable = false)
    private OffsetDateTime recebidoEm;

    @Column(name = "energia_injetada_mwh", nullable = false, precision = 19, scale = 6)
    private BigDecimal energiaInjetadaMwh;

    @Column(name = "percentual_perda_referencia", nullable = false, precision = 9, scale = 6)
    private BigDecimal percentualPerdaReferencia;
}
