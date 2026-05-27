package com.distriasociados.inventario.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {

    private static final int MAX_INTENTOS = 5;
    private static final long VENTANA_MS = 60_000L; // 1 minuto

    private record EntradaIP(long primeraVez, AtomicInteger contador) {}

    private final ConcurrentHashMap<String, EntradaIP> registro = new ConcurrentHashMap<>();

    /**
     * Devuelve true si el intento está permitido, false si se superó el límite.
     * Límite: 5 intentos por IP por minuto.
     */
    public boolean permitirIntento(String ip) {
        long ahora = System.currentTimeMillis();
        EntradaIP entrada = registro.compute(ip, (k, v) -> {
            if (v == null || ahora - v.primeraVez() > VENTANA_MS) {
                return new EntradaIP(ahora, new AtomicInteger(0));
            }
            return v;
        });
        return entrada.contador().incrementAndGet() <= MAX_INTENTOS;
    }
}
