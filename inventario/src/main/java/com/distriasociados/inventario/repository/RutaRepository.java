package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RutaRepository extends JpaRepository<Ruta, Long> {

    Optional<Ruta> findByCodigo(String codigo);
    List<Ruta> findByActivoTrue();
    boolean existsByCodigo(String codigo);
}