package com.distriasociados.inventario.dto.facturacion;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Resumen previo que se muestra al admin antes de enviar a Siigo */
@Data
@Builder
public class ResumenFacturacionDto {

    private LocalDate fecha;
    private int totalRemisionesPendientes;
    private int totalRemisionesYaFacturadas;
    private int totalRemisionesConError;

    /** Facturas que se van a generar (una por cliente + una consolidada CF) */
    private int facturasAGenerar;

    /** Cuántas remisiones son de consumidor final (se consolidan en 1) */
    private int remisionesConsumidorFinal;

    /** Cuántas son de clientes identificados (1 factura cada una) */
    private int remisionesClienteIdentificado;

    private BigDecimal totalSinIva;
    private BigDecimal totalIva;
    private BigDecimal totalIbua;
    private BigDecimal totalGeneral;

    /** Detalle de cada factura que se va a crear */
    private List<ItemResumen> items;

    @Data
    @Builder
    public static class ItemResumen {
        private String clienteNit;
        private String clienteNombre;
        private int cantidadRemisiones;
        private BigDecimal total;
        private boolean esConsolidada;   // true = consumidor final agrupado
    }
}
