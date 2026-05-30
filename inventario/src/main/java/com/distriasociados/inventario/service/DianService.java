package com.distriasociados.inventario.service;

import com.distriasociados.inventario.dto.response.FacturaDianResponse;
import com.distriasociados.inventario.dto.response.FacturaDianResponse.LineaFactura;
import com.distriasociados.inventario.dto.response.FacturaDianResponse.ProductoSugerido;
import com.distriasociados.inventario.entity.Producto;
import com.distriasociados.inventario.repository.CompraRepository;
import com.distriasociados.inventario.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Servicio para importación de facturas electrónicas de la DIAN.
 *
 * Dos modos de operación:
 *  1. consultarPorCufe()  — valida formato del CUFE, detecta duplicados,
 *                           devuelve sugerencias del historial de compras al proveedor.
 *  2. parsearXml()        — parsea el archivo XML UBL 2.1 que envía el proveedor
 *                           y extrae TODOS los datos: cabecera + líneas de producto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DianService {

    private final CompraRepository  compraRepository;
    private final ProductoRepository productoRepository;

    // ── REGEX para validar el formato del CUFE (SHA-384 = 96 caracteres hex) ──────
    private static final java.util.regex.Pattern CUFE_PATTERN =
        java.util.regex.Pattern.compile("[0-9a-fA-F]{96}");

    // ─────────────────────────────────────────────────────────────────────────────
    //  MODO 1: Consulta por CUFE
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Valida el CUFE, verifica duplicados y carga las sugerencias del historial.
     * NO consulta la DIAN directamente (el portal DIAN es una SPA que requiere JS).
     * El auxiliar llena el encabezado manualmente; las sugerencias de productos
     * son el principal ahorro de tiempo.
     */
    public FacturaDianResponse consultarPorCufe(String cufe) {
        if (cufe == null || cufe.isBlank()) {
            throw new RuntimeException("El CUFE no puede estar vacío");
        }

        cufe = cufe.trim().toLowerCase();

        // Validar formato: 96 caracteres hexadecimales (SHA-384)
        if (!CUFE_PATTERN.matcher(cufe).matches()) {
            throw new RuntimeException(
                "Formato de CUFE inválido. Debe tener exactamente 96 caracteres hexadecimales. " +
                "Verifique que copió el CUFE completo del reporte DIAN.");
        }

        // Verificar si ya fue registrado (anti-duplicado)
        boolean duplicado = compraRepository.existsByCufe(cufe);

        log.info("[DIAN] Consulta CUFE: {} — duplicado={}", cufe.substring(0, 8) + "...", duplicado);

        return FacturaDianResponse.builder()
            .cufe(cufe)
            .fuente("CUFE")
            .duplicado(duplicado)
            .build();
    }

    /**
     * Dado el nombre de un proveedor, devuelve los productos que se han comprado
     * anteriormente a ese proveedor (para pre-llenar el formulario automáticamente).
     */
    public List<ProductoSugerido> obtenerSugerencias(String nombreProveedor) {
        if (nombreProveedor == null || nombreProveedor.isBlank()) return List.of();

        List<Object[]> historial = compraRepository
            .findHistorialProductosProveedor(nombreProveedor.trim());

        // De-duplicar: quedarnos solo con la compra más reciente de cada producto
        Map<Long, ProductoSugerido> vistos = new LinkedHashMap<>();

        for (Object[] row : historial) {
            Long productoId = ((Number) row[0]).longValue();
            if (vistos.containsKey(productoId)) continue; // ya tenemos la más reciente

            BigDecimal precio = row[1] != null
                ? new BigDecimal(row[1].toString()) : null;
            Integer cantidad = row[2] != null
                ? ((Number) row[2]).intValue() : null;
            LocalDate fecha = row[3] != null
                ? (row[3] instanceof LocalDate ld ? ld
                    : LocalDate.parse(row[3].toString().substring(0, 10)))
                : null;

            productoRepository.findById(productoId).ifPresent(prod ->
                vistos.put(productoId, ProductoSugerido.builder()
                    .productoId(productoId)
                    .nombreProducto(prod.getNombreCompleto())
                    .ultimoPrecioCaja(precio)
                    .ultimaCantidad(cantidad)
                    .ultimaCompra(fecha)
                    .build())
            );
        }

        return new ArrayList<>(vistos.values());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  MODO 2: Parseo del XML UBL 2.1 (factura electrónica colombiana)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Parsea el archivo XML UBL 2.1 que el proveedor envía con cada factura electrónica.
     * Extrae: CUFE, proveedor, NIT, N° factura, fecha, total, y todas las líneas de producto.
     * Cada línea incluye: descripción, cantidad, precio unitario, IVA, subtotal.
     * Si el nombre de un producto coincide aproximadamente con uno del catálogo,
     * lo pre-selecciona para acelerar el mapeo.
     */
    public FacturaDianResponse parsearXml(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Seguridad: desactivar entidades externas (protección XXE)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xmlBytes));
            doc.getDocumentElement().normalize();

            // ── Encabezado ────────────────────────────────────────────────────
            String cufe          = extraerCufe(doc);
            String numeroFactura = getText(doc, "ID", 0);
            String fechaStr      = getText(doc, "IssueDate", 0);
            String proveedor     = extraerProveedor(doc);
            String proveedorNit  = extraerProveedorNit(doc);
            BigDecimal total     = extraerTotal(doc);

            LocalDate fecha = null;
            try { fecha = LocalDate.parse(fechaStr); } catch (Exception ignored) {}

            // Verificar duplicado si hay CUFE
            boolean duplicado = cufe != null && compraRepository.existsByCufe(cufe);

            // ── Líneas de producto ────────────────────────────────────────────
            List<LineaFactura> lineas = extraerLineas(doc);

            log.info("[DIAN] XML parseado — proveedor='{}' NIT={} factura={} líneas={}",
                proveedor, proveedorNit, numeroFactura, lineas.size());

            return FacturaDianResponse.builder()
                .cufe(cufe)
                .fuente("XML")
                .duplicado(duplicado)
                .proveedor(proveedor)
                .proveedorNit(proveedorNit)
                .numeroFactura(numeroFactura)
                .fecha(fecha)
                .total(total)
                .lineas(lineas)
                .build();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[DIAN] Error parseando XML: {}", e.getMessage());
            throw new RuntimeException(
                "No se pudo procesar el archivo XML. Verifique que sea una factura electrónica " +
                "colombiana en formato UBL 2.1. Detalle: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Helpers de parseo XML
    // ─────────────────────────────────────────────────────────────────────────────

    /** Obtiene el texto del n-ésimo elemento con ese nombre local (ignora namespace). */
    private String getText(Document doc, String localName, int index) {
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() > index) {
            return nodes.item(index).getTextContent().trim();
        }
        return null;
    }

    /**
     * El CUFE en la factura colombiana se encuentra en cbc:UUID con
     * schemeID="CUFE-SHA384".
     */
    private String extraerCufe(Document doc) {
        NodeList uuids = doc.getElementsByTagNameNS("*", "UUID");
        for (int i = 0; i < uuids.getLength(); i++) {
            Element el = (Element) uuids.item(i);
            String scheme = el.getAttribute("schemeID");
            if ("CUFE-SHA384".equalsIgnoreCase(scheme)) {
                return el.getTextContent().trim().toLowerCase();
            }
        }
        // Fallback: primer UUID que encuentre
        if (uuids.getLength() > 0) {
            return uuids.item(0).getTextContent().trim().toLowerCase();
        }
        return null;
    }

    /**
     * Nombre del proveedor desde AccountingSupplierParty.
     * Intenta RegistrationName primero, luego Name.
     */
    private String extraerProveedor(Document doc) {
        // RegistrationName es el nombre oficial registrado en la DIAN
        NodeList names = doc.getElementsByTagNameNS("*", "RegistrationName");
        if (names.getLength() > 0) {
            return names.item(0).getTextContent().trim();
        }
        // Fallback
        NodeList nameNodes = doc.getElementsByTagNameNS("*", "Name");
        if (nameNodes.getLength() > 0) {
            return nameNodes.item(0).getTextContent().trim();
        }
        return null;
    }

    /**
     * NIT del proveedor (CompanyID del AccountingSupplierParty).
     */
    private String extraerProveedorNit(Document doc) {
        NodeList companyIds = doc.getElementsByTagNameNS("*", "CompanyID");
        if (companyIds.getLength() > 0) {
            return companyIds.item(0).getTextContent().trim();
        }
        return null;
    }

    /** Total a pagar (PayableAmount dentro de LegalMonetaryTotal). */
    private BigDecimal extraerTotal(Document doc) {
        NodeList nodes = doc.getElementsByTagNameNS("*", "PayableAmount");
        if (nodes.getLength() > 0) {
            try { return new BigDecimal(nodes.item(0).getTextContent().trim()); }
            catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Extrae todas las líneas del InvoiceLine y trata de hacer matching
     * con productos de nuestro catálogo por similitud de nombre.
     */
    private List<LineaFactura> extraerLineas(Document doc) {
        List<LineaFactura> resultado = new ArrayList<>();
        List<Producto> catalogo = productoRepository.findByActivoTrue();

        NodeList invoiceLines = doc.getElementsByTagNameNS("*", "InvoiceLine");
        for (int i = 0; i < invoiceLines.getLength(); i++) {
            Element linea = (Element) invoiceLines.item(i);

            String descripcion = getChildText(linea, "Description");
            BigDecimal cantidad = parseBD(getChildText(linea, "InvoicedQuantity"));
            BigDecimal precioU  = parseBD(getChildText(linea, "PriceAmount"));
            BigDecimal subtotal = parseBD(getChildText(linea, "LineExtensionAmount"));
            BigDecimal iva      = parseBD(getChildText(linea, "Percent"));

            // Buscar match en nuestro catálogo (por similitud de nombre)
            Long   productoIdSugerido    = null;
            String productoNombreSugerido = null;
            if (descripcion != null) {
                Producto match = buscarProductoSimilar(descripcion, catalogo);
                if (match != null) {
                    productoIdSugerido     = match.getId();
                    productoNombreSugerido = match.getNombreCompleto();
                }
            }

            resultado.add(LineaFactura.builder()
                .descripcionDian(descripcion)
                .cantidad(cantidad)
                .precioUnitario(precioU)
                .subtotal(subtotal)
                .porcentajeIva(iva != null ? iva : BigDecimal.ZERO)
                .productoIdSugerido(productoIdSugerido)
                .productoNombreSugerido(productoNombreSugerido)
                .build());
        }
        return resultado;
    }

    /** Busca el primer hijo con ese nombre local dentro de un elemento. */
    private String getChildText(Element parent, String localName) {
        NodeList children = parent.getElementsByTagNameNS("*", localName);
        if (children.getLength() > 0) {
            return children.item(0).getTextContent().trim();
        }
        return null;
    }

    private BigDecimal parseBD(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value.replace(",", ".")); }
        catch (Exception e) { return null; }
    }

    /**
     * Busca el producto de nuestro catálogo cuyo nombre más se parece a la
     * descripción de la DIAN. Estrategia: coincidencia de palabras clave.
     *
     * Ejemplo: "COCA COLA ORIGINAL 600ML PET CAJA X 24"
     *   → coincide con Producto "COCA COLA 600ML PET (24)"
     */
    private Producto buscarProductoSimilar(String descripcionDian, List<Producto> catalogo) {
        String desc = descripcionDian.toLowerCase();
        Producto mejorMatch = null;
        int mejorPuntuacion = 0;

        for (Producto p : catalogo) {
            String nombre = p.getNombreCompleto().toLowerCase();
            int puntos = contarPalabrasComunes(desc, nombre);
            if (puntos > mejorPuntuacion && puntos >= 2) {
                mejorPuntuacion = puntos;
                mejorMatch = p;
            }
        }
        return mejorMatch;
    }

    private int contarPalabrasComunes(String a, String b) {
        Set<String> palabrasA = new HashSet<>(Arrays.asList(a.split("[\\s,./\\-()]+")));
        Set<String> palabrasB = new HashSet<>(Arrays.asList(b.split("[\\s,./\\-()]+")));
        // Ignorar palabras muy cortas o números de unidades genéricos
        palabrasA.removeIf(p -> p.length() < 3 || p.matches("\\d+"));
        int comunes = 0;
        for (String p : palabrasA) {
            if (palabrasB.contains(p)) comunes++;
        }
        return comunes;
    }
}
