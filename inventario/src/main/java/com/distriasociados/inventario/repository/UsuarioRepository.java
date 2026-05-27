package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.Usuario;
import com.distriasociados.inventario.entity.Usuario.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Para el login — busca por cédula
    Optional<Usuario> findByCedula(String cedula);

    // Verificar si ya existe esa cédula antes de crear usuario
    boolean existsByCedula(String cedula);

    // Listar usuarios por rol (ej: todos los vendedores)
    List<Usuario> findByRol(Rol rol);

    // Listar solo usuarios activos
    List<Usuario> findByActivoTrue();

    // Buscar usuarios activos por rol
    List<Usuario> findByRolAndActivoTrue(Rol rol);
}