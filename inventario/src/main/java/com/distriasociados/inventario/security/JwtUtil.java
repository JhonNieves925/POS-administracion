package com.distriasociados.inventario.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

	@Value("${JWT_SECRET:distriasociados2024_clave_super_secreta_minimo_32_caracteres}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.expiration.remember}")
    private Long expirationRemember;

    // Genera la llave de firma a partir del secreto
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // GENERAR TOKEN
    // rememberMe = true → dura 30 días / false → dura 8 horas
    public String generateToken(UserDetails userDetails, boolean rememberMe) {
        Map<String, Object> claims = new HashMap<>();
        // Guardamos el rol dentro del token para saber los permisos
        claims.put("rol", userDetails.getAuthorities().iterator().next().getAuthority());

        long duracion = rememberMe ? expirationRemember : expiration;

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername()) // username = cédula
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + duracion))
                .signWith(getSigningKey())
                .compact();
    }

    // EXTRAER la cédula del token
    public String extractCedula(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // EXTRAER el rol del token
    public String extractRol(String token) {
        return extractClaim(token, claims -> claims.get("rol", String.class));
    }

    // VALIDAR si el token es válido para ese usuario
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String cedula = extractCedula(token);
        return cedula.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public Date getExpiration(String token) {
        return extractExpiration(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Método genérico para extraer cualquier dato del token
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }
}