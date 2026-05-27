package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.RemisionDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RemisionDetalleRepository extends JpaRepository<RemisionDetalle, Long> {

    List<RemisionDetalle> findByRemisionId(Long remisionId);
}