package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.Compra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long> {

    List<Compra> findByFechaBetween(LocalDate inicio, LocalDate fin);
    List<Compra> findByProveedorContainingIgnoreCase(String proveedor);
    List<Compra> findByOrderByFechaDesc(); // Las más recientes primero
}