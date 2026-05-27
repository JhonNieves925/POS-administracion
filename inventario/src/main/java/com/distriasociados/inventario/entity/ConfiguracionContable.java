package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Mapeo de conceptos contables del negocio a cuentas PUC.
 * El contador configura una vez y el sistema genera la relación de ventas automáticamente.
 *
 * Ejemplo:
 *   clave = "VENTA_CONTADO"  →  cuentaPuc = "4135"  →  nombreCuenta = "Ingresos comercio al por mayor"
 */
@Entity
@Table(name = "configuracion_contable")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionContable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador interno del concepto. Único en el sistema. */
    @Column(unique = true, nullable = false, length = 50)
    private String clave;

    /** Número de cuenta PUC. Ej: "4135", "1305", "2408" */
    @Column(length = 20)
    private String cuentaPuc;

    /** Nombre descriptivo para mostrar en pantalla. Ej: "Ingresos por ventas contado" */
    @Column(nullable = false, length = 120)
    private String descripcion;

    /** Nombre oficial de la cuenta PUC según el plan. Ej: "Comercio al por mayor y al por menor" */
    @Column(length = 120)
    private String nombreCuenta;

    /** Grupo al que pertenece. Ej: "INGRESOS", "ACTIVO", "PASIVO" — para agrupar en el reporte */
    @Column(length = 30)
    private String grupo;

    /** Orden de presentación en pantalla */
    @Column(nullable = false)
    @Builder.Default
    private Integer orden = 0;
}
