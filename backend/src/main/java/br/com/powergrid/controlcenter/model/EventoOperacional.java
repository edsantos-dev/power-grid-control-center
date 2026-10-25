package br.com.powergrid.controlcenter.model;

import java.time.OffsetDateTime;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evento_operacional")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EventoOperacional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ativo_id", nullable = false)
    private AtivoRede ativo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "categoria", nullable = false, length = 32)
    private Categoria categoria;

    @Column(name = "criticidade", length = 32)
    private String criticidade;

    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "situacao", length = 32)
    private String situacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrador_usuario_id", nullable = false)
    private Usuario registradorUsuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_usuario_id", nullable = false)
    private Usuario responsavelUsuario;

    @Column(name = "inicio_previsto")
    private OffsetDateTime inicioPrevisto;

    @Column(name = "inicio_efetivo")
    private OffsetDateTime inicioEfetivo;

    @Column(name = "termino_previsto")
    private OffsetDateTime terminoPrevisto;

    @Column(name = "termino_efetivo")
    private OffsetDateTime terminoEfetivo;

    public enum Categoria {
        MANOBRA,
        DESLIGAMENTO_PROGRAMADO,
        INTERRUPCAO_FORNECIMENTO,
        SOBRECARGA,
        FALHA_EQUIPAMENTO,
        MANUTENCAO
    }
}
