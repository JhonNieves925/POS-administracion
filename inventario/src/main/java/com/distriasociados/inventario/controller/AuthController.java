package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.request.CambiarPasswordRequest;
import com.distriasociados.inventario.dto.request.LoginRequest;
import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.dto.response.LoginResponse;
import com.distriasociados.inventario.security.JwtUtil;
import com.distriasociados.inventario.service.AuditService;
import com.distriasociados.inventario.service.AuthService;
import com.distriasociados.inventario.service.RateLimitService;
import com.distriasociados.inventario.service.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    // POST /api/auth/login — público, sin token
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String ip = obtenerIp(httpRequest);
        if (!rateLimitService.permitirIntento(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error("Demasiados intentos. Espere 1 minuto e intente nuevamente."));
        }

        LoginResponse response = authService.login(request);
        auditService.registrar(response.getCedula(), "LOGIN", null, null,
            "Rol: " + response.getRol(), ip);
        return ResponseEntity.ok(ApiResponse.ok("Login exitoso", response));
    }

    // POST /api/auth/logout — invalida el token en el servidor
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                tokenBlacklistService.invalidar(token, jwtUtil.getExpiration(token).toInstant());
            } catch (Exception e) {
                // Token malformado o ya expirado — no hay nada que invalidar
            }
        }
        return ResponseEntity.ok(ApiResponse.ok("Sesión cerrada correctamente"));
    }

    // POST /api/auth/cambiar-password — requiere token
    @PostMapping("/cambiar-password")
    public ResponseEntity<ApiResponse<Void>> cambiarPassword(
            @Valid @RequestBody CambiarPasswordRequest request,
            Authentication authentication) {

        String cedula = authentication.getName();
        authService.cambiarPassword(cedula, request);
        return ResponseEntity.ok(ApiResponse.ok("Contraseña actualizada correctamente"));
    }

    private String obtenerIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}