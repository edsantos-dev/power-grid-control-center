package br.com.powergrid.controlcenter.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Synchronize;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "vw_alertas_dashboard")
@IdClass(AlertaDashboardId.class)
@Immutable
@Synchronize({"alerta", "alerta_sobrecarga", "alerta_tensao", "alerta_comunicacao", "medicao_linha", "medicao_transformador"})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AlertaDashboardView {
    @Id
    @Column(name = "id_origem", nullable = false)
    @EqualsAndHashCode.Include
    private Long idOrigem;

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tipo_alerta", nullable = false, length = 20)
    @EqualsAndHashCode.Include
    private TipoAlertaDashboard tipoAlerta;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "criticidade", length = 32)
    private Criticidade criticidade;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "escopo", length = 20)
    private EscopoAlerta escopo;

    @Column(name = "ativo_id")
    private Long ativoId;

    @Column(name = "fonte_id")
    private Long fonteId;

    @Column(name = "medicao_id")
    private Long medicaoId;

    @Column(name = "ocorrido_em")
    private OffsetDateTime ocorridoEm;

    @Column(name = "status_origem", length = 32)
    private String statusOrigem;

    @Column(name = "subtipo", length = 20)
    private String subtipo;

    @Column(name = "mensagem", length = 128)
    private String mensagem;

    @Column(name = "reconhecido_em")
    private OffsetDateTime reconhecidoEm;

    @Column(name = "reconhecido_por_usuario_id")
    private Long reconhecidoPorUsuarioId;

    @Column(name = "restabelecido_em")
    private OffsetDateTime restabelecidoEm;

    @Column(name = "carga_percentual", precision = 27, scale = 6)
    private BigDecimal cargaPercentual;

    @Column(name = "limiar_percentual", precision = 19, scale = 6)
    private BigDecimal limiarPercentual;

    @Column(name = "tensao_medida_kv", precision = 19, scale = 6)
    private BigDecimal tensaoMedidaKv;

    @Column(name = "tensao_referencia_kv", precision = 19, scale = 6)
    private BigDecimal tensaoReferenciaKv;

    @Column(name = "limite_inferior_kv", precision = 22, scale = 8)
    private BigDecimal limiteInferiorKv;

    @Column(name = "limite_superior_kv", precision = 22, scale = 8)
    private BigDecimal limiteSuperiorKv;

    @Column(name = "monitorada_desde")
    private OffsetDateTime monitoradaDesde;

    @Column(name = "ultima_comunicacao_em")
    private OffsetDateTime ultimaComunicacaoEm;

    @Column(name = "tolerancia_segundos")
    private Integer toleranciaSegundos;

}
