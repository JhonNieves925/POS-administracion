package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.Ruta;
import com.distriasociados.inventario.repository.RutaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RutaService {

    private final RutaRepository rutaRepository;

    public List<Ruta> listarTodas() {
        return rutaRepository.findByActivoTrue();
    }

    public Ruta buscarPorId(Long id) {
        return rutaRepository.findById(id)
                .orElseThrow(() ->
                    new RuntimeException("Ruta no encontrada"));
    }

    public Ruta crear(Ruta ruta) {
        if (rutaRepository.existsByCodigo(ruta.getCodigo())) {
            throw new RuntimeException(
                "Ya existe una ruta con el código: " + ruta.getCodigo());
        }
        ruta.setActivo(true);
        return rutaRepository.save(ruta);
    }

    public Ruta editar(Long id, Ruta datosNuevos) {
        Ruta ruta = buscarPorId(id);
        ruta.setCodigo(datosNuevos.getCodigo());
        ruta.setNombre(datosNuevos.getNombre());
        ruta.setDescripcion(datosNuevos.getDescripcion());
        return rutaRepository.save(ruta);
    }

    public void desactivar(Long id) {
        Ruta ruta = buscarPorId(id);
        ruta.setActivo(false);
        rutaRepository.save(ruta);
    }
}