package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.Vendedor;
import com.distriasociados.inventario.repository.VendedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VendedorService {

    private final VendedorRepository vendedorRepository;

    public List<Vendedor> listarTodos() {
        return vendedorRepository.findByActivoTrue();
    }

    public Vendedor buscarPorId(Long id) {
        return vendedorRepository.findById(id)
                .orElseThrow(() ->
                    new RuntimeException("Vendedor no encontrado"));
    }

    public Vendedor buscarPorUsuarioId(Long usuarioId) {
        return vendedorRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() ->
                    new RuntimeException(
                        "No hay vendedor asociado a este usuario"));
    }

    public Vendedor crear(Vendedor vendedor) {
        if (vendedorRepository.existsByCedula(vendedor.getCedula())) {
            throw new RuntimeException(
                "Ya existe un vendedor con la cédula: " + vendedor.getCedula());
        }
        vendedor.setActivo(true);
        return vendedorRepository.save(vendedor);
    }

    public Vendedor editar(Long id, Vendedor datosNuevos) {
        Vendedor vendedor = buscarPorId(id);
        vendedor.setNombre(datosNuevos.getNombre());
        vendedor.setCedula(datosNuevos.getCedula());
        vendedor.setTelefono(datosNuevos.getTelefono());
        vendedor.setRutaPrincipal(datosNuevos.getRutaPrincipal());
        // Vincular usuario si viene en el request (no sobreescribir con null)
        if (datosNuevos.getUsuario() != null) {
            vendedor.setUsuario(datosNuevos.getUsuario());
        }
        return vendedorRepository.save(vendedor);
    }

    public void desactivar(Long id) {
        Vendedor vendedor = buscarPorId(id);
        vendedor.setActivo(false);
        vendedorRepository.save(vendedor);
    }
}