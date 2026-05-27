package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.Producto;
import com.distriasociados.inventario.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelService {

    private final ProductoRepository productoRepository;

    // Genera plantilla Excel con todos los productos activos — stock expresado en CAJAS
    public byte[] generarPlantillaInventario() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Inventario");

            // ── Estilos ───────────────────────────────────────────────────
            // Encabezado rojo (columnas de identificación — NO EDITAR)
            XSSFCellStyle hdrRojo = wb.createCellStyle();
            XSSFFont fBlancoNegrita = wb.createFont();
            fBlancoNegrita.setBold(true);
            fBlancoNegrita.setColor(IndexedColors.WHITE.getIndex());
            hdrRojo.setFont(fBlancoNegrita);
            hdrRojo.setFillForegroundColor(new XSSFColor(new byte[]{(byte)211, 47, 47}, null));
            hdrRojo.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Encabezado gris (columna informativa Uds_Por_Caja — NO EDITAR)
            XSSFCellStyle hdrGris = wb.createCellStyle();
            XSSFFont fGrisNegrita = wb.createFont();
            fGrisNegrita.setBold(true);
            fGrisNegrita.setColor(IndexedColors.WHITE.getIndex());
            hdrGris.setFont(fGrisNegrita);
            hdrGris.setFillForegroundColor(new XSSFColor(new byte[]{(byte)117, 117, 117}, null));
            hdrGris.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Encabezado azul (columnas editables — INGRESAR CONTEO)
            XSSFCellStyle hdrAzul = wb.createCellStyle();
            XSSFFont fAzulNegrita = wb.createFont();
            fAzulNegrita.setBold(true);
            fAzulNegrita.setColor(IndexedColors.WHITE.getIndex());
            hdrAzul.setFont(fAzulNegrita);
            hdrAzul.setFillForegroundColor(new XSSFColor(new byte[]{(byte)21, 101, (byte)192}, null));
            hdrAzul.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Celda de datos gris claro (celdas informativas — no editar)
            XSSFCellStyle celdaGris = wb.createCellStyle();
            celdaGris.setFillForegroundColor(new XSSFColor(new byte[]{(byte)238, (byte)238, (byte)238}, null));
            celdaGris.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // ── Fila de encabezado ────────────────────────────────────────
            // Columnas: 0=ID, 1=Nombre, 2=Uds_Por_Caja(info), 3=Stock_Cajas(editar), 4=StockMin_Cajas(editar)
            Row headerRow = sheet.createRow(0);

            Cell h0 = headerRow.createCell(0); h0.setCellValue("ID_Producto");       h0.setCellStyle(hdrRojo);
            Cell h1 = headerRow.createCell(1); h1.setCellValue("Nombre_Producto");    h1.setCellStyle(hdrRojo);
            Cell h2 = headerRow.createCell(2); h2.setCellValue("Uds_Por_Caja (info)");h2.setCellStyle(hdrGris);
            Cell h3 = headerRow.createCell(3); h3.setCellValue("Stock_Actual_Cajas"); h3.setCellStyle(hdrAzul);
            Cell h4 = headerRow.createCell(4); h4.setCellValue("Stock_Minimo_Cajas"); h4.setCellStyle(hdrAzul);

            sheet.setColumnWidth(0, 3500);
            sheet.setColumnWidth(1, 9000);
            sheet.setColumnWidth(2, 4500);
            sheet.setColumnWidth(3, 5000);
            sheet.setColumnWidth(4, 5000);

            // ── Filas de productos ────────────────────────────────────────
            List<Producto> productos = productoRepository.findByActivoTrue();
            int rowNum = 1;
            for (Producto p : productos) {
                int upk = (p.getUnidadesPorCaja() != null && p.getUnidadesPorCaja() > 0)
                          ? p.getUnidadesPorCaja() : 1;
                int stockCajas    = p.getStockActual() != null ? p.getStockActual() / upk : 0;
                int stockMinCajas = p.getStockMinimo() != null ? p.getStockMinimo() / upk : 0;

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getNombreCompleto());

                Cell upkCell = row.createCell(2);
                upkCell.setCellValue(upk);
                upkCell.setCellStyle(celdaGris);   // gris = solo lectura visual

                row.createCell(3).setCellValue(stockCajas);
                row.createCell(4).setCellValue(stockMinCajas);
            }

            // ── Hoja de instrucciones ─────────────────────────────────────
            XSSFSheet instrSheet = wb.createSheet("Instrucciones");
            instrSheet.setColumnWidth(0, 20000);
            String[] instrucciones = {
                "INSTRUCCIONES DE IMPORTACION DE INVENTARIO",
                "",
                "COLUMNAS:",
                "  ID_Producto      → No modificar. Identificador interno del producto.",
                "  Nombre_Producto  → No modificar. Solo informativo.",
                "  Uds_Por_Caja     → No modificar. Indica cuantas unidades tiene cada caja.",
                "  Stock_Actual_Cajas  → INGRESE AQUI el conteo fisico en CAJAS.",
                "  Stock_Minimo_Cajas  → Opcional. Stock minimo de alerta en cajas.",
                "",
                "COMO USAR:",
                "  1. Cuente las cajas fisicas de cada producto.",
                "  2. Escriba el numero de cajas en la columna 'Stock_Actual_Cajas'.",
                "  3. El sistema convierte automaticamente cajas x Uds_Por_Caja al importar.",
                "  4. Ejemplo: 10 cajas x 24 uds/caja = 240 unidades en el sistema.",
                "",
                "IMPORTANTE:",
                "  - Los valores reemplazan el stock actual completamente.",
                "  - No agregue ni elimine filas.",
                "  - Guarde el archivo como .xlsx antes de subir."
            };
            for (int i = 0; i < instrucciones.length; i++) {
                Row r = instrSheet.createRow(i);
                r.createCell(0).setCellValue(instrucciones[i]);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error generando plantilla Excel", e);
            throw new RuntimeException("Error generando plantilla: " + e.getMessage());
        }
    }

    // Importa stock desde archivo Excel (valores en CAJAS → convierte a unidades usando upk de BD)
    public Map<String, Object> importarInventario(MultipartFile file) {
        int importados = 0;
        int errores = 0;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheet("Inventario");
            if (sheet == null) {
                throw new RuntimeException("El archivo no tiene una hoja llamada 'Inventario'");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    Cell idCell    = row.getCell(0);  // ID_Producto
                    Cell cajasCell = row.getCell(3);  // Stock_Actual_Cajas (col 3 en nueva plantilla)

                    // Compatibilidad con plantilla antigua (col 2 = unidades directas)
                    if (cajasCell == null) {
                        cajasCell = row.getCell(2);
                    }
                    if (idCell == null || cajasCell == null) continue;

                    long productoId = (long) idCell.getNumericCellValue();
                    int cajas = (int) cajasCell.getNumericCellValue();
                    if (cajas < 0) cajas = 0;

                    final int cajasFinales = cajas;
                    productoRepository.findById(productoId).ifPresent(prod -> {
                        // Convertir cajas → unidades usando upk real del producto en BD
                        int upk = (prod.getUnidadesPorCaja() != null && prod.getUnidadesPorCaja() > 0)
                                  ? prod.getUnidadesPorCaja() : 1;
                        prod.setStockActual(cajasFinales * upk);
                        productoRepository.save(prod);
                        log.info("Stock actualizado: {} → {} cajas × {} uds/caja = {} uds",
                            prod.getNombreCompleto(), cajasFinales, upk, cajasFinales * upk);
                    });
                    importados++;
                } catch (Exception e) {
                    log.warn("Error en fila {} del Excel: {}", i + 1, e.getMessage());
                    errores++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo el archivo Excel: " + e.getMessage());
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("importados", importados);
        resultado.put("errores", errores);
        return resultado;
    }

    // Genera reporte de ventas en Excel
    public byte[] generarReporteVentas(List<Map<String, Object>> datos) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Ventas");

            XSSFCellStyle headerStyle = wb.createCellStyle();
            XSSFFont font = wb.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)211, 47, 47}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] cols = {"Fecha", "Cargue", "Vendedor", "Producto", "Cant. Vendida", "Subtotal COP"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5500);
            }

            int rowIdx = 1;
            for (Map<String, Object> d : datos) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(String.valueOf(d.getOrDefault("fecha", "")));
                r.createCell(1).setCellValue(String.valueOf(d.getOrDefault("numeroCargue", "")));
                r.createCell(2).setCellValue(String.valueOf(d.getOrDefault("vendedor", "")));
                r.createCell(3).setCellValue(String.valueOf(d.getOrDefault("producto", "")));
                Object cantObj = d.getOrDefault("cantidadVendida", 0);
                r.createCell(4).setCellValue(cantObj instanceof Number ? ((Number) cantObj).doubleValue() : 0);
                Object subObj = d.getOrDefault("subtotal", 0);
                r.createCell(5).setCellValue(subObj instanceof Number ? ((Number) subObj).doubleValue() : 0);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de ventas: " + e.getMessage());
        }
    }
}
