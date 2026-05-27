package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.NotaDebito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NotaDebitoRepository extends JpaRepository<NotaDebito, Long> {

    List<NotaDebito> findByFactura_IdOrderByCreadoEnDesc(Long facturaId);

    Optional<NotaDebito> findTopByOrderByIdDesc();

    @Query("SELECT COUNT(n) FROM NotaDebito n WHERE n.numero LIKE 'ND-%'")
    long contarNotas();

    /** Listado global con filtro de fechas */
    List<NotaDebito> findByFechaBetweenOrderByFechaDescIdDesc(LocalDate inicio, LocalDate fin);

    /** Suma del valor de todas las ND EMITIDAS para una factura (solo Siigo confirmadas) */
    @Query("SELECT COALESCE(SUM(n.valorNota), 0) FROM NotaDebito n " +
           "WHERE n.factura.id = :facturaId AND n.estado = 'EMITIDA'")
    BigDecimal sumValorByFacturaId(@Param("facturaId") Long facturaId);
}
