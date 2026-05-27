package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.Factura;
import com.distriasociados.inventario.entity.Factura.EstadoFactura;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Long> {

    Optional<Factura> findByNumero(String numero);
    boolean existsByNumero(String numero);
    List<Factura> findByClienteId(Long clienteId);
    List<Factura> findByEstado(EstadoFactura estado);
    List<Factura> findByFechaBetween(LocalDate inicio, LocalDate fin);

    // SELECT ... FOR UPDATE: bloquea la fila hasta que la transacción termine,
    // evitando que dos hilos concurrentes generen el mismo número de factura.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Factura> findTopByOrderByIdDesc();
}