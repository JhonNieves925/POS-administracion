package com.distriasociados.inventario.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class CrearFacturaManualRequest {

    @NotNull(message = "Debe seleccionar un cliente")
    private Long clienteId;

    @NotEmpty(message = "Debe agregar al menos un producto")
    @Size(max = 50, message = "Una factura puede tener máximo 50 líneas de detalle")
    private List<@Valid CrearFacturaDetalleRequest> detalles;

    @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres")
    private String observaciones;

    /** CONTADO (default) o CREDITO */
    private String formaPago = "CONTADO";

    /** Solo cuando formaPago=CREDITO. Días hasta la fecha de vencimiento. */
    @Min(value = 1, message = "Los días de crédito deben ser al menos 1")
    @Max(value = 365, message = "Los días de crédito no pueden superar 365")
    private Integer diasCredito;
}
