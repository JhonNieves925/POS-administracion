package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.Compra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long> {

    List<Compra> findByFechaBetween(LocalDate inicio, LocalDate fin);
    List<Compra> findByProveedorContainingIgnoreCase(String proveedor);
    List<Compra> findByOrderByFechaDesc();

    /** Detección de duplicados: verifica si ya existe una compra con ese CUFE */
    boolean existsByCufe(String cufe);

    /**
     * Historial de productos comprados a un proveedor (para sugerencias automáticas).
     * Devuelve las líneas más recientes agrupadas por producto:
     * [productoId, ultimoPrecioCaja, ultimaCantidad, ultimaFecha]
     */
    @Query(value = """
        SELECT
            d.producto_id,
            d.precio_caja,
            d.cantidad,
            c.fecha
        FROM compra_detalles d
        JOIN compras c ON c.id = d.compra_id
        WHERE LOWER(c.proveedor) LIKE LOWER(CONCAT('%', :nombre, '%'))
        ORDER BY c.fecha DESC
        LIMIT 30
        """, nativeQuery = true)
    List<Object[]> findHistorialProductosProveedor(@Param("nombre") String nombre);
}