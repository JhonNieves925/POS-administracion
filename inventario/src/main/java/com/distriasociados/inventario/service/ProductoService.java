package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.Producto;
import com.distriasociados.inventario.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;

    // Listar todos los activos
    public List<Producto> listarTodos() {
        return productoRepository.findByActivoTrue();
    }

    // Buscar por ID
    public Producto buscarPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() ->
                    new RuntimeException("Producto no encontrado con id: " + id)
                );
    }

    // Buscar por código
    public Producto buscarPorCodigo(String codigo) {
        return productoRepository.findByCodigo(codigo)
                .orElseThrow(() ->
                    new RuntimeException("Producto no encontrado con código: " + codigo)
                );
    }

    // Buscador general (por marca, sabor o código)
    public List<Producto> buscar(String texto) {
        if (texto == null || texto.isBlank()) {
            return listarTodos();
        }
        return productoRepository.buscarProductos(texto.trim());
    }

    // Productos con stock crítico (para alertas en dashboard)
    public List<Producto> stockCritico() {
        return productoRepository.findProductosStockCritico();
    }

    // Crear producto — si ya existe uno inactivo con ese código, lo reactiva
    public Producto crear(Producto producto) {
        var existente = productoRepository.findByCodigo(producto.getCodigo());
        if (existente.isPresent()) {
            Producto p = existente.get();
            if (!p.getActivo()) {
                // Reactivar y actualizar con los nuevos datos
                p.setMarca(producto.getMarca());
                p.setSabor(producto.getSabor());
                p.setTamanoMl(producto.getTamanoMl());
                p.setUnidadMedida(producto.getUnidadMedida());
                p.setPresentacion(producto.getPresentacion());
                p.setUnidadesPorCaja(producto.getUnidadesPorCaja());
                p.setPrecioCaja(producto.getPrecioCaja());
                p.setPrecioCompra(producto.getPrecioCompra());
                p.setValorIbuaUnidad(producto.getValorIbuaUnidad() != null
                    ? producto.getValorIbuaUnidad() : java.math.BigDecimal.ZERO);
                p.setTipoIva(producto.getTipoIva());
                p.setStockMinimo(producto.getStockMinimo());
                p.setSiigoCodigo(producto.getSiigoCodigo());
                p.setActivo(true);
                return productoRepository.save(p);
            }
            throw new RuntimeException("Ya existe un producto activo con el código: " + producto.getCodigo());
        }
        producto.setActivo(true);
        return productoRepository.save(producto);
    }

    // Editar producto
    public Producto editar(Long id, Producto datosNuevos) {
        Producto producto = buscarPorId(id);

        // Si cambió el código, verificar que no exista
        if (!producto.getCodigo().equals(datosNuevos.getCodigo()) &&
            productoRepository.existsByCodigo(datosNuevos.getCodigo())) {
            throw new RuntimeException("Ya existe un producto con ese código");
        }

        producto.setCodigo(datosNuevos.getCodigo());
        producto.setMarca(datosNuevos.getMarca());
        producto.setSabor(datosNuevos.getSabor());
        producto.setTamanoMl(datosNuevos.getTamanoMl());
        producto.setUnidadMedida(datosNuevos.getUnidadMedida());
        producto.setPresentacion(datosNuevos.getPresentacion());
        producto.setUnidadesPorCaja(datosNuevos.getUnidadesPorCaja());
        producto.setPrecioCaja(datosNuevos.getPrecioCaja());
        producto.setPrecioCompra(datosNuevos.getPrecioCompra());
        producto.setValorIbuaUnidad(datosNuevos.getValorIbuaUnidad() != null
            ? datosNuevos.getValorIbuaUnidad() : java.math.BigDecimal.ZERO);
        producto.setTipoIva(datosNuevos.getTipoIva());
        producto.setStockMinimo(datosNuevos.getStockMinimo());
        producto.setSiigoCodigo(datosNuevos.getSiigoCodigo());

        return productoRepository.save(producto);
    }

    // Desactivar producto
    public void desactivar(Long id) {
        Producto producto = buscarPorId(id);
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    // Actualizar stock (lo usan los otros servicios internamente)
    public void actualizarStock(Long id, int cantidad) {
        Producto producto = buscarPorId(id);
        int nuevoStock = producto.getStockActual() + cantidad;

        if (nuevoStock < 0) {
            throw new RuntimeException(
                "Stock insuficiente para: " + producto.getNombreCompleto() +
                ". Stock actual: " + producto.getStockActual()
            );
        }

        producto.setStockActual(nuevoStock);
        productoRepository.save(producto);
    }
}