package com.distriasociados.inventario.service.facturacion;

import com.distriasociados.inventario.dto.facturacion.FacturaSiigoRequest;
import com.distriasociados.inventario.dto.facturacion.LineaFacturaDto;
import com.distriasociados.inventario.dto.facturacion.NotaSiigoRequest;
import com.distriasociados.inventario.dto.facturacion.SiigoConfigDto;
import com.distriasociados.inventario.dto.facturacion.SiigoInvoiceResult;
import com.distriasociados.inventario.dto.facturacion.SiigoProductoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Implementación REAL del conector con Siigo API.
 * Activa cuando facturacion.proveedor=siigo en application.properties.
 *
 * Documentación Siigo: https://developers.siigo.com/docs/siigoapi/
 *
 * Pasos para activar en producción:
 *   1. En application-prod.properties poner facturacion.proveedor=siigo
 *   2. Configurar siigo.api.username y siigo.api.access-key con las credenciales reales
 *   3. Obtener el siigo.api.documento-id consultando GET /v1/document-types
 *   4. Verificar siigo.api.iva19-id con GET /v1/taxes (normalmente es 3)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "facturacion.proveedor", havingValue = "siigo")
public class RealSiigoService implements SiigoPort {

    @Value("${siigo.api.url}")
    private String baseUrl;

    @Value("${siigo.api.username}")
    private String username;

    @Value("${siigo.api.access-key}")
    private String accessKey;

    @Value("${siigo.api.partner-id}")
    private String partnerId;

    // ID del tipo de documento "Factura de Venta Electrónica" en tu cuenta Siigo.
    // Consúltalo en: GET {baseUrl}/v1/document-types?type=FV
    @Value("${siigo.api.documento-id:24}")
    private int documentoId;

    // ID del tipo de documento "Nota Crédito" en tu cuenta Siigo.
    // Consúltalo en: GET {baseUrl}/v1/document-types?type=NC
    @Value("${siigo.api.nc-documento-id:25}")
    private int ncDocumentoId;

    // ID del tipo de documento "Nota Débito" en tu cuenta Siigo.
    // Consúltalo en: GET {baseUrl}/v1/document-types?type=ND
    @Value("${siigo.api.nd-documento-id:26}")
    private int ndDocumentoId;

    // ID del método de pago "Contado" en tu cuenta Siigo.
    // Consúltalo en: GET {baseUrl}/v1/payment-types?document_type=FV
    @Value("${siigo.api.pago-id:1}")
    private int pagoId;

    // ID del impuesto IVA 19% en tu cuenta Siigo.
    // Consúltalo en: GET {baseUrl}/v1/taxes
    @Value("${siigo.api.iva19-id:3}")
    private int iva19Id;

    // ID del impuesto IVA 5% en tu cuenta Siigo (si aplica).
    @Value("${siigo.api.iva5-id:4}")
    private int iva5Id;

    private final RestTemplate restTemplate = new RestTemplate();

    // Token en caché: se renueva si expiró
    private String cachedToken;
    private LocalDateTime tokenExpira;

    // ─── MÉTODO PRINCIPAL ────────────────────────────────────────

