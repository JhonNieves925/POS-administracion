package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.entity.Usuario.Rol;
import com.distriasociados.inventario.entity.Vendedor;
import com.distriasociados.inventario.repository.UsuarioRepository;
import com.distriasociados.inventario.repository.VendedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final VendedorRepository vendedorRepository;

    // Listar todos los usuarios activos
    public List<Usuario> listarTodos() {
        return usuarioRepository.findByActivoTrue();
    }

    // Listar por rol (ej: todos los vendedores)
    public List<Usuario> listarPorRol(Rol rol) {
        return usuarioRepository.findByRolAndActivoTrue(rol);
    }

    // Buscar por ID
    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    // Crear usuario nuevo
    @Transactional
    public Usuario crear(Usuario usuario) {

        // Verificar que la cédula no exista
        if (usuarioRepository.existsByCedula(usuario.getCedula())) {
            throw new RuntimeException(
                "Ya existe un usuario con la cédula: " + usuario.getCedula()
            );
        }

        // Encriptar contraseña antes de guardar
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuario.setActivo(true);
        usuario.setPrimerLogin(true); // Primer login siempre obligatorio

        Usuario guardado = usuarioRepository.save(usuario);

        // Si es VENDEDOR, crear automáticamente su perfil en la tabla vendedores
        if (Rol.VENDEDOR.equals(guardado.getRol()) &&
                !vendedorRepository.existsByCedula(guardado.getCedula())) {
            Vendedor vendedor = new Vendedor();
            vendedor.setNombre(guardado.getNombre());
            vendedor.setCedula(guardado.getCedula());
            vendedor.setUsuario(guardado);
            vendedor.setActivo(true);
            vendedorRepository.save(vendedor);
        }

        return guardado;
    }

    // Editar usuario (sin cambiar contraseña)
    public Usuario editar(Long id, Usuario datosNuevos) {
        Usuario usuario = buscarPorId(id);

        // Verificar cédula si cambió
        if (!usuario.getCedula().equals(datosNuevos.getCedula()) &&
            usuarioRepository.existsByCedula(datosNuevos.getCedula())) {
            throw new RuntimeException(
                "Ya existe un usuario con esa cédula"
            );
        }

        usuario.setNombre(datosNuevos.getNombre());
        usuario.setCedula(datosNuevos.getCedula());
        usuario.setCorreo(datosNuevos.getCorreo());
        usuario.setCelular(datosNuevos.getCelular());
        usuario.setRol(datosNuevos.getRol());

        return usuarioRepository.save(usuario);
    }

    // Desactivar usuario (nunca borramos, solo desactivamos)
    public void desactivar(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    // Resetear contraseña — solo el admin puede hacer esto
    public void resetearPassword(Long id, String passwordTemporal) {
        Usuario usuario = buscarPorId(id);
        usuario.setPassword(passwordEncoder.encode(passwordTemporal));
        usuario.setPrimerLogin(true); // Obliga a cambiar en próximo login
        usuarioRepository.save(usuario);
    }
    
    public Usuario buscarPorCedula(String cedula) {
        return usuarioRepository.findByCedula(cedula)
                .orElseThrow(() ->
                    new RuntimeException("Usuario no encontrado")
                );
    }

    // Listar solo vendedores activos
    public List<Usuario> listarVendedores() {
        return usuarioRepository.findByRolAndActivoTrue(Rol.VENDEDOR);
    }

    // Alternar estado activo/inactivo
    public Usuario toggleActivo(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setActivo(!Boolean.TRUE.equals(usuario.getActivo()));
        return usuarioRepository.save(usuario);
    }
}