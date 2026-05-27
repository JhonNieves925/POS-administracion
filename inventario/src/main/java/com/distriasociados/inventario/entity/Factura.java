package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "facturas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String numero;              // Ej: FEV-1479

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false)
    private LocalDate fecha;

    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormaPago formaPago = FormaPago.CONTADO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MedioPago medioPago = MedioPago.EFECTIVO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalIva = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoFactura estado = EstadoFactura.BORRADOR;

    // Código único DIAN — llega cuando Siigo procesa la factura
    @Column(columnDefinition = "TEXT")
    private String cufe;

    // ID que Siigo asigna a la factura en su sistema
    @Column(length = 50)
    private String siigoId;

    @ManyToOne
    @JoinColumn(name = "creado_por_id")
    private Usuario creadoPor;

    @Column(length = 500)
    private String observaciones;

    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FacturaDetalle> detalles = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<NotaCredito> notasCredito = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<NotaDebito> notasDebito = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    private LocalDateTime actualizadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        actualizadoEn = LocalDateTime.now();
        if (fecha == null) fecha = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEn = LocalDateTime.now();
    }

    public enum EstadoFactura {
        BORRADOR,           // Creada pero no enviada a Siigo
        EMITIDA,            // Enviada a Siigo, tiene CUFE
        CON_NOTA_CREDITO,   // Tiene al menos una nota crédito aplicada
        CON_NOTA_DEBITO,    // Tiene al menos una nota débito aplicada
        ANULADA             // Nota crédito total — factura completamente anulada
    }

    public enum FormaPago {
        CONTADO, CREDITO
    }

    public enum MedioPago {
        EFECTIVO, TRANSFERENCIA, CHEQUE, CONSIGNACION
    }
}