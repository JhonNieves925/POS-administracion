package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.*;
import com.distriasociados.inventario.entity.Remision.EstadoRemision;
import com.distriasociados.inventario.entity.Remision.FormaPago;
import com.distriasociados.inventario.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemisionService {

    private final RemisionRepository remisionRepository;
    private final CargueRepository cargueRepository;
    private final CargueDetalleRepository cargueDetalleRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoService productoService;

    @Value("${remision.prefijo}")
    private String prefijo;

    // ─── CONSULTAS ───────────────────────────────────────────

    public List<Remision> listarPorFecha(LocalDate inicio, LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fin == null) fin = LocalDate.now();
        return remisionRepository.findByFechaBetween(inicio, fin);
    }

    public List<Remision> pendientesPorFecha(LocalDate fecha) {
        return remisionRepository.findByEstadoAndFecha(
            EstadoRemision.PENDIENTE,
            fecha != null ? fecha : LocalDate.now()
        );
    }

    public Remision buscarPorId(Long id) {
        return remisionRepository.findById(id)
                .orElseThrow(() ->
                    new RuntimeException("Remision no encontrada"));
    }

    // ─── CREAR DESDE REQUEST DEL FRONTEND ───────────────────
    // Payload: { cargueId, clienteId, observaciones, detalles:[{cargueDetalleId, cantidadCajas, cantidadUnidades}] }

    // NIT reservado para ventas sin cliente identificado
    public static final String NIT_CONSUMIDOR_FINAL = "222222222222";

    @Transactional
    public Remision crearDesdeRequest(Map<String, Object> body, Usuario usuarioActual) {
        Long cargueId  = Long.valueOf(body.get("cargueId").toString());
        Object cidRaw  = body.get("clienteId");
        String obs     = body.getOrDefault("observaciones", "").toString();

        // ── Forma de pago ──────────────────────────────────────
        FormaPago formaPago = FormaPago.CONTADO;
        Object fpRaw = body.get("formaPago");
        if (fpRaw != null && !fpRaw.toString().isBlank() && !fpRaw.toString().equals("null")) {
            try { formaPago = FormaPago.valueOf(fpRaw.toString().toUpperCase()); }
            catch (IllegalArgumentException ignored) { /* usar CONTADO por defecto */ }
        }
        Integer diasCredito = null;
        if (formaPago == FormaPago.CREDITO) {
            Object dcRaw = body.get("diasCredito");
            if (dcRaw != null && !dcRaw.toString().isBlank()) {
                try { diasCredito = Integer.parseInt(dcRaw.toString()); }
                catch (NumberFormatException ignored) {}
            }
            if (diasCredito == null || diasCredito <= 0) diasCredito = 30; // default 30 días
        }

        Cargue cargue = cargueRepository.findById(cargueId)
            .orElseThrow(() -> new RuntimeException("Cargue no encontrado: " + cargueId));

        // Si no viene clienteId (null o vacío) → usar CONSUMIDOR FINAL automáticamente
        Cliente cliente;
        if (cidRaw == null || cidRaw.toString().isBlank() || cidRaw.toString().equals("null")) {
            cliente = clienteRepository.findByNit(NIT_CONSUMIDOR_FINAL)
                .orElseThrow(() -> new RuntimeException(
                    "No existe el cliente 'CONSUMIDOR FINAL' (NIT " + NIT_CONSUMIDOR_FINAL + "). " +
                    "Créalo en el panel de administración antes de usar ventas rápidas."));
        } else {
            Long clienteId = Long.valueOf(cidRaw.toString());
            cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + clienteId));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detallesRaw =
            (List<Map<String, Object>>) body.get("detalles");

        Remision remision = new Remision();
        remision.setVendedor(cargue.getVendedor());
        remision.setCliente(cliente);
        remision.setCargue(cargue);
        remision.setObservaciones(obs.isBlank() ? null : obs);
        remision.setFormaPago(formaPago);
        remision.setDiasCredito(diasCredito);
        if (formaPago == FormaPago.CREDITO && diasCredito != null) {
            remision.setFechaVencimientoPago(LocalDate.now().plusDays(diasCredito));
        }

        List<RemisionDetalle> detalles = new ArrayList<>();
        for (Map<String, Object> d : detallesRaw) {
            Long cargueDetalleId = Long.valueOf(d.get("cargueDetalleId").toString());
            int cajas    = Integer.parseInt(d.getOrDefault("cantidadCajas", 0).toString());
            int unidades = Integer.parseInt(d.getOrDefault("cantidadUnidades", 0).toString());

            CargueDetalle cargDet = cargueDetalleRepository.findById(cargueDetalleId)
                .orElseThrow(() -> new RuntimeException("Detalle de cargue no encontrado: " + cargueDetalleId));

            Producto prod = cargDet.getProducto();
            int upk = prod.getUnidadesPorCaja() > 0 ? prod.getUnidadesPorCaja() : 1;
            int totalUds = cajas * upk + unidades;
            if (totalUds <= 0) continue;

            BigDecimal precioSugerido = prod.getPrecioCaja()
                .divide(BigDecimal.valueOf(upk), 4, java.math.RoundingMode.HALF_UP);

            // ── Precio negociado: usa override si viene válido ──────────
            BigDecimal precioUnidad = precioSugerido;
            Object precioOverrideRaw = d.get("precioVentaUnidad");
            if (precioOverrideRaw != null && !precioOverrideRaw.toString().isBlank()
                    && !precioOverrideRaw.toString().equals("null")) {
                BigDecimal override = new BigDecimal(precioOverrideRaw.toString());
                // El precio mínimo es el precio de caja sugerido dividido por unidades
                BigDecimal precioMinimo = prod.getPrecioCaja() != null
                    ? prod.getPrecioCaja().divide(BigDecimal.valueOf(upk), 4, java.math.RoundingMode.HALF_UP)
                    : null;
                if (precioMinimo != null && override.compareTo(precioMinimo) < 0) {
                    throw new RuntimeException(
                        "El precio de \"" + prod.getNombreCompleto() + "\" ($" +
                        override.setScale(0, java.math.RoundingMode.HALF_UP) +
                        "/u) no puede ser inferior al precio sugerido ($" +
                        precioMinimo.setScale(0, java.math.RoundingMode.HALF_UP) + "/u)");
                }
                if (override.compareTo(BigDecimal.ZERO) > 0) precioUnidad = override;
            }

            // ── Costo proveedor (para reporte de ganancias) ─────────────
            BigDecimal costoUnitario = null;
            if (cargDet.getPrecioCajaSnapshot() != null && prod.getPrecioCompra() != null) {
                // Usar precioCompra del producto como costo real de proveedor
                costoUnitario = prod.getPrecioCompra()
                    .divide(BigDecimal.valueOf(upk), 4, java.math.RoundingMode.HALF_UP);
            } else if (cargDet.getPrecioCajaSnapshot() != null) {
                // Si no hay precioCompra, usar snapshot del cargue como aproximación
                costoUnitario = cargDet.getPrecioCajaSnapshot()
                    .divide(BigDecimal.valueOf(upk), 4, java.math.RoundingMode.HALF_UP);
            }

            // ── IBUA ────────────────────────────────────────────────────
            BigDecimal ibuaUnidad = prod.getValorIbuaUnidad() != null
                ? prod.getValorIbuaUnidad() : BigDecimal.ZERO;

            RemisionDetalle det = new RemisionDetalle();
            det.setProducto(prod);
            det.setDescripcionProducto(prod.getNombreCompleto());
            det.setCantidad(BigDecimal.valueOf(totalUds));
            det.setPrecioUnitario(precioUnidad);
            det.setPorcentajeIva(BigDecimal.valueOf(prod.getPorcentajeIva()));
            det.setCostoUnitario(costoUnitario);
            det.setValorIbuaUnidad(ibuaUnidad);
            detalles.add(det);
        }

        if (detalles.isEmpty()) {
            throw new RuntimeException("Debe ingresar al menos un producto con cantidad mayor a 0");
        }

        // Validar stock disponible a nivel de grupo
        validarStockGrupo(cargue, detalles);

        remision.setDetalles(detalles);
        return crear(remision);
    }

    // ─── CREAR REMISION (metodo base) ────────────────────────

    @Transactional
    public Remision crear(Remision remision) {
        remision.setNumero(generarNumero());
        // La remisión hereda la fecha del cargue (no la fecha del servidor)
        // así facturación puede buscarla por la misma fecha del cargue
        LocalDate fechaRemision = (remision.getCargue() != null && remision.getCargue().getFecha() != null)
            ? remision.getCargue().getFecha()
            : LocalDate.now();
        remision.setFecha(fechaRemision);
        remision.setEstado(EstadoRemision.PENDIENTE);

        calcularTotales(remision);

        Remision guardada = remisionRepository.save(remision);

        Cliente cliente = guardada.getCliente();
        if (Boolean.TRUE.equals(cliente.getEnvioCorreo()) &&
            cliente.getCorreo() != null) {
            enviarPorCorreo(guardada);
        }

        if (Boolean.TRUE.equals(cliente.getEnvioWhatsapp())) {
            log.info("WhatsApp pendiente de activar para remision: {}",
                guardada.getNumero());
        }

        return guardada;
    }

    // ─── VALIDAR STOCK A NIVEL DE GRUPO ─────────────────────
    // Si el cargue pertenece a un grupo, verifica que no se sobrevenda
    // el pool compartido entre todos los vendedores del grupo.

    private void validarStockGrupo(Cargue cargue, List<RemisionDetalle> detallesNuevos) {
        Long grupoId = cargue.getGrupoId();

        // Obtener todos los cargues relevantes (grupo o solo el propio)
        List<Cargue> carguesGrupo;
        if (grupoId != null) {
            carguesGrupo = cargueRepository.findByGrupoId(grupoId);
        } else {
            carguesGrupo = List.of(cargue);
        }

        List<Long> cargueIds = carguesGrupo.stream().map(Cargue::getId).collect(Collectors.toList());

        // Total cargado por producto en el grupo
        Map<Long, Integer> totalCargadoMap = new HashMap<>();
        for (Cargue c : carguesGrupo) {
            for (CargueDetalle det : c.getDetalles()) {
                totalCargadoMap.merge(det.getProducto().getId(), det.getCantidadCargue(), Integer::sum);
            }
        }

        // Total vendido por producto en el grupo (sin anuladas)
        Map<Long, Integer> totalVendidoMap = new HashMap<>();
        for (Remision rem : remisionRepository.findByCargueIdIn(cargueIds)) {
            if (rem.getEstado() == EstadoRemision.ANULADA) continue;
            for (RemisionDetalle det : rem.getDetalles()) {
                totalVendidoMap.merge(det.getProducto().getId(), det.getCantidad().intValue(), Integer::sum);
            }
        }

        // Verificar cada producto de la remision nueva
        for (RemisionDetalle detNuevo : detallesNuevos) {
            Long pid = detNuevo.getProducto().getId();
            int cargado = totalCargadoMap.getOrDefault(pid, 0);
            int vendido = totalVendidoMap.getOrDefault(pid, 0);
            int disponible = cargado - vendido;
            int intentando = detNuevo.getCantidad().intValue();

            if (intentando > disponible) {
                throw new RuntimeException(
                    "Stock insuficiente para: " + detNuevo.getProducto().getNombreCompleto() +
                    ". Disponible en el grupo: " + disponible + " uds" +
                    (grupoId != null ? " (cargue compartido)" : ""));
            }
        }
    }

    // ─── ACTUALIZAR REMISION ─────────────────────────────────
    // Permite editar cantidades y precios sin anular.
    // Payload: { observaciones?, detalles:[{cargueDetalleId, cantidadCajas, cantidadUnidades, precioVentaUnidad?}] }

    @Transactional
    public Remision actualizar(Long id, Map<String, Object> body) {
        Remision remision = buscarPorId(id);
        if (remision.getEstado() == EstadoRemision.ANULADA) {
            throw new RuntimeException("No se puede editar una remisión anulada");
        }
        if (remision.getEstado() == EstadoRemision.FACTURADA) {
            throw new RuntimeException("No se puede editar una remisión ya facturada en la DIAN");
        }

        // Actualizar observaciones si viene
        if (body.containsKey("observaciones")) {
            String obs = body.get("observaciones") != null ? body.get("observaciones").toString().trim() : "";
            remision.setObservaciones(obs.isBlank() ? null : obs);
        }

        // Actualizar forma de pago si viene
        if (body.containsKey("formaPago")) {
            Object fpRaw = body.get("formaPago");
            if (fpRaw != null && !fpRaw.toString().isBlank() && !fpRaw.toString().equals("null")) {
                try {
                    FormaPago fp = FormaPago.valueOf(fpRaw.toString().toUpperCase());
                    remision.setFormaPago(fp);
                    if (fp == FormaPago.CREDITO) {
                        Object dcRaw = body.get("diasCredito");
                        int dias = 30;
                        if (dcRaw != null && !dcRaw.toString().isBlank()) {
                            try { dias = Integer.parseInt(dcRaw.toString()); } catch (NumberFormatException ignored) {}
                        }
                        remision.setDiasCredito(dias > 0 ? dias : 30);
                        remision.setFechaVencimientoPago(remision.getFecha().plusDays(remision.getDiasCredito()));
                    } else {
                        remision.setDiasCredito(null);
                        remision.setFechaVencimientoPago(null);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Cambiar cliente si viene clienteId
        if (body.containsKey("clienteId")) {
            Object cidRaw = body.get("clienteId");
            if (cidRaw != null && !cidRaw.toString().isBlank() && !cidRaw.toString().equals("null")) {
                Long clienteId = Long.valueOf(cidRaw.toString());
                Cliente cliente = clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + clienteId));
                remision.setCliente(cliente);
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detallesRaw = (List<Map<String, Object>>) body.get("detalles");
        if (detallesRaw == null || detallesRaw.isEmpty()) {
            throw new RuntimeException("Debe incluir al menos un producto");
        }

        Cargue cargue = remision.getCargue();

        // Construir nuevos detalles (misma lógica que crearDesdeRequest)
        List<RemisionDetalle> nuevosDetalles = new ArrayList<>();
        for (Map<String, Object> d : detallesRaw) {
            Long cargueDetalleId = Long.valueOf(d.get("cargueDetalleId").toString());
            int cajas    = Integer.parseInt(d.getOrDefault("cantidadCajas", 0).toString());
            int unidades = Integer.parseInt(d.getOrDefault("cantidadUnidades", 0).toString());

            CargueDetalle cargDet = cargueDetalleRepository.findById(cargueDetalleId)
                .orElseThrow(() -> new RuntimeException("Detalle de cargue no encontrado: " + cargueDetalleId));

            Producto prod = cargDet.getProducto();
            int upk = prod.getUnidadesPorCaja() > 0 ? prod.getUnidadesPorCaja() : 1;
            int totalUds = cajas * upk + unidades;
            if (totalUds <= 0) continue;

            BigDecimal precioSugerido = prod.getPrecioCaja()
                .divide(BigDecimal.valueOf(upk), 4, java.math.RoundingMode.HALF_UP);

            BigDecimal precioUnidad = precioSugerido;
            Object precioOverrideRaw = d.get("precioVentaUnidad");
            if (precioOverrideRaw != null && !precioOverrideRaw.toString().isBlank()
                    && !precioOverrideRaw.toString().equals("null")) {
                BigDecimal override = new BigDecimal(precioOverrideRaw.toString());
                BigDecimal precioMinimo = prod.getPrecioCaja() != null
                    ? prod.getPrecioCaja().divide(BigDecimal.valueOf(upk), 4, java.math.RoundingMode.HALF_UP)
                    : null;
                if (precioMinimo != null && override.compareTo(precioMinimo) < 0) {
                    throw new RuntimeException(
                        "El precio de \"" + prod.getNombreCompleto() + "\" no puede ser inferior al precio sugerido");
                }
                if (override.compareTo(BigDecimal.ZERO) > 0) precioUnidad = override;
            }

            BigDecimal costoUnitario = null;
            if (prod.getPrecioCompra() != null) {
                costoUnitario = prod.getPrecioCompra()
                    .divide(BigDecimal.valueOf(upk), 4, java.math.RoundingMode.HALF_UP);
            } else if (cargDet.getPrecioCajaSnapshot() != null) {
                costoUnitario = cargDet.getPrecioCajaSnapshot()
                    .divide(BigDecimal.valueOf(upk), 4, java.math.RoundingMode.HALF_UP);
            }

            BigDecimal ibuaUnidad = prod.getValorIbuaUnidad() != null ? prod.getValorIbuaUnidad() : BigDecimal.ZERO;

            RemisionDetalle det = new RemisionDetalle();
            det.setProducto(prod);
            det.setDescripcionProducto(prod.getNombreCompleto());
            det.setCantidad(BigDecimal.valueOf(totalUds));
            det.setPrecioUnitario(precioUnidad);
            det.setPorcentajeIva(BigDecimal.valueOf(prod.getPorcentajeIva()));
            det.setCostoUnitario(costoUnitario);
            det.setValorIbuaUnidad(ibuaUnidad);
            nuevosDetalles.add(det);
        }

        if (nuevosDetalles.isEmpty()) {
            throw new RuntimeException("Debe ingresar al menos un producto con cantidad mayor a 0");
        }

        // Validar stock (excluyendo la propia remision del conteo de vendido)
        validarStockGrupoExcluyendo(cargue, nuevosDetalles, id);

        // Reemplazar detalles
        remision.getDetalles().clear();
        remision.getDetalles().addAll(nuevosDetalles);

        // Recalcular totales
        calcularTotales(remision);

        return remisionRepository.save(remision);
    }

    /** Igual que validarStockGrupo pero excluye la remisión que se está editando del conteo. */
    private void validarStockGrupoExcluyendo(Cargue cargue, List<RemisionDetalle> detallesNuevos, Long remisionExcluidaId) {
        Long grupoId = cargue.getGrupoId();
        List<Cargue> carguesGrupo = grupoId != null
            ? cargueRepository.findByGrupoId(grupoId)
            : List.of(cargue);
        List<Long> cargueIds = carguesGrupo.stream().map(Cargue::getId).collect(Collectors.toList());

        Map<Long, Integer> totalCargadoMap = new HashMap<>();
        for (Cargue c : carguesGrupo) {
            for (CargueDetalle det : c.getDetalles()) {
                totalCargadoMap.merge(det.getProducto().getId(), det.getCantidadCargue(), Integer::sum);
            }
        }

        Map<Long, Integer> totalVendidoMap = new HashMap<>();
        for (Remision rem : remisionRepository.findByCargueIdIn(cargueIds)) {
            if (rem.getEstado() == EstadoRemision.ANULADA) continue;
            if (rem.getId().equals(remisionExcluidaId)) continue; // excluir la que se edita
            for (RemisionDetalle det : rem.getDetalles()) {
                totalVendidoMap.merge(det.getProducto().getId(), det.getCantidad().intValue(), Integer::sum);
            }
        }

        for (RemisionDetalle detNuevo : detallesNuevos) {
            Long pid = detNuevo.getProducto().getId();
            int cargado = totalCargadoMap.getOrDefault(pid, 0);
            int vendido = totalVendidoMap.getOrDefault(pid, 0);
            int disponible = cargado - vendido;
            int intentando = detNuevo.getCantidad().intValue();
            if (intentando > disponible) {
                throw new RuntimeException(
                    "Stock insuficiente para: " + detNuevo.getProducto().getNombreCompleto() +
                    ". Disponible: " + disponible + " uds");
            }
        }
    }

    // ─── ANULAR REMISION ─────────────────────────────────────

    @Transactional
    public Remision anular(Long id) {
        Remision remision = buscarPorId(id);
        if (remision.getEstado() == EstadoRemision.FACTURADA) {
            throw new RuntimeException(
                "No se puede anular una remision ya facturada");
        }
        // UPDATE directo solo del estado — evita cascade completo sobre detalles
        // y problemas de serialización con las asociaciones lazy
        remisionRepository.actualizarEstado(id, EstadoRemision.ANULADA);
        remision.setEstado(EstadoRemision.ANULADA);
        return remision;
    }

    // ─── CALCULAR TOTALES ────────────────────────────────────

    private void calcularTotales(Remision remision) {
        BigDecimal subtotal  = BigDecimal.ZERO;
        BigDecimal totalIva  = BigDecimal.ZERO;
        BigDecimal totalIbua = BigDecimal.ZERO;

        for (RemisionDetalle detalle : remision.getDetalles()) {
            detalle.setRemision(remision);
            if (detalle.getPorcentajeIva() == null) {
                detalle.setPorcentajeIva(BigDecimal.valueOf(
                    detalle.getProducto().getPorcentajeIva()));
            }
            if (detalle.getValorIbuaUnidad() == null) {
                BigDecimal ibua = detalle.getProducto().getValorIbuaUnidad();
                detalle.setValorIbuaUnidad(ibua != null ? ibua : BigDecimal.ZERO);
            }
            if (detalle.getDescripcionProducto() == null) {
                detalle.setDescripcionProducto(
                    detalle.getProducto().getNombreCompleto());
            }
            subtotal  = subtotal.add(detalle.getSubtotal());
            totalIva  = totalIva.add(detalle.getValorIva());
            totalIbua = totalIbua.add(detalle.getValorIbua());
        }

        remision.setSubtotal(subtotal);
        remision.setTotalIva(totalIva);
        remision.setTotalIbua(totalIbua);
        remision.setTotal(subtotal.add(totalIva).add(totalIbua));
    }

    // ─── ENVIO POR CORREO ─────────────────────────────────────

    private void enviarPorCorreo(Remision remision) {
        log.info("Correo pendiente de enviar para remision: {} — Cliente: {}",
            remision.getNumero(),
            remision.getCliente().getRazonSocial());
    }

    // ─── GENERAR NUMERO ──────────────────────────────────────

    private String generarNumero() {
        long total = remisionRepository.count() + 1;
        return String.format("%s-%04d", prefijo, total);
    }
}
