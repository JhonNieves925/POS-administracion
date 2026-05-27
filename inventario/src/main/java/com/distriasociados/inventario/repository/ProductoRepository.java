package com.distriasociados.inventario.repository;

import com.distriasociados.inventario.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findByCodigo(String codigo);
    Optional<Producto> findBySiigoCodigo(String siigoCodigo);

    boolean existsByCodigo(String codigo);

    // Todos los productos activos
    List<Producto> findByActivoTrue();

    // Buscar por marca (para filtros)
    List<Producto> findByMarcaContainingIgnoreCaseAndActivoTrue(String marca);

    // Productos con stock crítico (stock actual <= stock mínimo)
    // @Query nos permite escribir consultas personalizadas cuando
    // el nombre del método no es suficiente
    @Query("SELECT p FROM Producto p WHERE p.stockActual <= p.stockMinimo AND p.activo = true")
    List<Producto> findProductosStockCritico();

    // Buscar por código o nombre para el buscador del frontend
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND " +
           "(LOWER(p.codigo) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "LOWER(p.marca) LIKE LOWER(CONCAT('%', :busqueda, '%')) OR " +
           "LOWER(p.sabor) LIKE LOWER(CONCAT('%', :busqueda, '%')))")
    List<Producto> buscarProductos(String busqueda);
}