package br.com.powergrid.controlcenter.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "medicao_linha")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MedicaoLinha {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fonte_id", nullable = false)
    private FonteMedicao fonte;

    @Column(name = "medido_em", nullable = false)
    private OffsetDateTime medidoEm;

    @Column(name = "recebido_em", nullable = false)
    private OffsetDateTime recebidoEm;

    @Column(name = "tensao_kv", precision = 19, scale = 6)
    private BigDecimal tensaoKv;

    @Column(name = "corrente_a", precision = 19, scale = 6)
    private BigDecimal correnteA;
}
