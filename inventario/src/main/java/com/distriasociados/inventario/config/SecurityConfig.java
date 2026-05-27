package com.distriasociados.inventario.config;


import com.distriasociados.inventario.security.JwtAuthenticationFilter;
import com.distriasociados.inventario.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Value("${app.cors.allowed-origins}")
    private String corsAllowedOrigins;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @PostConstruct
    public void validarConfiguracion() {
        if ("prod".equals(activeProfile) && corsAllowedOrigins.contains("localhost")) {
            throw new IllegalStateException(
                "CORS_ORIGINS contiene 'localhost' en producción. " +
                "Configure la variable de entorno CORS_ORIGINS con la URL real del frontend.");
        }
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())   // Deshabilitado porque usamos JWT
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                // Sin sesión — JWT es stateless (sin estado)
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos (no requieren token)
                .requestMatchers("/api/auth/**").permitAll()
                // Health check — permite que Render y el frontend puedan despertar el servidor
                .requestMatchers("/api/health").permitAll()

                // Solo ADMIN puede gestionar usuarios y configuración
                .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                .requestMatchers("/api/configuracion/**").hasRole("ADMIN")

                // ADMIN y AUXILIAR pueden todo lo demás del sistema web
                .requestMatchers("/api/productos/**").hasAnyRole("ADMIN","AUXILIAR")
                .requestMatchers("/api/inventario/**").hasAnyRole("ADMIN","AUXILIAR")
                .requestMatchers("/api/compras/**").hasAnyRole("ADMIN","AUXILIAR")
                .requestMatchers("/api/clientes/**").hasAnyRole("ADMIN","AUXILIAR","VENDEDOR")
                .requestMatchers("/api/cargues/**").hasAnyRole("ADMIN","AUXILIAR","VENDEDOR")
                .requestMatchers("/api/remisiones/**").hasAnyRole("ADMIN","AUXILIAR","VENDEDOR")
                .requestMatchers("/api/facturas/**").hasAnyRole("ADMIN","AUXILIAR")
                .requestMatchers("/api/reportes/**").hasAnyRole("ADMIN","AUXILIAR")
                .requestMatchers("/api/rutas/**").hasAnyRole("ADMIN","AUXILIAR")
                .requestMatchers("/api/vendedores/**").hasAnyRole("ADMIN","AUXILIAR")
                .requestMatchers("/api/facturacion/**").hasAnyRole("ADMIN","AUXILIAR")
                .requestMatchers("/api/siigo/diagnostico").hasRole("ADMIN")

                // Cualquier otra petición requiere autenticación
                .anyRequest().authenticated()
            )
            // Agregar nuestro filtro JWT antes del filtro estándar de Spring
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS — los orígenes se leen de application-{perfil}.properties
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origenes = Arrays.stream(corsAllowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Encriptador de contraseñas — BCrypt es el estándar seguro
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}