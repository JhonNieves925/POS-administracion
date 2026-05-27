package com.distriasociados.inventario.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "remisiones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Remision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Número propio de remisión: REM-0001, REM-0002...
    @Column(nullable = false, unique = true, length = 15)
    private String numero;

    @Column(nullable = false)
    private LocalDate fecha;

    // Vendedor que genera la remisión en ruta
    @ManyToOne
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Vendedor vendedor;

    // Cliente (tienda) que compra
    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    // Cargue del que se descuenta esta venta
    // @JsonIgnore evita la cadena: Remision→Cargue→CargueDetalle→Cargue (infinita)
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "cargue_id")
    private Cargue cargue;

    /** Expone solo el id del cargue en JSON sin cargar la entidad completa. */
    @com.fasterxml.jackson.annotation.JsonProperty("cargueId")
    public Long getCargueId() {
        return cargue != null ? cargue.getId() : null;
    }

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalIva = BigDecimal.ZERO;

    /** IBUA total de la remisión (suma de todos los IBUA por línea) */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalIbua = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRemision estado = EstadoRemision.PENDIENTE;

    // ── Forma de pago ──────────────────────────────────────────
    /** Contado, crédito, transferencia, etc. Default = CONTADO */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'CONTADO'")
    private FormaPago formaPago = FormaPago.CONTADO;

    /**
     * Días de crédito (solo aplica cuando formaPago = CREDITO).
     * La fecha de vencimiento = fecha + diasCredito.
     */
    @Column
    private Integer diasCredito;

    /**
     * Fecha límite de pago (calculada en el backend cuando es CREDITO).
     * Para CONTADO permanece null.
     */
    @Column
    private LocalDate fechaVencimientoPago;

    // Opciones de envío
    @Column(nullable = false)
    private Boolean correoEnviado = false;

    @Column(nullable = false)
    private Boolean whatsappEnviado = false;    // Cuando se active la API

    @Column(nullable = false)
    private Boolean facturaFisicaEntregada = false;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "factura_id")
    private Factura factura;

    /** Expone sólo el ID de la factura en el JSON (evita referencia circular) */
    @JsonProperty("facturaId")
    public Long getFacturaId() {
        return factura != null ? factura.getId() : null;
    }

    @Column(length = 300)
    private String observaciones;

    // ── Facturación electrónica DIAN (vía Siigo) ──────────────
    /** ID interno que devuelve Siigo al crear la factura (UUID) */
    @Column(length = 80)
    private String siigoInvoiceId;

    /** Número de factura electrónica, ej: FE-0001 */
    @Column(length = 20)
    private String numeroFacturaDian;

    /** Cuándo se envió exitosamente a Siigo */
    private LocalDateTime fechaFacturacion;

    /** Último mensaje de error si el envío falló (para reintentos) */
    @Column(length = 500)
    private String errorFacturacion;

    // ── Estado de pago de crédito ─────────────────────────────
    /**
     * PENDIENTE = sin ningún pago.
     * PARCIAL   = tiene abonos pero no saldado.
     * PAGADO    = completamente saldado (o es CONTADO/TRANSFERENCIA).
     */
    public enum EstadoPago { PENDIENTE, PARCIAL, PAGADO }

    @Enumerated(EnumType.STRING)
    @Column(length = 15, columnDefinition = "VARCHAR(15) DEFAULT 'PENDIENTE'")
    @Builder.Default
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    /** Abonos registrados contra esta remisión de crédito. */
    @JsonIgnore
    @OneToMany(mappedBy = "remision", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AbonoCredito> abonos = new ArrayList<>();

    @JsonManagedReference("remision-detalles")
    @OneToMany(mappedBy = "remision", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RemisionDetalle> detalles = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        if (fecha == null) fecha = LocalDate.now();
    }

    public enum EstadoRemision {
        PENDIENTE,    // Generada en ruta, aún no procesada en bodega
        FACTURADA,    // Ya tiene factura DIAN asociada
        ANULADA       // Cancelada
    }

    public enum FormaPago {
        CONTADO,       // Pago en efectivo al momento de la entrega
        CREDITO,       // Pago a plazo (30/60/90 días)
        TRANSFERENCIA, // Transferencia bancaria / Nequi / Daviplata
        CHEQUE         // Cheque a fecha
    }
}