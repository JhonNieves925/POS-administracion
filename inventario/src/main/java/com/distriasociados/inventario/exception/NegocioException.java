package com.distriasociados.inventario.exception;

/**
 * Excepción para errores de negocio que son seguros de mostrar al usuario.
 * Usar en lugar de RuntimeException cuando el mensaje puede ser visto por el frontend.
 */
public class NegocioException extends RuntimeException {
    public NegocioException(String mensaje) {
        super(mensaje);
    }
}
