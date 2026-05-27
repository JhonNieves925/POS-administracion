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
@Table(name = "notas_credito")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotaCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número consecutivo. Ej: NC-001 */
    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    /** Factura DIAN a la que aplica esta nota */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_id", nullable = false)
    @JsonIgnore
    private Factura factura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoNotaCredito tipo;

    /** Descripción del motivo */
    @Column(length = 500)
    private String motivo;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Valor total de la nota (lo que se resta a la factura original) */
    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal valorNota = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoNota estado = EstadoNota.PENDIENTE;

    /** CUFE asignado por la DIAN a través de Siigo */
    @Column(columnDefinition = "TEXT")
    private String cufe;

    /** ID de la nota en Siigo */
    @Column(length = 60)
    private String siigoId;

    /** Mensaje de error si el envío a Siigo falló */
    @Column(length = 500)
    private String errorSiigo;

    @OneToMany(mappedBy = "notaCredito", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NotaCreditoDetalle> detalles = new ArrayList<>();

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

    // ── Helpers para JSON ──────────────────────────────────────────
    public Long getFacturaId()              { return factura != null ? factura.getId() : null; }
    public String getFacturaNumero()        { return factura != null ? factura.getNumero() : null; }
    public String getFacturaClienteNombre() { return factura != null && factura.getCliente() != null
                                                ? factura.getCliente().getRazonSocial() : null; }
    public String getCreadoPorNombre()      { return creadoPor != null ? creadoPor.getNombre() : "-"; }

    public enum TipoNotaCredito {
        DEVOLUCION,      // Cliente devuelve productos → afecta inventario
        AJUSTE_PRECIO,   // Precio cobrado fue mayor al real → solo valor
        ANULACION        // Cancela la factura completa → afecta inventario y estado de cuenta
    }

    public enum EstadoNota {
        PENDIENTE,  // Creada, no enviada a Siigo
        EMITIDA,    // Enviada y aceptada por Siigo/DIAN
        RECHAZADA   // Siigo la rechazó
    }
}
