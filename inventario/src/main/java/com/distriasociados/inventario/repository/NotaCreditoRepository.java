package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.NotaCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.List;
import java.util.Optional;

public interface NotaCreditoRepository extends JpaRepository<NotaCredito, Long> {

    List<NotaCredito> findByFactura_IdOrderByCreadoEnDesc(Long facturaId);

    Optional<NotaCredito> findTopByOrderByIdDesc();

    @Query("SELECT COUNT(n) FROM NotaCredito n WHERE n.numero LIKE 'NC-%'")
    long contarNotas();

    @Query("SELECT n FROM NotaCredito n WHERE n.factura.id = :facturaId ORDER BY n.creadoEn DESC")
    List<NotaCredito> findByFacturaConDetalles(@Param("facturaId") Long facturaId);

    /** Listado global con filtro de fechas */
    List<NotaCredito> findByFechaBetweenOrderByFechaDescIdDesc(LocalDate inicio, LocalDate fin);

    /** Suma del valor de todas las NC EMITIDAS para una factura (solo Siigo confirmadas) */
    @Query("SELECT COALESCE(SUM(n.valorNota), 0) FROM NotaCredito n " +
           "WHERE n.factura.id = :facturaId AND n.estado = 'EMITIDA'")
    BigDecimal sumValorByFacturaId(@Param("facturaId") Long facturaId);
}
