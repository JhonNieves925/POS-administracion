package com.distriasociados.inventario.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String cedula;
    private String nombre;
    private String rol;           // ADMIN, AUXILIAR, VENDEDOR
    private Boolean primerLogin;  // true = debe cambiar contraseña
}