package com.distriasociados.inventario.config;

import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.cedula}")
    private String adminCedula;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${admin.nombre}")
    private String adminNombre;

    @Value("${admin.correo}")
    private String adminCorreo;

    @Override
    public void run(ApplicationArguments args) {
        crearAdminSiNoExiste();
    }

    private void crearAdminSiNoExiste() {
        boolean existeAdmin = usuarioRepository
                .findByRolAndActivoTrue(Usuario.Rol.ADMIN)
                .isEmpty();

        if (existeAdmin) {
            Usuario admin = Usuario.builder()
                    .nombre(adminNombre)
                    .cedula(adminCedula)
                    .correo(adminCorreo)
                    .password(passwordEncoder.encode(adminPassword))
                    .rol(Usuario.Rol.ADMIN)
                    .activo(true)
                    .primerLogin(true)  // Obliga a cambiar contraseña
                    .recordarme(false)
                    .build();

            usuarioRepository.save(admin);
            log.info("✅ Usuario administrador creado — cédula: {}", adminCedula);
        } else {
            log.info("✅ Administrador ya existe");
        }
    }
}