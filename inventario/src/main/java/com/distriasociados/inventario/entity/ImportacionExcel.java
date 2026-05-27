package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "importaciones_excel")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportacionExcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoImportacion tipo;       // Qué tipo de datos se importaron

    @Column(nullable = false, length = 200)
    private String archivoNombre;       // Nombre del archivo subido

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoImportacion estado = EstadoImportacion.PENDIENTE;

    @Column(nullable = false)
    private Integer filasExitosas = 0;  // Cuántas filas se importaron bien

    @Column(nullable = false)
    private Integer filasError = 0;     // Cuántas filas fallaron

    // Guardamos los errores en formato JSON
    // Ej: {"fila 3": "Código duplicado", "fila 7": "Precio inválido"}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> errores;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
    }

    public enum TipoImportacion {
        PRODUCTOS, INVENTARIO, CLIENTES
    }

    public enum EstadoImportacion {
        PENDIENTE, PROCESANDO, EXITOSO, ERROR_PARCIAL, ERROR_TOTAL
    }
}