package com.distriasociados.inventario.service;

import com.distriasociados.inventario.dto.request.CambiarPasswordRequest;
import com.distriasociados.inventario.dto.request.LoginRequest;
import com.distriasociados.inventario.dto.response.LoginResponse;
import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.repository.UsuarioRepository;
import com.distriasociados.inventario.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {

        // 1. Verificar cedula y password contra la BD
        // Si falla lanza BadCredentialsException automáticamente
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getCedula(),
                request.getPassword()
            )
        );

        // 2. Cargar datos completos del usuario
        Usuario usuario = usuarioRepository.findByCedula(request.getCedula())
                .orElseThrow(() ->
                    new BadCredentialsException("Usuario no encontrado")
                );

        // 3. Generar token con duración según "recordarme"
        UserDetails userDetails =
            userDetailsService.loadUserByUsername(request.getCedula());

        String token = jwtUtil.generateToken(userDetails, request.isRecordarme());

        // 4. Retornar respuesta con token y datos del usuario
        return LoginResponse.builder()
                .token(token)
                .cedula(usuario.getCedula())
                .nombre(usuario.getNombre())
                .rol(usuario.getRol().name())
                .primerLogin(usuario.getPrimerLogin())
                .build();
    }

    public void cambiarPassword(String cedula, CambiarPasswordRequest request) {

        // Validar que las contraseñas nuevas coincidan
        if (!request.getPasswordNueva().equals(request.getConfirmarPassword())) {
            throw new IllegalArgumentException(
                "Las contraseñas nuevas no coinciden"
            );
        }

        Usuario usuario = usuarioRepository.findByCedula(cedula)
                .orElseThrow(() ->
                    new RuntimeException("Usuario no encontrado")
                );

        // Validar que la contraseña actual sea correcta
        if (!passwordEncoder.matches(
                request.getPasswordActual(), usuario.getPassword())) {
            throw new BadCredentialsException("La contraseña actual es incorrecta");
        }

        // Actualizar contraseña encriptada y marcar que ya no es primer login
        usuario.setPassword(passwordEncoder.encode(request.getPasswordNueva()));
        usuario.setPrimerLogin(false);
        usuarioRepository.save(usuario);
    }

    public void resetearPassword(Long usuarioId, String nuevaPassword) {
        // Solo el admin llama a este método
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() ->
                    new RuntimeException("Usuario no encontrado")
                );

        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuario.setPrimerLogin(true); // Obliga a cambiar en el próximo login
        usuarioRepository.save(usuario);
    }
}