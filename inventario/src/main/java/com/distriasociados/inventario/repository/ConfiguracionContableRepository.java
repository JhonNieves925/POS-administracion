package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.ConfiguracionContable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfiguracionContableRepository extends JpaRepository<ConfiguracionContable, Long> {
    Optional<ConfiguracionContable> findByClave(String clave);
    List<ConfiguracionContable> findAllByOrderByOrdenAsc();
}
