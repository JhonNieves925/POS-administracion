package com.distriasociados.inventario.dto.facturacion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Resultado de la sincronización de productos desde Siigo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiigoSyncResultDto {

    /** Productos existentes a los que se actualizó siigoCodigo o tipoIva */
    private int actualizados;

    /** Productos nuevos creados (activo=false, pendientes de completar) */
    private int creados;

    /** Productos que ya estaban sincronizados y sin cambios */
    private int sinCambios;

    /** Productos que fallaron al procesar */
    private int errores;

    /** Nombres de los productos recién creados */
    private List<String> productosCreados;

    /** Descripción de errores individuales */
    private List<String> mensajesError;
}
