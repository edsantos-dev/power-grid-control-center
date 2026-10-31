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
@Table(name = "alerta_tensao")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AlertaTensao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ativo_id", nullable = false)
    private AtivoRede ativo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicao_id", nullable = false, unique = true)
    private MedicaoLinha medicao;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TipoAlertaTensao tipo;

    @Column(name = "tensao_medida_kv", nullable = false, precision = 19, scale = 6)
    private BigDecimal tensaoMedidaKv;

    @Column(name = "tensao_referencia_kv", nullable = false, precision = 19, scale = 6)
    private BigDecimal tensaoReferenciaKv;

    @Column(name = "limite_inferior_kv", nullable = false, precision = 22, scale = 8)
    private BigDecimal limiteInferiorKv;

    @Column(name = "limite_superior_kv", nullable = false, precision = 22, scale = 8)
    private BigDecimal limiteSuperiorKv;

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
