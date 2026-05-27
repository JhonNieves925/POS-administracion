package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.CargueDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CargueDetalleRepository extends JpaRepository<CargueDetalle, Long> {

    List<CargueDetalle> findByCargueId(Long cargueId);

    boolean existsByProductoIdAndCargue_EstadoNot(
        Long productoId,
        com.distriasociados.inventario.entity.Cargue.EstadoCargue estado
    );

    // Detalles en un rango de fechas (campo 'fecha' en entidad Cargue)
    @Query("SELECT cd FROM CargueDetalle cd " +
           "JOIN cd.cargue c " +
           "WHERE c.fecha BETWEEN :desde AND :hasta")
    List<CargueDetalle> findByFechaRango(
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta
    );

    // Detalles de un vendedor especifico en un rango de fechas
    @Query("SELECT cd FROM CargueDetalle cd " +
           "JOIN cd.cargue c " +
           "WHERE c.fecha BETWEEN :desde AND :hasta " +
           "AND c.vendedor.id = :vendedorId")
    List<CargueDetalle> findByFechaRangoYVendedor(
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta,
        @Param("vendedorId") Long vendedorId
    );
}
