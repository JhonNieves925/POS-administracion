package com.distriasociados.inventario.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Endpoint de health check — NO requiere autenticación.
 *
 * Propósito principal: despertar el servidor en Render (free tier hace cold start
 * después de 15 min de inactividad). El frontend hace un ping a este endpoint
 * antes de operaciones lentas (sincronización Siigo, carga de catálogo).
 *
 * GET /api/health  →  200 OK  { status: "UP", timestamp: "..." }
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", Instant.now().toString()
        ));
    }
}
