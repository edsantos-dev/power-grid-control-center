package br.com.powergrid.controlcenter.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "alerta_sobrecarga")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AlertaSobrecarga {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ativo_id", nullable = false)
    private AtivoRede ativo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicao_id", nullable = false, unique = true)
    private MedicaoTransformador medicao;

    @Column(name = "carga_percentual", nullable = false, precision = 27, scale = 6)
    private BigDecimal cargaPercentual;

    @Column(name = "limiar_percentual", nullable = false, precision = 19, scale = 6)
    private BigDecimal limiarPercentual;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private StatusAlerta status;

    @Column(name = "gerado_em", nullable = false)
    private OffsetDateTime geradoEm;

    @Column(name = "reconhecido_em")
    private OffsetDateTime reconhecidoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconhecido_por_usuario_id")
    private Usuario reconhecidoPorUsuario;
}
