package com.distriasociados.inventario.dto.facturacion;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Resultado de la consulta de configuración de Siigo.
 * Contiene los IDs que deben copiarse como variables de entorno en producción.
 */
@Data
@Builder
public class SiigoConfigDto {

    /** true = se conectó a Siigo real; false = modo mock */
    private boolean modoReal;

    /** Mensaje informativo (especialmente útil en modo mock) */
    private String mensaje;

    /** Tipos de documento disponibles (FV, NC, ND, etc.) */
    private List<TipoDocumento> tiposDocumento;

    /** Impuestos disponibles (IVA 19%, IVA 5%, etc.) */
    private List<Impuesto> impuestos;

    /** Métodos de pago disponibles */
    private List<MetodoPago> metodosPago;

    @Data @Builder
    public static class TipoDocumento {
        private int id;
        private String tipo;      // FV, NC, ND, etc.
        private String nombre;    // "Factura de Venta Electrónica"
        private String variableEntorno; // qué variable de entorno usar
    }

    @Data @Builder
    public static class Impuesto {
        private int id;
        private String nombre;    // "IVA 19%"
        private double porcentaje;
        private String variableEntorno;
    }

    @Data @Builder
    public static class MetodoPago {
        private int id;
        private String nombre;    // "Contado", "Crédito", etc.
        private String variableEntorno;
    }
}
