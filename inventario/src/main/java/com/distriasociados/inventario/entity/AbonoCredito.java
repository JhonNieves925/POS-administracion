package com.distriasociados.inventario.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "abono_credito")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbonoCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remision_id", nullable = false)
    @JsonIgnore
    private Remision remision;

    @JsonProperty("remisionId")
    public Long getRemisionId() {
        return remision != null ? remision.getId() : null;
    }

    /** Valor abonado en este pago */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    /** Fecha en que se recibió el pago */
    @Column(nullable = false)
    private LocalDate fecha;

    /** Observaciones opcionales (ej: "Pago vía Nequi", "Efectivo") */
    @Column(length = 300)
    private String notas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        if (fecha == null) fecha = LocalDate.now();
    }
}
