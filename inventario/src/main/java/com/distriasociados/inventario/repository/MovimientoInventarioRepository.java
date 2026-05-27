package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.MovimientoInventario;
import com.distriasociados.inventario.entity.MovimientoInventario.TipoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface MovimientoInventarioRepository
        extends JpaRepository<MovimientoInventario, Long> {

    List<MovimientoInventario> findByProductoId(Long productoId);

    List<MovimientoInventario> findByFechaBetween(LocalDate inicio, LocalDate fin);

    List<MovimientoInventario> findByProductoIdAndFechaBetween(
        Long productoId, LocalDate inicio, LocalDate fin
    );

    List<MovimientoInventario> findByTipoAndFechaBetween(
        TipoMovimiento tipo, LocalDate inicio, LocalDate fin
    );

    // Total de unidades por tipo en un período (para reportes)
    @Query("SELECT SUM(m.cantidad) FROM MovimientoInventario m " +
           "WHERE m.producto.id = :productoId AND m.tipo = :tipo " +
           "AND m.fecha BETWEEN :inicio AND :fin")
    Integer sumCantidadByProductoAndTipoAndFecha(
        Long productoId, TipoMovimiento tipo,
        LocalDate inicio, LocalDate fin
    );

    // Movimientos por referencia (para revertir al eliminar un cargue, compra, etc.)
    List<MovimientoInventario> findByReferenciaIdAndReferenciaTipo(
        Long referenciaId, String referenciaTipo);

    // Kardex: suma de cantidades por producto y tipo en un período
    // Retorna [productoId (Long), tipo (String), sumaCantidad (Long)]
    @Query("SELECT m.producto.id, m.tipo, SUM(m.cantidad) FROM MovimientoInventario m " +
           "WHERE m.fecha BETWEEN :inicio AND :fin " +
           "GROUP BY m.producto.id, m.tipo")
    List<Object[]> sumPorProductoYTipo(LocalDate inicio, LocalDate fin);
}