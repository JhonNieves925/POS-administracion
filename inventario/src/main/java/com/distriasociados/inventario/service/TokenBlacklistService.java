package com.distriasociados.inventario.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lista negra de tokens JWT revocados en el logout.
 * Usa SHA-256 del token como clave para no guardar tokens en claro.
 * Nota: al reiniciar el servidor, la lista se vacía (tokens expirados quedan
 * inválidos de todas formas por expiración natural, así que el riesgo residual
 * solo aplica a tokens activos con "recordarme" de hasta 30 días).
 */
@Service
public class TokenBlacklistService {

    private final ConcurrentHashMap<String, Instant> blacklist = new ConcurrentHashMap<>();

    public void invalidar(String token, Instant expiraEn) {
        blacklist.put(hash(token), expiraEn);
    }

    public boolean estaInvalidado(String token) {
        limpiarExpirados();
        return blacklist.containsKey(hash(token));
    }

    private void limpiarExpirados() {
        Instant ahora = Instant.now();
        blacklist.entrySet().removeIf(e -> e.getValue().isBefore(ahora));
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }
}
