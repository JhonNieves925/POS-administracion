package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.Remision;
import com.distriasociados.inventario.entity.Remision.EstadoRemision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RemisionRepository extends JpaRepository<Remision, Long> {

    Optional<Remision> findByNumero(String numero);
    boolean existsByNumero(String numero);

    /** Busca la remisión cuyo número de factura DIAN coincide — fallback por string */
    Optional<Remision> findFirstByNumeroFacturaDian(String numeroFacturaDian);

    /** Busca la remisión vinculada directamente a esta Factura (FK factura_id) */
    Optional<Remision> findFirstByFactura(com.distriasociados.inventario.entity.Factura factura);

    // Remisiones del día de un vendedor (para la PWA)
    List<Remision> findByVendedorIdAndFecha(Long vendedorId, LocalDate fecha);
    List<Remision> findByVendedorIdAndFechaOrderByIdDesc(Long vendedorId, LocalDate fecha);
    @Query("SELECT r FROM Remision r WHERE r.cargue.id = :cargueId ORDER BY r.id DESC")
    List<Remision> findByCargueIdOrderByIdDesc(@Param("cargueId") Long cargueId);

    List<Remision> findByClienteId(Long clienteId);
    List<Remision> findByEstado(EstadoRemision estado);
    List<Remision> findByFechaBetween(LocalDate inicio, LocalDate fin);

    // Pendientes de facturar (para que la auxiliar las convierta en DIAN)
    List<Remision> findByEstadoAndFecha(EstadoRemision estado, LocalDate fecha);

    // Reporte de nulas: remisiones anuladas en un rango de fechas
    List<Remision> findByEstadoAndFechaBetween(EstadoRemision estado, LocalDate inicio, LocalDate fin);

    // Para facturación electrónica: remisiones de una fecha por estado
    List<Remision> findByFechaAndEstado(LocalDate fecha, EstadoRemision estado);

    // Historial de facturación: facturadas o con error, en rango de fechas
    @Query("SELECT r FROM Remision r WHERE r.fecha BETWEEN :inicio AND :fin " +
           "AND (r.estado = 'FACTURADA' OR r.errorFacturacion IS NOT NULL) " +
           "ORDER BY r.fecha DESC, r.id DESC")
    List<Remision> findByFechaBetweenAndFacturadas(
        @Param("inicio") LocalDate inicio,
        @Param("fin") LocalDate fin);

    // Remisiones FACTURADAS que aún no tienen Factura vinculada (para sincronización histórica)
    @Query("SELECT r FROM Remision r WHERE r.estado = 'FACTURADA' AND r.factura IS NULL AND r.numeroFacturaDian IS NOT NULL ORDER BY r.fecha ASC, r.id ASC")
    List<Remision> findFacturadasSinFactura();

    // Remisiones de varios cargues (para cálculo de stock grupal)
    @Query("SELECT r FROM Remision r WHERE r.cargue.id IN :cargueIds")
    List<Remision> findByCargueIdIn(@Param("cargueIds") List<Long> cargueIds);

    // Actualiza solo el estado — evita cascade completo y problemas de serialización
    @Modifying
    @Query("UPDATE Remision r SET r.estado = :estado WHERE r.id = :id")
    int actualizarEstado(@Param("id") Long id, @Param("estado") Remision.EstadoRemision estado);
}