    @Override
    public SiigoInvoiceResult crearFactura(FacturaSiigoRequest request) {
        try {
            String token = obtenerToken();
            Map<String, Object> payload = construirPayload(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            headers.set("Partner-Id", partnerId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/v1/invoices",
                HttpMethod.POST,
                entity,
                Map.class
            );

            Map<?, ?> body = response.getBody();
            if (body == null) throw new RuntimeException("Siigo devolvió respuesta vacía");

            String siigoId = String.valueOf(body.get("id"));
            String numero  = String.valueOf(body.get("name")); // Ej: "FE-0123"
            String cufe    = body.get("stamp") != null ? body.get("stamp").toString() : null;

            log.info("[SIIGO] Factura creada: {} — Cliente: {}", numero, request.getClienteNit());

            return SiigoInvoiceResult.builder()
                    .exito(true)
                    .siigoId(siigoId)
                    .numeroFactura(numero)
                    .cufe(cufe)
                    .build();

        } catch (Exception e) {
            log.error("[SIIGO] Error creando factura para cliente {}: {}",
                request.getClienteNit(), e.getMessage());
            return SiigoInvoiceResult.builder()
                    .exito(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    // ─── AUTENTICACIÓN ───────────────────────────────────────────

    private String obtenerToken() {
        if (cachedToken != null && tokenExpira != null &&
            LocalDateTime.now().isBefore(tokenExpira)) {
            return cachedToken;
        }

        Map<String, String> authBody = new HashMap<>();
        authBody.put("username", username);
        authBody.put("access_key", accessKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Partner-Id", partnerId);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(authBody, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/auth",
            HttpMethod.POST,
            request,
            Map.class
        );

        Map<?, ?> body = response.getBody();
        if (body == null || body.get("access_token") == null) {
            throw new RuntimeException("Siigo no devolvió token de autenticación");
        }

        cachedToken = String.valueOf(body.get("access_token"));
        // Siigo tokens duran 1 hora; guardamos 55 min para seguridad
        tokenExpira = LocalDateTime.now().plusMinutes(55);
        log.info("[SIIGO] Token renovado, válido hasta: {}", tokenExpira);

        return cachedToken;
    }

    // ─── NOTA CRÉDITO ────────────────────────────────────────────

    @Override
    public SiigoInvoiceResult crearNotaCredito(NotaSiigoRequest request) {
        try {
            String token = obtenerToken();
            Map<String, Object> payload = construirPayloadNota(request, "credit_note");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            headers.set("Partner-Id", partnerId);

            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/v1/credit-notes",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Map.class
            );
            Map<?, ?> body = response.getBody();
            if (body == null) throw new RuntimeException("Siigo devolvió respuesta vacía");

            log.info("[SIIGO] Nota Crédito creada: {} — Factura: {}",
                body.get("name"), request.getFacturaNumero());
            return SiigoInvoiceResult.builder()
                .exito(true)
                .siigoId(String.valueOf(body.get("id")))
                .numeroFactura(String.valueOf(body.get("name")))
                .build();
        } catch (Exception e) {
            log.error("[SIIGO] Error creando nota crédito para factura {}: {}",
                request.getFacturaNumero(), e.getMessage());
            return SiigoInvoiceResult.builder().exito(false).error(e.getMessage()).build();
        }
    }

    // ─── NOTA DÉBITO ─────────────────────────────────────────────

    @Override
    public SiigoInvoiceResult crearNotaDebito(NotaSiigoRequest request) {
        try {
            String token = obtenerToken();
            Map<String, Object> payload = construirPayloadNota(request, "debit_note");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            headers.set("Partner-Id", partnerId);

            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/v1/debit-notes",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Map.class
            );
            Map<?, ?> body = response.getBody();
            if (body == null) throw new RuntimeException("Siigo devolvió respuesta vacía");

            log.info("[SIIGO] Nota Débito creada: {} — Factura: {}",
                body.get("name"), request.getFacturaNumero());
            return SiigoInvoiceResult.builder()
                .exito(true)
                .siigoId(String.valueOf(body.get("id")))
                .numeroFactura(String.valueOf(body.get("name")))
                .build();
        } catch (Exception e) {
            log.error("[SIIGO] Error creando nota débito para factura {}: {}",
                request.getFacturaNumero(), e.getMessage());
            return SiigoInvoiceResult.builder().exito(false).error(e.getMessage()).build();
        }
    }

    // ─── LISTAR PRODUCTOS (para sincronización) ──────────────────

    /**
     * Trae todos los productos activos del catálogo de Siigo, paginando
     * automáticamente hasta obtener el total reportado por la API.
     * Siigo devuelve max 25 por página.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<SiigoProductoDto> listarProductos() {
        String token = obtenerToken();
        List<SiigoProductoDto> todos = new ArrayList<>();
        int page = 1;
        final int PAGE_SIZE = 25;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Partner-Id", partnerId);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        while (true) {
            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/v1/products?page=" + page + "&page_size=" + PAGE_SIZE + "&active=true",
                HttpMethod.GET, entity, Map.class
            );

            Map<?, ?> body = response.getBody();
            if (body == null) break;

            List<Map<?, ?>> results = (List<Map<?, ?>>) body.get("results");
            if (results == null || results.isEmpty()) break;

            for (Map<?, ?> prod : results) {
                try { todos.add(mapProducto(prod)); }
                catch (Exception e) {
                    log.warn("[SIIGO] Error mapeando producto {}: {}", prod.get("code"), e.getMessage());
                }
            }

            // ¿Hay más páginas?
            Map<?, ?> pagination = (Map<?, ?>) body.get("pagination");
            if (pagination == null) break;
            int total = ((Number) pagination.get("total_results")).intValue();
            if (todos.size() >= total) break;
            page++;
        }

        log.info("[SIIGO] {} productos obtenidos del catálogo", todos.size());
        return todos;
    }

    @SuppressWarnings("unchecked")
    private SiigoProductoDto mapProducto(Map<?, ?> prod) {
        String siigoId = String.valueOf(prod.get("id"));
        String codigo  = String.valueOf(prod.get("code"));
        String nombre  = String.valueOf(prod.get("name"));
        boolean activo = Boolean.TRUE.equals(prod.get("active"));

        // IVA: toma el porcentaje del primer impuesto si existe
        double ivaPct = 0.0;
        List<Map<?, ?>> taxes = (List<Map<?, ?>>) prod.get("taxes");
        if (taxes != null && !taxes.isEmpty()) {
            Object pct = taxes.get(0).get("percentage");
            if (pct != null) ivaPct = ((Number) pct).doubleValue();
        }

        // Precio: primer valor de la primera lista de precios
        BigDecimal precio = BigDecimal.ZERO;
        try {
            List<Map<?, ?>> prices = (List<Map<?, ?>>) prod.get("prices");
            if (prices != null && !prices.isEmpty()) {
                List<Map<?, ?>> priceList = (List<Map<?, ?>>) prices.get(0).get("price_list");
                if (priceList != null && !priceList.isEmpty()) {
                    Object val = priceList.get(0).get("value");
                    if (val != null) precio = new BigDecimal(val.toString());
                }
            }
        } catch (Exception ignored) {}

        return SiigoProductoDto.builder()
            .siigoId(siigoId)
            .codigo(codigo)
            .nombre(nombre)
            .porcentajeIva(ivaPct)
            .precio(precio)
            .activo(activo)
            .build();
    }

    // ─── CONSULTAR CONFIGURACIÓN ─────────────────────────────────

    /**
     * Llama a Siigo para obtener los IDs de configuración de esta cuenta.
     * Se ejecuta una sola vez para configurar las variables de entorno en producción.
     *
     * Endpoints consultados:
     *   GET /v1/document-types?type=FV  → ID de Factura de Venta Electrónica
     *   GET /v1/document-types?type=NC  → ID de Nota Crédito
     *   GET /v1/document-types?type=ND  → ID de Nota Débito
     *   GET /v1/taxes                   → IDs de IVA 19% e IVA 5%
     *   GET /v1/payment-types           → ID de método de pago "Contado"
     */
    @Override
    @SuppressWarnings("unchecked")
    public SiigoConfigDto consultarConfiguracion() {
        String token = obtenerToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Partner-Id", partnerId);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        List<SiigoConfigDto.TipoDocumento> tiposDocumento = new ArrayList<>();
        List<SiigoConfigDto.Impuesto>      impuestos      = new ArrayList<>();
        List<SiigoConfigDto.MetodoPago>    metodosPago    = new ArrayList<>();

        // ── 1. Tipos de documento ────────────────────────────────
        for (String tipo : List.of("FV", "NC", "ND")) {
            try {
                ResponseEntity<List> resp = restTemplate.exchange(
                    baseUrl + "/v1/document-types?type=" + tipo,
                    HttpMethod.GET, entity, List.class
                );
                if (resp.getBody() != null) {
                    for (Map<?, ?> doc : (List<Map<?, ?>>) resp.getBody()) {
                        Number idNum = (Number) doc.get("id");
                        String nombre = String.valueOf(doc.get("name"));
                        String varEnv = switch (tipo) {
                            case "FV" -> "SIIGO_API_DOCUMENTO_ID";
                            case "NC" -> "SIIGO_API_NC_DOCUMENTO_ID";
                            case "ND" -> "SIIGO_API_ND_DOCUMENTO_ID";
                            default   -> "SIIGO_API_" + tipo + "_DOCUMENTO_ID";
                        };
                        tiposDocumento.add(SiigoConfigDto.TipoDocumento.builder()
                            .id(idNum != null ? idNum.intValue() : 0)
                            .tipo(tipo)
                            .nombre(nombre)
                            .variableEntorno(varEnv)
                            .build());
                    }
                }
            } catch (Exception e) {
                log.warn("[SIIGO CONFIG] Error consultando document-types type={}: {}", tipo, e.getMessage());
            }
        }

        // ── 2. Impuestos ─────────────────────────────────────────
        try {
            ResponseEntity<List> resp = restTemplate.exchange(
                baseUrl + "/v1/taxes", HttpMethod.GET, entity, List.class
            );
            if (resp.getBody() != null) {
                for (Map<?, ?> tax : (List<Map<?, ?>>) resp.getBody()) {
                    Number idNum  = (Number) tax.get("id");
                    Number pctNum = (Number) tax.get("percentage");
                    String nombre = String.valueOf(tax.get("name"));
                    double pct    = pctNum != null ? pctNum.doubleValue() : 0;
                    String varEnv = pct >= 19 ? "SIIGO_API_IVA19_ID"
                                  : pct >= 5  ? "SIIGO_API_IVA5_ID"
                                  : null;
                    impuestos.add(SiigoConfigDto.Impuesto.builder()
                        .id(idNum != null ? idNum.intValue() : 0)
                        .nombre(nombre)
                        .porcentaje(pct)
                        .variableEntorno(varEnv)
                        .build());
                }
            }
        } catch (Exception e) {
            log.warn("[SIIGO CONFIG] Error consultando taxes: {}", e.getMessage());
        }

        // ── 3. Métodos de pago ────────────────────────────────────
        try {
            ResponseEntity<List> resp = restTemplate.exchange(
                baseUrl + "/v1/payment-types?document_type=FV",
                HttpMethod.GET, entity, List.class
            );
            if (resp.getBody() != null) {
                for (Map<?, ?> pago : (List<Map<?, ?>>) resp.getBody()) {
                    Number idNum  = (Number) pago.get("id");
                    String nombre = String.valueOf(pago.get("name"));
                    // El método "Contado" es el que se usa por defecto
                    boolean esContado = nombre != null &&
                        (nombre.toUpperCase().contains("CONTADO") ||
                         nombre.toUpperCase().contains("EFECTIVO"));
                    metodosPago.add(SiigoConfigDto.MetodoPago.builder()
                        .id(idNum != null ? idNum.intValue() : 0)
                        .nombre(nombre)
                        .variableEntorno(esContado ? "SIIGO_API_PAGO_ID" : null)
                        .build());
                }
            }
        } catch (Exception e) {
            log.warn("[SIIGO CONFIG] Error consultando payment-types: {}", e.getMessage());
        }

        log.info("[SIIGO CONFIG] Configuración consultada — {} tipos doc, {} impuestos, {} pagos",
            tiposDocumento.size(), impuestos.size(), metodosPago.size());

        return SiigoConfigDto.builder()
            .modoReal(true)
            .mensaje("Conexión exitosa con Siigo. Copia los IDs en tus variables de entorno.")
            .tiposDocumento(tiposDocumento)
            .impuestos(impuestos)
            .metodosPago(metodosPago)
            .build();
    }

    // ─── CONSTRUCCIÓN DEL PAYLOAD ────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> construirPayload(FacturaSiigoRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();

        // Tipo de documento (Factura de Venta Electrónica)
        payload.put("document", Map.of("id", documentoId));

        // Cliente
        payload.put("customer", Map.of(
            "identification", request.getClienteNit(),
            "branch_office", 0
        ));

        // Moneda
        payload.put("currency", Map.of("code", "COP", "exchange_rate", 0));

        // Fecha
        payload.put("date", request.getFecha().toString());

        // Líneas de producto
        List<Map<String, Object>> items = new ArrayList<>();
        for (LineaFacturaDto linea : request.getLineas()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", linea.getCodigoProducto());
            item.put("description", linea.getDescripcion());
            item.put("quantity", linea.getCantidad().doubleValue());
            item.put("price", linea.getPrecioUnitario().doubleValue());
            item.put("discount", 0);

            // Impuestos según porcentaje
            if (linea.getPorcentajeIva() != null &&
                linea.getPorcentajeIva().compareTo(BigDecimal.ZERO) > 0) {
                int taxId = linea.getPorcentajeIva().intValue() >= 19 ? iva19Id : iva5Id;
                item.put("taxes", List.of(Map.of("id", taxId)));
            }
            items.add(item);
        }
        payload.put("items", items);

        // Forma de pago — due_date = fecha de vencimiento (crédito) o fecha de emisión (contado)
        LocalDate dueDate = request.getFechaVencimiento() != null
            ? request.getFechaVencimiento() : request.getFecha();
        payload.put("payments", List.of(Map.of(
            "id", pagoId,
            "value", request.getTotal().doubleValue(),
            "due_date", dueDate.toString()
        )));

        return payload;
    }

    /**
     * Payload para nota crédito o débito.
     * Siigo requiere document.id DIFERENTE al de facturas regulares:
     *   credit_note → ncDocumentoId  (GET /v1/document-types?type=NC)
     *   debit_note  → ndDocumentoId  (GET /v1/document-types?type=ND)
     */
    private Map<String, Object> construirPayloadNota(NotaSiigoRequest request, String tipo) {
        int docId = "credit_note".equals(tipo) ? ncDocumentoId : ndDocumentoId;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("document", Map.of("id", docId));
        payload.put("customer", Map.of("identification", request.getClienteNit(), "branch_office", 0));
        payload.put("date", request.getFecha().toString());
        payload.put("observations", request.getMotivo() != null ? request.getMotivo() : "");

        // Referencia obligatoria a la factura electrónica original (CUFE de Siigo)
        if (request.getFacturaSiigoId() != null) {
            payload.put("invoice", Map.of("id", request.getFacturaSiigoId()));
        } else {
            // Si no hay siigoId (ej. facturas antes de la integración), referenciar por número
            log.warn("[SIIGO] Nota {} sin siigoId de factura — se envía sin referencia invoice.id", tipo);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        if (request.getLineas() != null) {
            for (LineaFacturaDto linea : request.getLineas()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", linea.getCodigoProducto());
                item.put("description", linea.getDescripcion());
                item.put("quantity", linea.getCantidad().doubleValue());
                item.put("price", linea.getPrecioUnitario().doubleValue());
                item.put("discount", 0);
                if (linea.getPorcentajeIva() != null &&
                    linea.getPorcentajeIva().compareTo(BigDecimal.ZERO) > 0) {
                    int taxId = linea.getPorcentajeIva().intValue() >= 19 ? iva19Id : iva5Id;
                    item.put("taxes", List.of(Map.of("id", taxId)));
                }
                items.add(item);
            }
        }
        payload.put("items", items);
        // Nota: Siigo acepta payments en NC/ND pero no es obligatorio; se incluye por completitud
        payload.put("payments", List.of(Map.of(
            "id", pagoId,
            "value", request.getTotal().doubleValue(),
            "due_date", request.getFecha().toString()
        )));
        return payload;
    }
}
