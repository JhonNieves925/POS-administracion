package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.Cliente;
import com.distriasociados.inventario.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public List<Cliente> listarTodos() {
        return clienteRepository.findByActivoTrue();
    }

    public List<Cliente> buscar(String texto) {
        if (texto == null || texto.isBlank()) return listarTodos();
        return clienteRepository.buscarClientes(texto.trim());
    }

    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() ->
                    new RuntimeException("Cliente no encontrado"));
    }

    public Cliente buscarPorNit(String nit) {
        return clienteRepository.findByNit(nit)
                .orElseThrow(() ->
                    new RuntimeException("Cliente no encontrado con NIT: " + nit));
    }

    public Cliente crear(Cliente cliente) {
        if (clienteRepository.existsByNit(cliente.getNit())) {
            throw new RuntimeException(
                "Ya existe un cliente con el NIT: " + cliente.getNit());
        }
        cliente.setActivo(true);
        return clienteRepository.save(cliente);
    }

    public Cliente editar(Long id, Cliente datosNuevos) {
        Cliente cliente = buscarPorId(id);

        if (!cliente.getNit().equals(datosNuevos.getNit()) &&
            clienteRepository.existsByNit(datosNuevos.getNit())) {
            throw new RuntimeException("Ya existe un cliente con ese NIT");
        }

        cliente.setTipoPersona(datosNuevos.getTipoPersona());
        cliente.setRazonSocial(datosNuevos.getRazonSocial());
        cliente.setNit(datosNuevos.getNit());
        cliente.setDv(datosNuevos.getDv());
        cliente.setTelefono(datosNuevos.getTelefono());
        cliente.setCorreo(datosNuevos.getCorreo());
        cliente.setDireccion(datosNuevos.getDireccion());
        cliente.setCiudad(datosNuevos.getCiudad());
        cliente.setMunicipio(datosNuevos.getMunicipio());
        cliente.setRegimen(datosNuevos.getRegimen());
        cliente.setEnvioCorreo(datosNuevos.getEnvioCorreo());
        cliente.setEnvioWhatsapp(datosNuevos.getEnvioWhatsapp());
        cliente.setSoloFacturaFisica(datosNuevos.getSoloFacturaFisica());

        return clienteRepository.save(cliente);
    }

    public void desactivar(Long id) {
        Cliente cliente = buscarPorId(id);
        cliente.setActivo(false);
        clienteRepository.save(cliente);
    }
}