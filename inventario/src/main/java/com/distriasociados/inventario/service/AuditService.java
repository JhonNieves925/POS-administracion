package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.AuditLog;
import com.distriasociados.inventario.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;

    /**
     * Guarda la acción en la base de datos.
     *
     * REQUIRES_NEW: abre su propia transacción independiente para que un fallo
     * al guardar la auditoría nunca revierta la operación principal del negocio.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String usuarioCedula, String accion,
                          String entidadTipo, Long entidadId,
                          String detalle, String ip) {
        try {
            repo.save(AuditLog.builder()
                .usuarioCedula(usuarioCedula)
                .accion(accion)
                .entidadTipo(entidadTipo)
                .entidadId(entidadId)
                .detalle(detalle)
                .ip(ip)
                .build());
        } catch (Exception e) {
            log.error("No se pudo guardar auditoría [{}/{}]: {}", accion, entidadId, e.getMessage());
        }
    }
}
