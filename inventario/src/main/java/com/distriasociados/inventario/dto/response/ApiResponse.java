package com.distriasociados.inventario.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String mensaje;
    private T data;

    // Respuesta exitosa con datos
    public static <T> ApiResponse<T> ok(String mensaje, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .mensaje(mensaje)
                .data(data)
                .build();
    }

    // Respuesta exitosa sin datos
    public static <T> ApiResponse<T> ok(String mensaje) {
        return ApiResponse.<T>builder()
                .success(true)
                .mensaje(mensaje)
                .build();
    }

    // Respuesta de error
    public static <T> ApiResponse<T> error(String mensaje) {
        return ApiResponse.<T>builder()
                .success(false)
                .mensaje(mensaje)
                .build();
    }
}