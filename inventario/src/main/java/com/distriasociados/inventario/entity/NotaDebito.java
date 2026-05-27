package com.distriasociados.inventario.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "notas_debito")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaDebito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número consecutivo. Ej: ND-001 */
    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_id", nullable = false)
    @JsonIgnore
    private Factura factura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoNotaDebito tipo;

    @Column(length = 500)
    private String motivo;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Valor total que se le suma a la factura original */
    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal valorNota = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoNota estado = EstadoNota.PENDIENTE;

    @Column(columnDefinition = "TEXT")
    private String cufe;

    @Column(length = 60)
    private String siigoId;

    @Column(length = 500)
    private String errorSiigo;

    @OneToMany(mappedBy = "notaDebito", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NotaDebitoDetalle> detalles = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id")
    @JsonIgnore
    private Usuario creadoPor;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        if (fecha == null) fecha = LocalDate.now();
    }

    public Long getFacturaId()              { return factura != null ? factura.getId() : null; }
    public String getFacturaNumero()        { return factura != null ? factura.getNumero() : null; }
    public String getFacturaClienteNombre() { return factura != null && factura.getCliente() != null
                                                ? factura.getCliente().getRazonSocial() : null; }
    public String getCreadoPorNombre()      { return creadoPor != null ? creadoPor.getNombre() : "-"; }

    public enum TipoNotaDebito {
        INTERESES,       // Intereses por mora en el pago
        AJUSTE_PRECIO,   // Precio cobrado fue menor al real
        CARGO_ADICIONAL  // Flete, seguro u otro cargo posterior
    }

    public enum EstadoNota {
        PENDIENTE,
        EMITIDA,
        RECHAZADA
    }
}
