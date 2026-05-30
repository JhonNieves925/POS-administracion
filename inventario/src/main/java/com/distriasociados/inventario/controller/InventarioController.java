package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.dto.response.FacturaDianResponse;
import com.distriasociados.inventario.entity.*;
import com.distriasociados.inventario.service.DianService;
import com.distriasociados.inventario.service.ExcelService;
import com.distriasociados.inventario.service.InventarioService;
import com.distriasociados.inventario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService inventarioService;
    private final UsuarioService usuarioService;
    private final ExcelService excelService;
    private final DianService dianService;

    // Listar todos los productos con su stock
    @GetMapping
    public ResponseEntity<ApiResponse<List<Producto>>> listar() {
        return ResponseEntity.ok(
            ApiResponse.ok("Inventario obtenido", inventarioService.listarInventario())
        );
    }

    @GetMapping("/movimientos")
    public ResponseEntity<ApiResponse<List<MovimientoInventario>>> movimientos(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(
            ApiResponse.ok("Movimientos obtenidos", inventarioService.listarMovimientos(inicio, fin))
        );
    }

    /**
     * Entrada / salida de mercancía — suma o resta al stock actual.
     * Body: { cantidad: N (positivo=entrada, negativo=salida), motivo: "..." }
     */
    @PutMapping("/{productoId}/ajustar")
    public ResponseEntity<ApiResponse<Producto>> ajustarPorId(
            @PathVariable Long productoId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {

        int cantidad = Integer.parseInt(body.getOrDefault("cantidad", 0).toString());
        String motivo = body.getOrDefault("motivo", "Ajuste manual").toString();

        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        Producto prod = inventarioService.ajustarStock(productoId, cantidad, motivo, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Ajuste registrado", prod));
    }

    /**
     * Corrección de inventario físico — fija el stock al valor exacto contado.
     * Body: { stockReal: N, motivo: "..." }
     */
    @PutMapping("/{productoId}/corregir")
    public ResponseEntity<ApiResponse<Producto>> corregirStock(
            @PathVariable Long productoId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {

        int stockReal = Integer.parseInt(body.getOrDefault("stockReal", 0).toString());
        String motivo = body.getOrDefault("motivo", "Corrección inventario físico").toString();

        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        Producto prod = inventarioService.corregirStockFisico(productoId, stockReal, motivo, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Corrección registrada", prod));
    }

    @PostMapping("/ajuste")
    public ResponseEntity<ApiResponse<Void>> ajustar(
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Long productoId = Long.valueOf(body.get("productoId").toString());
        int cantidad    = Integer.parseInt(body.get("cantidad").toString());
        String motivo   = body.get("motivo").toString();

        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        inventarioService.ajustarInventario(productoId, cantidad, motivo, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Ajuste registrado"));
    }

    // Descargar plantilla Excel
    @GetMapping("/plantilla-excel")
    public ResponseEntity<byte[]> plantillaExcel() {
        byte[] excel = excelService.generarPlantillaInventario();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla_inventario.xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(excel);
    }

    // Importar inventario desde Excel
    @PostMapping("/importar-excel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importarExcel(
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> resultado = excelService.importarInventario(file);
        return ResponseEntity.ok(ApiResponse.ok("Importacion completada", resultado));
    }

    // ── Importación DIAN ─────────────────────────────────────────────────────────

    /**
     * Paso 1 — Validar CUFE y cargar sugerencias del historial.
     * El auxiliar pega el CUFE copiado del reporte Excel de la DIAN.
     * Responde: si el CUFE ya fue registrado (duplicado) y sugerencias de productos.
     */
    @PostMapping("/compras/consultar-cufe")
    public ResponseEntity<ApiResponse<FacturaDianResponse>> consultarCufe(
            @RequestBody Map<String, String> body) {
        String cufe = body.getOrDefault("cufe", "").trim();
        FacturaDianResponse resultado = dianService.consultarPorCufe(cufe);
        return ResponseEntity.ok(ApiResponse.ok("CUFE validado", resultado));
    }

    /**
     * Paso 1 alternativo — Parsear el XML UBL 2.1 de la factura electrónica.
     * Cuando el auxiliar logra obtener el archivo XML (descargado del portal DIAN
     * o enviado por el proveedor), este endpoint lo procesa completamente:
     * extrae proveedor, N° factura, fecha, total y todas las líneas de producto.
     */
    @PostMapping("/compras/importar-xml")
    public ResponseEntity<ApiResponse<FacturaDianResponse>> importarXml(
            @RequestParam("file") MultipartFile file) throws IOException {
        byte[] xmlBytes = file.getBytes();
        FacturaDianResponse resultado = dianService.parsearXml(xmlBytes);
        return ResponseEntity.ok(ApiResponse.ok("XML procesado", resultado));
    }

    /**
     * Sugerencias de productos para un proveedor específico
     * (historial de lo que se le ha comprado anteriormente).
     * El nombre puede ser parcial: "coca" encuentra "Coca-Cola FEMSA".
     */
    @GetMapping("/compras/sugerencias-proveedor")
    public ResponseEntity<ApiResponse<List<FacturaDianResponse.ProductoSugerido>>> sugerenciasProveedor(
            @RequestParam("nombre") String nombre) {
        var sugerencias = dianService.obtenerSugerencias(nombre);
        return ResponseEntity.ok(ApiResponse.ok("Sugerencias cargadas", sugerencias));
    }

    @GetMapping("/compras")
    public ResponseEntity<ApiResponse<List<Compra>>> compras(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(
            ApiResponse.ok("Compras obtenidas", inventarioService.listarCompras(inicio, fin))
        );
    }

    @PostMapping("/compras")
    public ResponseEntity<ApiResponse<Compra>> registrarCompra(
            @RequestBody Compra compra,
            Authentication auth) {
        Usuario usuario = usuarioService.buscarPorCedula(auth.getName());
        return ResponseEntity.ok(
            ApiResponse.ok("Compra registrada", inventarioService.registrarCompra(compra, usuario))
        );
    }
}
