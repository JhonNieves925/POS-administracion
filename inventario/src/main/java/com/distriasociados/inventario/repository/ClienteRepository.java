package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByNit(String nit);
    boolean existsByNit(String nit);
    List<Cliente> findByActivoTrue();

    // Buscador por nombre o NIT
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND " +
           "(LOWER(c.razonSocial) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "c.nit LIKE CONCAT('%', :busqueda, '%'))")
    List<Cliente> buscarClientes(String busqueda);
}