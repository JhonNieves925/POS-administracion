package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUsuarioCedulaOrderByFechaDesc(String cedula);

    List<AuditLog> findByAccionAndFechaBetweenOrderByFechaDesc(
        String accion, LocalDateTime desde, LocalDateTime hasta);
}
