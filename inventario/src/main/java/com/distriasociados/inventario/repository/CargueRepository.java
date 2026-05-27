package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.Cargue;
import com.distriasociados.inventario.entity.Cargue.EstadoCargue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CargueRepository extends JpaRepository<Cargue, Long> {

    // Cargues del día (para el dashboard)
    List<Cargue> findByFecha(LocalDate fecha);

    // Cargue activo de un vendedor en una fecha
    Optional<Cargue> findByVendedorIdAndFecha(Long vendedorId, LocalDate fecha);

    // Cargues por estado (ej: todos EN_RUTA)
    List<Cargue> findByEstado(EstadoCargue estado);

    // Para la PWA: el cargue activo del vendedor hoy (más reciente primero)
    // Devuelve lista para tolerar duplicados históricos de datos de prueba
    @Query("SELECT c FROM Cargue c WHERE c.vendedor.id = :vendedorId " +
           "AND c.fecha = :fecha AND c.estado <> 'LIQUIDADO' ORDER BY c.id DESC")
    List<Cargue> findCargueActivoVendedor(Long vendedorId, LocalDate fecha);

    // Reportes: cargues en un rango de fechas
    List<Cargue> findByFechaBetween(LocalDate inicio, LocalDate fin);

    // Cargues de un vendedor en un período
    List<Cargue> findByVendedorIdAndFechaBetween(
        Long vendedorId, LocalDate inicio, LocalDate fin
    );

    boolean existsByNumero(String numero);

    // Todos los cargues del mismo grupo
    List<Cargue> findByGrupoId(Long grupoId);
}