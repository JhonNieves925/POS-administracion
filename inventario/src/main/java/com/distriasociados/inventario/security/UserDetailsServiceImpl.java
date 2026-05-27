package com.distriasociados.inventario.security;

import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    // Spring llama a este método con la cédula como "username"
    @Override
    public UserDetails loadUserByUsername(String cedula)
            throws UsernameNotFoundException {

        Usuario usuario = usuarioRepository.findByCedula(cedula)
                .orElseThrow(() ->
                    new UsernameNotFoundException(
                        "Usuario no encontrado con cédula: " + cedula
                    )
                );

        // Verificar que el usuario esté activo
        if (!usuario.getActivo()) {
            throw new UsernameNotFoundException("Usuario inactivo: " + cedula);
        }

        // El rol se convierte en autoridad (ROLE_ADMIN, ROLE_AUXILIAR, ROLE_VENDEDOR)
        return new User(
                usuario.getCedula(),
                usuario.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))
        );
    }
}