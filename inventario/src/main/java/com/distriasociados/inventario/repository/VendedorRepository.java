package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.Vendedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendedorRepository extends JpaRepository<Vendedor, Long> {

    Optional<Vendedor> findByCedula(String cedula);
    List<Vendedor> findByActivoTrue();
    boolean existsByCedula(String cedula);

    // Buscar el vendedor asociado a un usuario
    Optional<Vendedor> findByUsuarioId(Long usuarioId);

    // Vendedores de una ruta específica
    List<Vendedor> findByRutaPrincipalIdAndActivoTrue(Long rutaId);
}