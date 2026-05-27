package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_cedula", columnList = "usuarioCedula"),
    @Index(name = "idx_audit_fecha",  columnList = "fecha"),
    @Index(name = "idx_audit_accion", columnList = "accion")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cédula del usuario que realizó la acción */
    @Column(nullable = false, length = 20)
    private String usuarioCedula;

    /** LOGIN | CREAR_FACTURA | EMITIR_DIAN | ANULAR_FACTURA */
    @Column(nullable = false, length = 50)
    private String accion;

    /** FACTURA, REMISION, etc. — null si la acción no aplica a una entidad */
    @Column(length = 30)
    private String entidadTipo;

    private Long entidadId;

    /** Datos adicionales: número de factura, CUFE, motivo, etc. */
    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(length = 45)
    private String ip;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @PrePersist
    protected void onCreate() {
        if (fecha == null) fecha = LocalDateTime.now();
    }
}
