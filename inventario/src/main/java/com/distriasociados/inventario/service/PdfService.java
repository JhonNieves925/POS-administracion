package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.Cargue;
import com.distriasociados.inventario.entity.CargueDetalle;
import com.distriasociados.inventario.entity.Remision;
import com.distriasociados.inventario.entity.RemisionDetalle;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class PdfService {

    @Value("${spring.application.name:DISTRIASOCIADOS SAS}")
    private String appName;

    // ── Datos empresa para PDF factura electrónica ────────────
    @Value("${empresa.nombre:DISTRIASOCIADOS SAS}")        private String empNombre;
    @Value("${empresa.nit:901.897.762-1}")                 private String empNit;
    @Value("${empresa.direccion:CRA 3 N 21A 64}")          private String empDireccion;
    @Value("${empresa.telefono:(602) 3178547638}")         private String empTelefono;
    @Value("${empresa.ciudad:La Union - Colombia}")        private String empCiudad;
    @Value("${empresa.correo:distriasociados2024@gmail.com}") private String empCorreo;
    @Value("${empresa.ciiu:4632}")                         private String empCiiu;
    @Value("${empresa.actividad:Comercio al por mayor de bebidas y tabaco}") private String empActividad;
    @Value("${empresa.resolucion.numero:}")                private String resNumero;
    @Value("${empresa.resolucion.fecha:}")                 private String resFecha;
    @Value("${empresa.resolucion.prefijo:FEV}")            private String resPrefijo;
    @Value("${empresa.resolucion.desde:1}")                private String resDesde;
    @Value("${empresa.resolucion.hasta:2000}")             private String resHasta;
    @Value("${empresa.resolucion.vigencia:24 Meses}")      private String resVigencia;

    private static final Font FONT_TITULO = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, BaseColor.WHITE);
    private static final Font FONT_HEADER = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.WHITE);
    private static final Font FONT_NORMAL = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL);
    private static final Font FONT_BOLD   = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD);
    private static final Font FONT_SMALL  = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.GRAY);
    private static final Font FONT_MICRO  = new Font(Font.FontFamily.HELVETICA, 7, Font.NORMAL, BaseColor.GRAY);
    private static final Font FONT_DIAN_TITULO = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, new BaseColor(211,47,47));
    private static final BaseColor COLOR_ROJO = new BaseColor(211, 47, 47);
    private static final BaseColor COLOR_GRIS = new BaseColor(245, 245, 245);
    private static final BaseColor COLOR_BORDE = new BaseColor(200, 200, 200);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm");

    private final NumberFormat formatCOP = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    public byte[] generarRemisionCargue(Cargue cargue, List<Remision> remisiones) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Encabezado
            agregarEncabezado(document, cargue);

            // Espacio
            document.add(Chunk.NEWLINE);

            // Info del cargue
            agregarInfoCargue(document, cargue);

            document.add(Chunk.NEWLINE);

            // Tabla de ventas reales (agregadas de remisiones activas)
            if (remisiones != null && !remisiones.isEmpty()) {
                agregarTablaVentasReales(document, remisiones);
                document.add(Chunk.NEWLINE);
                agregarTotalesVentas(document, remisiones);
                document.add(Chunk.NEWLINE);
                document.add(Chunk.NEWLINE);
                agregarHistorialVentas(document, remisiones);
            } else {
                // Sin remisiones: mostrar tabla vacía
                agregarTablaProductos(document, cargue);
                document.add(Chunk.NEWLINE);
                agregarTotales(document, cargue);
            }

            document.add(Chunk.NEWLINE);

            // Firmas
            agregarFirmas(document);

            // Footer
            agregarFooter(document);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando PDF de remision para cargue {}", cargue.getId(), e);
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }

    /**
     * Tabla "Resumen de lo vendido" — agrupada por producto desde remisiones activas.
     * Reemplaza la tabla de cargue para evitar confusión entre "cargado" y "vendido".
     */
    private void agregarTablaVentasReales(Document doc, List<Remision> remisiones) throws DocumentException {
        // Agregar por producto (solo remisiones no anuladas)
        java.util.LinkedHashMap<String, long[]> porProducto = new java.util.LinkedHashMap<>();
        // long[0] = cantidad uds, long[1] = subtotal centavos (para evitar BigDecimal map)
        java.util.Map<String, BigDecimal> subtotalMap = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> upcMap = new java.util.LinkedHashMap<>();

        for (Remision rem : remisiones) {
            if (rem.getEstado() != null && rem.getEstado().name().equals("ANULADA")) continue;
            if (rem.getDetalles() == null) continue;
            for (RemisionDetalle det : rem.getDetalles()) {
                String key = det.getDescripcionProducto();
                int qty = det.getCantidad() != null ? det.getCantidad().intValue() : 0;
                BigDecimal sub = det.getSubtotal() != null ? det.getSubtotal() : BigDecimal.ZERO;
                porProducto.merge(key, new long[]{qty}, (a, b) -> new long[]{a[0] + qty});
                subtotalMap.merge(key, sub, BigDecimal::add);
                if (!upcMap.containsKey(key) && det.getProducto() != null)
                    upcMap.put(key, det.getProducto().getUnidadesPorCaja() > 0 ? det.getProducto().getUnidadesPorCaja() : 1);
            }
        }

        PdfPTable tabla = new PdfPTable(3);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{5f, 2.5f, 2.5f});
        tabla.setSpacingBefore(5);

        // Cabeceras
        for (String h : new String[]{"Producto vendido", "Cantidad", "Subtotal"}) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FONT_HEADER));
            cell.setBackgroundColor(COLOR_ROJO);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorder(Rectangle.NO_BORDER);
            tabla.addCell(cell);
        }

        if (porProducto.isEmpty()) {
            PdfPCell vacia = new PdfPCell(new Phrase("Sin ventas registradas", FONT_SMALL));
            vacia.setColspan(3);
            vacia.setBorder(Rectangle.NO_BORDER);
            vacia.setPadding(8);
            vacia.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabla.addCell(vacia);
        } else {
            boolean gris = false;
            for (java.util.Map.Entry<String, long[]> e : porProducto.entrySet()) {
                String key = e.getKey();
                int qty = (int) e.getValue()[0];
                int upk = upcMap.getOrDefault(key, 1);
                BigDecimal sub = subtotalMap.getOrDefault(key, BigDecimal.ZERO);
                BaseColor bg = gris ? COLOR_GRIS : BaseColor.WHITE;

                String cantStr = upk > 1
                    ? ((int) Math.floor((double) qty / upk)) + " caj (" + qty + " und)"
                    : qty + " und";

                agregarCeldaTabla(tabla, key, bg, Element.ALIGN_LEFT);
                agregarCeldaTabla(tabla, cantStr, bg, Element.ALIGN_CENTER);
                agregarCeldaTabla(tabla, formatCOP.format(sub), bg, Element.ALIGN_RIGHT);
                gris = !gris;
            }
        }
        doc.add(tabla);
    }

    /** Totales calculados desde remisiones activas (no anuladas). */
    private void agregarTotalesVentas(Document doc, List<Remision> remisiones) throws DocumentException {
        long activas = 0;
        BigDecimal totalCobrado = BigDecimal.ZERO;

        for (Remision rem : remisiones) {
            if (rem.getEstado() != null && rem.getEstado().name().equals("ANULADA")) continue;
            activas++;
            totalCobrado = totalCobrado.add(rem.getTotal() != null ? rem.getTotal() : BigDecimal.ZERO);
        }

        PdfPTable totales = new PdfPTable(2);
        totales.setWidthPercentage(50);
        totales.setHorizontalAlignment(Element.ALIGN_RIGHT);

        agregarFilaTotalInfo(totales, "Ventas realizadas:", activas + " remisión" + (activas != 1 ? "es" : ""));

        PdfPCell labelTotal = new PdfPCell(new Phrase("TOTAL COBRADO:", new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD)));
        labelTotal.setBorder(Rectangle.TOP);
        labelTotal.setPadding(5);
        labelTotal.setBackgroundColor(COLOR_GRIS);
        totales.addCell(labelTotal);

        PdfPCell valTotal = new PdfPCell(new Phrase(formatCOP.format(totalCobrado),
            new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, COLOR_ROJO)));
        valTotal.setBorder(Rectangle.TOP);
        valTotal.setPadding(5);
        valTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totales.addCell(valTotal);

        doc.add(totales);
    }

    private void agregarHistorialVentas(Document doc, List<Remision> remisiones) throws DocumentException {
        // Título de sección
        Paragraph titulo = new Paragraph("HISTORIAL DE VENTAS DEL DÍA",
            new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, COLOR_ROJO));
        titulo.setSpacingAfter(6);
        doc.add(titulo);

        for (Remision rem : remisiones) {
            // Cabecera de cada remision
            PdfPTable remHead = new PdfPTable(3);
            remHead.setWidthPercentage(100);
            remHead.setWidths(new float[]{2.5f, 3.5f, 2f});
            remHead.setSpacingBefore(4);
            remHead.setSpacingAfter(2);

            PdfPCell cNumero = new PdfPCell(new Phrase(rem.getNumero(), FONT_BOLD));
            cNumero.setBorder(Rectangle.NO_BORDER);
            cNumero.setBackgroundColor(COLOR_GRIS);
            cNumero.setPadding(4);
            remHead.addCell(cNumero);

            String clienteNombre = rem.getCliente() != null ? rem.getCliente().getRazonSocial() : "Consumidor Final";
            PdfPCell cCliente = new PdfPCell(new Phrase(clienteNombre, FONT_NORMAL));
            cCliente.setBorder(Rectangle.NO_BORDER);
            cCliente.setBackgroundColor(COLOR_GRIS);
            cCliente.setPadding(4);
            remHead.addCell(cCliente);

            boolean anulada = rem.getEstado() != null && rem.getEstado().name().equals("ANULADA");
            String totalStr = anulada ? "ANULADA" : formatCOP.format(rem.getTotal() != null ? rem.getTotal() : 0);
            Font totalFont = anulada
                ? new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, new BaseColor(211, 47, 47))
                : new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, new BaseColor(46, 125, 50));
            PdfPCell cTotal = new PdfPCell(new Phrase(totalStr, totalFont));
            cTotal.setBorder(Rectangle.NO_BORDER);
            cTotal.setBackgroundColor(COLOR_GRIS);
            cTotal.setPadding(4);
            cTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            remHead.addCell(cTotal);

            doc.add(remHead);

            // Detalles de la remision
            if (rem.getDetalles() != null && !rem.getDetalles().isEmpty()) {
                PdfPTable detTable = new PdfPTable(3);
                detTable.setWidthPercentage(95);
                detTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
                detTable.setWidths(new float[]{5f, 2f, 2f});
                detTable.setSpacingAfter(3);

                for (RemisionDetalle det : rem.getDetalles()) {
                    int upk = det.getProducto() != null && det.getProducto().getUnidadesPorCaja() > 0
                        ? det.getProducto().getUnidadesPorCaja() : 1;
                    int cantidad = det.getCantidad() != null ? det.getCantidad().intValue() : 0;
                    String cantStr = upk > 1
                        ? Math.floor((double) cantidad / upk) + " caj (" + cantidad + " und)"
                        : cantidad + " und";

                    PdfPCell cProd = new PdfPCell(new Phrase(det.getDescripcionProducto(), FONT_SMALL));
                    cProd.setBorder(Rectangle.BOTTOM);
                    cProd.setBorderColor(new BaseColor(230, 230, 230));
                    cProd.setPadding(3);
                    detTable.addCell(cProd);

                    PdfPCell cCant = new PdfPCell(new Phrase(cantStr, FONT_SMALL));
                    cCant.setBorder(Rectangle.BOTTOM);
                    cCant.setBorderColor(new BaseColor(230, 230, 230));
                    cCant.setPadding(3);
                    cCant.setHorizontalAlignment(Element.ALIGN_CENTER);
                    detTable.addCell(cCant);

                    PdfPCell cSub = new PdfPCell(new Phrase(
                        formatCOP.format(det.getSubtotal() != null ? det.getSubtotal() : BigDecimal.ZERO), FONT_SMALL));
                    cSub.setBorder(Rectangle.BOTTOM);
                    cSub.setBorderColor(new BaseColor(230, 230, 230));
                    cSub.setPadding(3);
                    cSub.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    detTable.addCell(cSub);
                }
                doc.add(detTable);
            }
        }
    }

    private void agregarEncabezado(Document doc, Cargue cargue) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{3, 1});

        // Empresa
        PdfPCell celdaEmpresa = new PdfPCell();
        celdaEmpresa.setBackgroundColor(COLOR_ROJO);
        celdaEmpresa.setPadding(10);
        celdaEmpresa.setBorder(Rectangle.NO_BORDER);

        Paragraph empresa = new Paragraph("DISTRIASOCIADOS SAS", FONT_TITULO);
        empresa.add(Chunk.NEWLINE);
        empresa.add(new Chunk("NIT: Pendiente configuracion", new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.WHITE)));
        empresa.add(Chunk.NEWLINE);
        empresa.add(new Chunk("La Union, Valle del Cauca - Colombia", new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.WHITE)));
        celdaEmpresa.addElement(empresa);
        header.addCell(celdaEmpresa);

        // Numero documento
        PdfPCell celdaNum = new PdfPCell();
        celdaNum.setBackgroundColor(new BaseColor(30, 30, 50));
        celdaNum.setPadding(10);
        celdaNum.setBorder(Rectangle.NO_BORDER);
        celdaNum.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph numDoc = new Paragraph("REMISION", new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE));
        numDoc.setAlignment(Element.ALIGN_CENTER);
        numDoc.add(Chunk.NEWLINE);
        numDoc.add(new Chunk(cargue.getNumero(), new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, new BaseColor(211, 47, 47))));
        numDoc.add(Chunk.NEWLINE);
        if (cargue.getFecha() != null) {
            numDoc.add(new Chunk(cargue.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.WHITE)));
        }
        celdaNum.addElement(numDoc);
        header.addCell(celdaNum);

        doc.add(header);
    }

    private void agregarInfoCargue(Document doc, Cargue cargue) throws DocumentException {
        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setSpacingBefore(5);

        agregarFilaInfo(info, "Vendedor:", cargue.getVendedor() != null ? cargue.getVendedor().getNombre() : "-");
        agregarFilaInfo(info, "Cedula:", cargue.getVendedor() != null ? cargue.getVendedor().getCedula() : "-");
        agregarFilaInfo(info, "Estado:", cargue.getEstado() != null ? cargue.getEstado().name() : "-");

        doc.add(info);
    }

    private void agregarFilaInfo(PdfPTable table, String label, String value) {
        PdfPCell cLabel = new PdfPCell(new Phrase(label, FONT_BOLD));
        cLabel.setBorder(Rectangle.NO_BORDER);
        cLabel.setPadding(3);
        cLabel.setBackgroundColor(COLOR_GRIS);
        table.addCell(cLabel);

        PdfPCell cValue = new PdfPCell(new Phrase(value, FONT_NORMAL));
        cValue.setBorder(Rectangle.NO_BORDER);
        cValue.setPadding(3);
        table.addCell(cValue);
    }

    private void agregarTablaProductos(Document doc, Cargue cargue) throws DocumentException {
        PdfPTable tabla = new PdfPTable(5);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{4, 2, 2, 2, 2.5f});
        tabla.setSpacingBefore(5);

        // Cabeceras
        String[] headers = {"Producto", "Cargado", "Devuelto", "Vendido", "Subtotal"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FONT_HEADER));
            cell.setBackgroundColor(COLOR_ROJO);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBorder(Rectangle.NO_BORDER);
            tabla.addCell(cell);
        }

        // Filas
        boolean gris = false;
        if (cargue.getDetalles() != null) {
            for (CargueDetalle det : cargue.getDetalles()) {
                BaseColor bg = gris ? COLOR_GRIS : BaseColor.WHITE;
                int vendido = det.getCantidadCargue() - (det.getCantidadDevolucion() != null ? det.getCantidadDevolucion() : 0);
                double subtotal = det.getSubtotalVenta() != null ? det.getSubtotalVenta().doubleValue() : 0.0;

                agregarCeldaTabla(tabla, det.getProducto() != null ? det.getProducto().getNombreCompleto() : "-", bg, Element.ALIGN_LEFT);
                agregarCeldaTabla(tabla, String.valueOf(det.getCantidadCargue()), bg, Element.ALIGN_CENTER);
                agregarCeldaTabla(tabla, String.valueOf(det.getCantidadDevolucion() != null ? det.getCantidadDevolucion() : 0), bg, Element.ALIGN_CENTER);
                agregarCeldaTabla(tabla, String.valueOf(vendido), bg, Element.ALIGN_CENTER);
                agregarCeldaTabla(tabla, formatCOP.format(subtotal), bg, Element.ALIGN_RIGHT);
                gris = !gris;
            }
        }

        doc.add(tabla);
    }

    private void agregarCeldaTabla(PdfPTable tabla, String texto, BaseColor bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, FONT_NORMAL));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setHorizontalAlignment(align);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(new BaseColor(220, 220, 220));
        cell.setBorderWidth(0.5f);
        tabla.addCell(cell);
    }

    private void agregarTotales(Document doc, Cargue cargue) throws DocumentException {
        double totalVenta = 0;
        int totalCargado = 0;
        int totalVendido = 0;

        if (cargue.getDetalles() != null) {
            for (CargueDetalle det : cargue.getDetalles()) {
                totalCargado += det.getCantidadCargue();
                totalVendido += det.getCantidadCargue() - (det.getCantidadDevolucion() != null ? det.getCantidadDevolucion() : 0);
                totalVenta += det.getSubtotalVenta() != null ? det.getSubtotalVenta().doubleValue() : 0.0;
            }
        }

        PdfPTable totales = new PdfPTable(2);
        totales.setWidthPercentage(50);
        totales.setHorizontalAlignment(Element.ALIGN_RIGHT);

        agregarFilaTotalInfo(totales, "Total unidades cargadas:", String.valueOf(totalCargado));
        agregarFilaTotalInfo(totales, "Total unidades vendidas:", String.valueOf(totalVendido));

        PdfPCell labelTotal = new PdfPCell(new Phrase("TOTAL VENTA:", new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD)));
        labelTotal.setBorder(Rectangle.TOP);
        labelTotal.setPadding(5);
        labelTotal.setBackgroundColor(COLOR_GRIS);
        totales.addCell(labelTotal);

        PdfPCell valTotal = new PdfPCell(new Phrase(formatCOP.format(totalVenta), new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, COLOR_ROJO)));
        valTotal.setBorder(Rectangle.TOP);
        valTotal.setPadding(5);
        valTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totales.addCell(valTotal);

        doc.add(totales);
    }

    private void agregarFilaTotalInfo(PdfPTable table, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, FONT_SMALL));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPadding(3);
        table.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value, FONT_NORMAL));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPadding(3);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(v);
    }

    private void agregarFirmas(Document doc) throws DocumentException {
        PdfPTable firmas = new PdfPTable(2);
        firmas.setWidthPercentage(100);
        firmas.setSpacingBefore(40);

        PdfPCell firma1 = new PdfPCell();
        firma1.setBorder(Rectangle.NO_BORDER);
        firma1.setPadding(5);
        Paragraph p1 = new Paragraph("_________________________________\nFirma Vendedor", FONT_NORMAL);
        p1.setAlignment(Element.ALIGN_CENTER);
        firma1.addElement(p1);
        firmas.addCell(firma1);

        PdfPCell firma2 = new PdfPCell();
        firma2.setBorder(Rectangle.NO_BORDER);
        firma2.setPadding(5);
        Paragraph p2 = new Paragraph("_________________________________\nFirma Administrador / Auxiliar", FONT_NORMAL);
        p2.setAlignment(Element.ALIGN_CENTER);
        firma2.addElement(p2);
        firmas.addCell(firma2);

        doc.add(firmas);
    }

    // ─── PDF DE REMISION DE VENTA ────────────────────────────

    public byte[] generarRemisionVenta(Remision remision) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Encabezado
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{3, 1});

            PdfPCell celdaEmpresa = new PdfPCell();
            celdaEmpresa.setBackgroundColor(COLOR_ROJO);
            celdaEmpresa.setPadding(10);
            celdaEmpresa.setBorder(Rectangle.NO_BORDER);
            Paragraph emp = new Paragraph("DISTRIASOCIADOS SAS", FONT_TITULO);
            emp.add(Chunk.NEWLINE);
            emp.add(new Chunk("La Union, Valle del Cauca - Colombia",
                new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.WHITE)));
            celdaEmpresa.addElement(emp);
            header.addCell(celdaEmpresa);

            PdfPCell celdaNum = new PdfPCell();
            celdaNum.setBackgroundColor(new BaseColor(30, 30, 50));
            celdaNum.setPadding(10);
            celdaNum.setBorder(Rectangle.NO_BORDER);
            Paragraph numDoc = new Paragraph("REMISION DE VENTA",
                new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.WHITE));
            numDoc.setAlignment(Element.ALIGN_CENTER);
            numDoc.add(Chunk.NEWLINE);
            numDoc.add(new Chunk(remision.getNumero(),
                new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, new BaseColor(211, 47, 47))));
            numDoc.add(Chunk.NEWLINE);
            numDoc.add(new Chunk(remision.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.WHITE)));
            celdaNum.addElement(numDoc);
            header.addCell(celdaNum);
            document.add(header);

            // Info cliente y vendedor
            document.add(Chunk.NEWLINE);
            PdfPTable info = new PdfPTable(2);
            info.setWidthPercentage(100);
            agregarFilaInfo(info, "Cliente:", remision.getCliente() != null
                ? remision.getCliente().getRazonSocial() : "-");
            agregarFilaInfo(info, "NIT:", remision.getCliente() != null
                ? remision.getCliente().getNit() : "-");
            agregarFilaInfo(info, "Vendedor:", remision.getVendedor() != null
                ? remision.getVendedor().getNombre() : "-");
            document.add(info);

            // Tabla de productos
            document.add(Chunk.NEWLINE);
            PdfPTable tabla = new PdfPTable(5);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{4, 1.5f, 2, 1.5f, 2.5f});

            String[] headers = {"Producto", "Cant.", "Precio Unit.", "IVA %", "Total"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FONT_HEADER));
                cell.setBackgroundColor(COLOR_ROJO);
                cell.setPadding(6);
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                tabla.addCell(cell);
            }

            boolean gris = false;
            if (remision.getDetalles() != null) {
                for (RemisionDetalle det : remision.getDetalles()) {
                    BaseColor bg = gris ? COLOR_GRIS : BaseColor.WHITE;
                    agregarCeldaTabla(tabla, det.getDescripcionProducto() != null
                        ? det.getDescripcionProducto() : "-", bg, Element.ALIGN_LEFT);
                    agregarCeldaTabla(tabla,
                        det.getCantidad() != null ? det.getCantidad().intValue() + "" : "0",
                        bg, Element.ALIGN_CENTER);
                    agregarCeldaTabla(tabla,
                        det.getPrecioUnitario() != null ? formatCOP.format(det.getPrecioUnitario()) : "-",
                        bg, Element.ALIGN_RIGHT);
                    agregarCeldaTabla(tabla,
                        det.getPorcentajeIva() != null ? det.getPorcentajeIva().intValue() + "%" : "0%",
                        bg, Element.ALIGN_CENTER);
                    agregarCeldaTabla(tabla,
                        formatCOP.format(det.getTotalLinea()),
                        bg, Element.ALIGN_RIGHT);
                    gris = !gris;
                }
            }
            document.add(tabla);

            // Totales
            document.add(Chunk.NEWLINE);
            PdfPTable totales = new PdfPTable(2);
            totales.setWidthPercentage(45);
            totales.setHorizontalAlignment(Element.ALIGN_RIGHT);
            agregarFilaTotalInfo(totales, "Subtotal sin impuestos:",
                formatCOP.format(remision.getSubtotal() != null ? remision.getSubtotal() : java.math.BigDecimal.ZERO));

            java.math.BigDecimal ivaTotal = remision.getTotalIva() != null ? remision.getTotalIva() : java.math.BigDecimal.ZERO;
            if (ivaTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                agregarFilaTotalInfo(totales, "IVA:",
                    formatCOP.format(ivaTotal));
            }

            java.math.BigDecimal ibuaTotal = remision.getTotalIbua() != null ? remision.getTotalIbua() : java.math.BigDecimal.ZERO;
            if (ibuaTotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                agregarFilaTotalInfo(totales, "IBUA (beb. azucaradas):",
                    formatCOP.format(ibuaTotal));
            }

            PdfPCell lblTotal = new PdfPCell(
                new Phrase("TOTAL:", new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD)));
            lblTotal.setBorder(Rectangle.TOP);
            lblTotal.setPadding(5);
            lblTotal.setBackgroundColor(COLOR_GRIS);
            totales.addCell(lblTotal);
            PdfPCell valTotal = new PdfPCell(
                new Phrase(formatCOP.format(remision.getTotal() != null ? remision.getTotal() : java.math.BigDecimal.ZERO),
                    new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, COLOR_ROJO)));
            valTotal.setBorder(Rectangle.TOP);
            valTotal.setPadding(5);
            valTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totales.addCell(valTotal);
            document.add(totales);

            // Observaciones
            if (remision.getObservaciones() != null && !remision.getObservaciones().isBlank()) {
                document.add(Chunk.NEWLINE);
                Paragraph obs = new Paragraph("Observaciones: " + remision.getObservaciones(), FONT_SMALL);
                document.add(obs);
            }

            agregarFirmas(document);
            agregarFooter(document);
            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando PDF de remision {}", remision.getId(), e);
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }

    // ─── VISTA PREVIA PDF DIAN CON MARCA DE AGUA ────────────────

    /**
     * Genera el PDF de factura DIAN con una marca de agua diagonal roja
     * que indica claramente que es un documento de prueba sin valor comercial.
     * Usado para que el admin verifique la información antes de enviar a la DIAN.
     */
    public byte[] generarVistaPreviaFacturaDian(Remision remision) {
        try {
            byte[] pdfBase = generarFacturaDian(remision);
            return agregarMarcaDeAgua(pdfBase);
        } catch (Exception e) {
            log.error("Error generando vista previa DIAN para remision {}", remision.getId(), e);
            throw new RuntimeException("Error generando vista previa: " + e.getMessage());
        }
    }

    private byte[] agregarMarcaDeAgua(byte[] pdfBytes) throws Exception {
        PdfReader reader = new PdfReader(pdfBytes);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(reader, baos);

        BaseFont bf = BaseFont.createFont(
            BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

        int numPaginas = reader.getNumberOfPages();

        for (int pag = 1; pag <= numPaginas; pag++) {
            PdfContentByte canvas = stamper.getOverContent(pag);
            com.itextpdf.text.Rectangle pageSize = reader.getPageSize(pag);
            float cx = pageSize.getWidth()  / 2f;
            float cy = pageSize.getHeight() / 2f;

            canvas.saveState();

            // ── Transparencia: 22% opacidad (casi transparente) ──
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(0.22f);
            gs.setStrokeOpacity(0.22f);
            canvas.setGState(gs);

            // ── Color rojo oscuro ──
            canvas.setColorFill(new BaseColor(180, 30, 30));

            // ── Ángulo 45° ──
            float angle = (float)(Math.PI / 4.0);
            float cos   = (float) Math.cos(angle);
            float sin   = (float) Math.sin(angle);

            // Tres líneas de texto, cada una con un tamaño y desplazamiento vertical distintos
            // (desplazamiento perpendicular al eje de escritura, en coordenadas del texto rotado)
            String[] lineas    = {"DOCUMENTO DE PRUEBA",
                                  "DISEÑO DE DOCUMENTOS,",
                                  "NO TIENE NINGÚN VALOR COMERCIAL"};
            float[]  tamanos   = { 36f, 28f, 20f };
            float[]  offsetPerp = { 52f, 6f, -40f }; // perpendicular al eje de escritura

            for (int i = 0; i < lineas.length; i++) {
                float sz   = tamanos[i];
                float op   = offsetPerp[i];
                float tw   = bf.getWidthPoint(lineas[i], sz);

                // Centro del texto (antes de rotar):
                //   - Paralelo al eje de texto: desplazamos -tw/2 para centrar
                //   - Perpendicular: op
                // Después aplicamos la rotación para obtener las coordenadas en el plano de la página
                float startX = cx + (-tw / 2f) * cos - op * sin;
                float startY = cy + (-tw / 2f) * sin + op * cos;

                canvas.beginText();
                canvas.setFontAndSize(bf, sz);
                canvas.setTextMatrix(cos, sin, -sin, cos, startX, startY);
                canvas.showText(lineas[i]);
                canvas.endText();
            }

            canvas.restoreState();
        }

        stamper.close();
        reader.close();
        return baos.toByteArray();
    }

    // ─── PDF FACTURA ELECTRÓNICA DIAN (estilo Siigo) ────────────

    public byte[] generarFacturaDian(Remision remision) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 36, 36);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.open();

            // 1. Encabezado: empresa | QR | tipo documento
            agregarEncabezadoDian(doc, remision);

            // 2. Datos del cliente + fechas
            agregarClienteDian(doc, remision);

            // 3. Tabla de ítems
            agregarItemsDian(doc, remision);

            // 4. Totales + pie legal
            agregarPieDian(doc, remision);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando PDF factura DIAN para remision {}", remision.getId(), e);
            throw new RuntimeException("Error generando PDF DIAN: " + e.getMessage());
        }
    }

    private void agregarEncabezadoDian(Document doc, Remision remision) throws DocumentException {
        // 3 columnas: empresa | QR | título factura
        PdfPTable header = new PdfPTable(3);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{2.5f, 0.9f, 1.8f});
        header.setSpacingAfter(8);

        // ── Col 1: Datos empresa ──
        PdfPCell cEmpresa = new PdfPCell();
        cEmpresa.setBorder(Rectangle.NO_BORDER);
        cEmpresa.setPadding(6);
        Paragraph pEmp = new Paragraph();
        pEmp.add(new Chunk(empNombre + "\n", new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD)));
        pEmp.add(new Chunk("NIT " + empNit + "\n", FONT_NORMAL));
        pEmp.add(new Chunk(empDireccion + "\n", FONT_SMALL));
        pEmp.add(new Chunk("Tel: " + empTelefono + "\n", FONT_SMALL));
        pEmp.add(new Chunk(empCiudad + "\n", FONT_SMALL));
        pEmp.add(new Chunk(empCorreo, FONT_SMALL));
        cEmpresa.addElement(pEmp);
        header.addCell(cEmpresa);

        // ── Col 2: QR DIAN ──
        PdfPCell cQr = new PdfPCell();
        cQr.setBorder(Rectangle.NO_BORDER);
        cQr.setHorizontalAlignment(Element.ALIGN_CENTER);
        cQr.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cQr.setPadding(4);
        try {
            // El contenido del QR es el CUFE (en mock usamos el número de factura)
            String cufe = remision.getSiigoInvoiceId() != null ? remision.getSiigoInvoiceId() : remision.getNumero();
            BarcodeQRCode qr = new BarcodeQRCode(cufe, 1, 1, null);
            Image qrImg = qr.getImage();
            qrImg.scaleAbsolute(75, 75);
            cQr.addElement(qrImg);
        } catch (Exception ex) {
            // Si falla el QR, dejamos la celda vacía
            cQr.addElement(new Phrase("QR", FONT_MICRO));
        }
        header.addCell(cQr);

        // ── Col 3: Tipo de documento ──
        PdfPCell cTipo = new PdfPCell();
        cTipo.setBorder(Rectangle.BOX);
        cTipo.setBorderColor(COLOR_BORDE);
        cTipo.setPadding(10);
        cTipo.setHorizontalAlignment(Element.ALIGN_CENTER);
        cTipo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph pTipo = new Paragraph();
        pTipo.setAlignment(Element.ALIGN_CENTER);
        pTipo.add(new Chunk("Factura electrónica de venta\n",
            new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL)));
        String numFEV = remision.getNumeroFacturaDian() != null
            ? remision.getNumeroFacturaDian() : "SIMULACION";
        pTipo.add(new Chunk("No. " + numFEV,
            new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, COLOR_ROJO)));
        cTipo.addElement(pTipo);
        header.addCell(cTipo);

        doc.add(header);
    }

    private void agregarClienteDian(Document doc, Remision remision) throws DocumentException {
        // 2 columnas: datos cliente | fechas
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{3f, 1.6f});
        tabla.setSpacingAfter(8);

        // ── Col 1: datos cliente ──
        PdfPCell cCliente = new PdfPCell();
        cCliente.setBorder(Rectangle.BOX);
        cCliente.setBorderColor(COLOR_BORDE);
        cCliente.setPadding(8);

        String nombre    = remision.getCliente() != null ? remision.getCliente().getRazonSocial() : "-";
        String nit       = remision.getCliente() != null ? remision.getCliente().getNit() : "-";
        String telefono  = remision.getCliente() != null && remision.getCliente().getTelefono() != null
            ? remision.getCliente().getTelefono() : "-";
        String direccion = remision.getCliente() != null && remision.getCliente().getDireccion() != null
            ? remision.getCliente().getDireccion() : "-";
        String ciudad    = remision.getCliente() != null && remision.getCliente().getCiudad() != null
            ? remision.getCliente().getCiudad() : "-";

        Paragraph pCliente = new Paragraph();
        pCliente.add(new Chunk("Señores  ", FONT_BOLD));
        pCliente.add(new Chunk(nombre + "\n", FONT_NORMAL));
        pCliente.add(new Chunk("NIT  ", FONT_BOLD));
        pCliente.add(new Chunk(nit + "    ", FONT_NORMAL));
        pCliente.add(new Chunk("Teléfono  ", FONT_BOLD));
        pCliente.add(new Chunk(telefono + "\n", FONT_NORMAL));
        pCliente.add(new Chunk("Dirección  ", FONT_BOLD));
        pCliente.add(new Chunk(direccion + "    ", FONT_NORMAL));
        pCliente.add(new Chunk("Ciudad  ", FONT_BOLD));
        pCliente.add(new Chunk(ciudad, FONT_NORMAL));
        cCliente.addElement(pCliente);
        tabla.addCell(cCliente);

        // ── Col 2: fechas ──
        PdfPCell cFechas = new PdfPCell();
        cFechas.setBorder(Rectangle.BOX);
        cFechas.setBorderColor(COLOR_BORDE);
        cFechas.setPadding(0);

        PdfPTable tFechas = new PdfPTable(2);
        tFechas.setWidthPercentage(100);
        // Título "Fecha y hora Factura"
        PdfPCell tituloF = new PdfPCell(new Phrase("Fecha y hora Factura",
            new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD)));
        tituloF.setColspan(2);
        tituloF.setBackgroundColor(COLOR_GRIS);
        tituloF.setPadding(4);
        tituloF.setBorder(Rectangle.BOTTOM);
        tituloF.setBorderColor(COLOR_BORDE);
        tituloF.setHorizontalAlignment(Element.ALIGN_CENTER);
        tFechas.addCell(tituloF);

        LocalDateTime ahora = remision.getFechaFacturacion() != null
            ? remision.getFechaFacturacion() : LocalDateTime.now();
        String fechaStr = ahora.format(DTF);
        String fechaSolo = remision.getFecha() != null
            ? remision.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : fechaStr;

        agregarFilaFecha(tFechas, "Generación",  fechaStr);
        agregarFilaFecha(tFechas, "Expedición",  fechaStr);
        agregarFilaFecha(tFechas, "Vencimiento", fechaSolo);
        cFechas.addElement(tFechas);
        tabla.addCell(cFechas);

        doc.add(tabla);
    }

    private void agregarFilaFecha(PdfPTable t, String label, String valor) {
        PdfPCell l = new PdfPCell(new Phrase(label, FONT_SMALL));
        l.setBorder(Rectangle.BOTTOM);
        l.setBorderColor(COLOR_BORDE);
        l.setPadding(4);
        l.setBackgroundColor(COLOR_GRIS);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(valor, FONT_SMALL));
        v.setBorder(Rectangle.BOTTOM);
        v.setBorderColor(COLOR_BORDE);
        v.setPadding(4);
        t.addCell(v);
    }

    private void agregarItemsDian(Document doc, Remision remision) throws DocumentException {
        PdfPTable tabla = new PdfPTable(3);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{0.5f, 5f, 2f});
        tabla.setSpacingAfter(8);

        // Cabeceras
        for (String h : List.of("Ítem", "Descripción", "Vr. Total")) {
            PdfPCell c = new PdfPCell(new Phrase(h, FONT_HEADER));
            c.setBackgroundColor(COLOR_ROJO);
            c.setPadding(6);
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(h.equals("Vr. Total") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            tabla.addCell(c);
        }

        // Filas
        boolean gris = false;
        int item = 1;
        if (remision.getDetalles() != null) {
            for (RemisionDetalle det : remision.getDetalles()) {
                BaseColor bg = gris ? COLOR_GRIS : BaseColor.WHITE;
                agregarCeldaDian(tabla, String.valueOf(item++), bg, Element.ALIGN_CENTER);
                // Descripción: nombre × cant. @ precio/u  (+IBUA si aplica)
                int cantUds = det.getCantidad() != null ? det.getCantidad().intValue() : 0;
                StringBuilder descBuilder = new StringBuilder();
                descBuilder.append(det.getDescripcionProducto() != null ? det.getDescripcionProducto() : "-");
                descBuilder.append("\n  ").append(cantUds).append(" und × ")
                    .append(formatCOP.format(det.getPrecioUnitario() != null ? det.getPrecioUnitario() : BigDecimal.ZERO)).append("/u");
                if (det.getPorcentajeIva() != null && det.getPorcentajeIva().compareTo(BigDecimal.ZERO) > 0) {
                    descBuilder.append("  IVA ").append(det.getPorcentajeIva().intValue()).append("%");
                }
                if (det.getValorIbuaUnidad() != null && det.getValorIbuaUnidad().compareTo(BigDecimal.ZERO) > 0) {
                    descBuilder.append("  IBUA $").append(
                        det.getValorIbuaUnidad().setScale(0, java.math.RoundingMode.HALF_UP)).append("/u");
                }
                agregarCeldaDian(tabla, descBuilder.toString(), bg, Element.ALIGN_LEFT);
                agregarCeldaDian(tabla,
                    formatCOP.format(det.getTotalLinea()), bg, Element.ALIGN_RIGHT);
                gris = !gris;
            }
        }

        // Si es consolidada (consumidor final puede tener observaciones)
        if (remision.getObservaciones() != null && !remision.getObservaciones().isBlank()) {
            PdfPCell obs = new PdfPCell(new Phrase("Obs: " + remision.getObservaciones(), FONT_SMALL));
            obs.setColspan(3);
            obs.setBorder(Rectangle.TOP);
            obs.setBorderColor(COLOR_BORDE);
            obs.setPadding(5);
            tabla.addCell(obs);
        }

        doc.add(tabla);
    }

    private void agregarCeldaDian(PdfPTable t, String texto, BaseColor bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(texto, FONT_NORMAL));
        c.setBackgroundColor(bg);
        c.setPadding(5);
        c.setHorizontalAlignment(align);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new BaseColor(220, 220, 220));
        c.setBorderWidth(0.5f);
        t.addCell(c);
    }

    private void agregarPieDian(Document doc, Remision remision) throws DocumentException {
        // ── Bloque inferior: totales a la derecha ──
        PdfPTable piePrincipal = new PdfPTable(2);
        piePrincipal.setWidthPercentage(100);
        piePrincipal.setWidths(new float[]{3f, 1.5f});

        // Columna izquierda: datos legales
        PdfPCell cLegal = new PdfPCell();
        cLegal.setBorder(Rectangle.NO_BORDER);
        cLegal.setPadding(5);

        Paragraph pLegal = new Paragraph();
        pLegal.add(new Chunk("Total items: " + (remision.getDetalles() != null ? remision.getDetalles().size() : 0) + "\n",
            new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD)));
        pLegal.add(Chunk.NEWLINE);
        pLegal.add(new Chunk("Valor en Letras:\n", new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD)));
        BigDecimal total = remision.getTotal() != null ? remision.getTotal() : BigDecimal.ZERO;
        pLegal.add(new Chunk(numeroALetras(total) + "\n\n", FONT_NORMAL));
        pLegal.add(new Chunk("Forma de pago:\n", new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD)));
        pLegal.add(new Chunk("Contado\n\n", FONT_NORMAL));
        pLegal.add(new Chunk("Medio de pago:\n", new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD)));
        pLegal.add(new Chunk("Efectivo - Efectivo", FONT_NORMAL));
        pLegal.add(new Chunk("    $  " + formatCOP.format(total) + "\n\n", FONT_NORMAL));
        pLegal.add(new Chunk("Observaciones:\n", new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD)));
        if (remision.getObservaciones() != null) {
            pLegal.add(new Chunk(remision.getObservaciones(), FONT_NORMAL));
        }
        cLegal.addElement(pLegal);
        piePrincipal.addCell(cLegal);

        // Columna derecha: totales
        PdfPCell cTotales = new PdfPCell();
        cTotales.setBorder(Rectangle.NO_BORDER);
        cTotales.setPadding(5);

        PdfPTable tTotales = new PdfPTable(2);
        tTotales.setWidthPercentage(100);

        // Subtotal base (sin impuestos)
        BigDecimal subtotalBase = remision.getSubtotal() != null ? remision.getSubtotal() : BigDecimal.ZERO;
        agregarFilaTotalDian(tTotales, "Total Bruto", formatCOP.format(subtotalBase), false);

        // IVA (si aplica)
        if (remision.getTotalIva() != null && remision.getTotalIva().compareTo(BigDecimal.ZERO) > 0) {
            agregarFilaTotalDian(tTotales, "Total IVA", formatCOP.format(remision.getTotalIva()), false);
        }

        // IBUA — Impuesto a Bebidas Ultraprocesadas Azucaradas (si aplica)
        BigDecimal ibuaTotal = remision.getTotalIbua() != null ? remision.getTotalIbua() : BigDecimal.ZERO;
        if (ibuaTotal.compareTo(BigDecimal.ZERO) > 0) {
            agregarFilaTotalDian(tTotales, "IBUA (Beb. Azucaradas)", formatCOP.format(ibuaTotal), false);
        }

        agregarFilaTotalDian(tTotales, "Total a Pagar", formatCOP.format(total), true);
        cTotales.addElement(tTotales);
        piePrincipal.addCell(cTotales);

        doc.add(piePrincipal);

        // ── Texto legal inferior ──
        String cufe = remision.getSiigoInvoiceId() != null ? remision.getSiigoInvoiceId() : "SIMULACION-SIN-CUFE-REAL";
        Paragraph pTextLegal = new Paragraph();
        pTextLegal.setSpacingBefore(10);
        pTextLegal.add(new Chunk(
            "A esta factura de venta aplican las normas relativas a la letra de cambio (artículo 5 Ley 1231 de 2008). " +
            "Con esta el Comprador declara haber recibido real y materialmente las mercancías o prestación de " +
            "servicios descritos en este título - Valor. ",
            FONT_MICRO));
        if (!resNumero.isBlank()) {
            pTextLegal.add(new Chunk(
                "Número Autorización Electrónica " + resNumero +
                " aprobado en " + resFecha +
                " prefijo " + resPrefijo +
                " desde el número " + resDesde +
                " al " + resHasta +
                " Vigencia: " + resVigencia + " ",
                new Font(Font.FontFamily.HELVETICA, 7, Font.BOLD)));
        }
        pTextLegal.add(Chunk.NEWLINE);
        pTextLegal.add(new Chunk(
            "Responsable de IVA - Actividad Económica " + empCiiu + " " + empActividad,
            FONT_MICRO));
        pTextLegal.add(Chunk.NEWLINE);
        pTextLegal.add(new Chunk("CUFE: " + cufe, FONT_MICRO));
        doc.add(pTextLegal);
    }

    private void agregarFilaTotalDian(PdfPTable t, String label, String valor, boolean bold) {
        Font f = bold ? new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD) : FONT_NORMAL;
        PdfPCell l = new PdfPCell(new Phrase(label, f));
        l.setBorder(bold ? Rectangle.TOP : Rectangle.NO_BORDER);
        l.setBorderColor(COLOR_BORDE);
        l.setPadding(4);
        l.setBackgroundColor(bold ? COLOR_GRIS : BaseColor.WHITE);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(valor, f));
        v.setBorder(bold ? Rectangle.TOP : Rectangle.NO_BORDER);
        v.setBorderColor(COLOR_BORDE);
        v.setPadding(4);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setBackgroundColor(bold ? COLOR_GRIS : BaseColor.WHITE);
        t.addCell(v);
    }

    // ── Número a letras en español (pesos colombianos) ────────

    private String numeroALetras(BigDecimal valor) {
        if (valor == null) return "cero pesos m/cte";
        long entero = valor.longValue();
        return letras(entero) + " pesos m/cte";
    }

    private static final String[] UNIDADES = {"", "un", "dos", "tres", "cuatro", "cinco",
        "seis", "siete", "ocho", "nueve", "diez", "once", "doce", "trece", "catorce",
        "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve"};
    private static final String[] DECENAS = {"", "diez", "veinte", "treinta", "cuarenta",
        "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
    private static final String[] CENTENAS = {"", "cien", "doscientos", "trescientos",
        "cuatrocientos", "quinientos", "seiscientos", "setecientos", "ochocientos", "novecientos"};

    private String letras(long n) {
        if (n == 0) return "cero";
        if (n < 0) return "menos " + letras(-n);
        if (n < 20) return UNIDADES[(int) n];
        if (n < 100) {
            String dec = DECENAS[(int)(n / 10)];
            return n % 10 == 0 ? dec : dec + " y " + UNIDADES[(int)(n % 10)];
        }
        if (n < 1000) {
            String cent = n == 100 ? "cien" : CENTENAS[(int)(n / 100)];
            return n % 100 == 0 ? cent : cent + " " + letras(n % 100);
        }
        if (n < 1_000_000) {
            long miles = n / 1000;
            String prefMil = miles == 1 ? "mil" : letras(miles) + " mil";
            return n % 1000 == 0 ? prefMil : prefMil + " " + letras(n % 1000);
        }
        if (n < 1_000_000_000L) {
            long mill = n / 1_000_000;
            String prefMill = mill == 1 ? "un millón" : letras(mill) + " millones";
            return n % 1_000_000 == 0 ? prefMill : prefMill + " " + letras(n % 1_000_000);
        }
        return String.valueOf(n);
    }

    private void agregarFooter(Document doc) throws DocumentException {
        Paragraph footer = new Paragraph(
            "Documento generado por DISTRIASOCIADOS SAS - Sistema de Inventario",
            FONT_SMALL
        );
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        doc.add(footer);
    }
}
