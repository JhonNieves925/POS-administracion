package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.AbonoCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface AbonoCreditoRepository extends JpaRepository<AbonoCredito, Long> {

    List<AbonoCredito> findByRemision_IdOrderByFechaDesc(Long remisionId);

    /** Suma total de abonos registrados para una remisión. */
    @Query("SELECT COALESCE(SUM(a.monto), 0) FROM AbonoCredito a WHERE a.remision.id = :remisionId")
    BigDecimal sumMontoPorRemision(@Param("remisionId") Long remisionId);

    /** Suma de todos los abonos de un cliente (por id). */
    @Query("SELECT COALESCE(SUM(a.monto), 0) FROM AbonoCredito a WHERE a.remision.cliente.id = :clienteId")
    BigDecimal sumMontoPorCliente(@Param("clienteId") Long clienteId);

    /** Abonos registrados en un rango de fechas (para reporte de recaudos). */
    @Query("SELECT a FROM AbonoCredito a WHERE a.fecha BETWEEN :inicio AND :fin ORDER BY a.fecha DESC")
    List<AbonoCredito> findByFechaBetween(@Param("inicio") java.time.LocalDate inicio,
                                          @Param("fin")    java.time.LocalDate fin);

    /** Suma de abonos positivos (cobros reales) en un rango de fechas. */
    @Query("SELECT COALESCE(SUM(a.monto), 0) FROM AbonoCredito a WHERE a.fecha BETWEEN :inicio AND :fin AND a.monto > 0")
    BigDecimal sumRecaudadoEnPeriodo(@Param("inicio") java.time.LocalDate inicio,
                                     @Param("fin")    java.time.LocalDate fin);
}
