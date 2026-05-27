package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.dto.response.ApiResponse;
import com.distriasociados.inventario.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    private final BackupService backupService;

    @Value("${backup.directorio:./backups}")
    private String directorioBackup;

    /** Información de configuración y espacio usado */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> configuracion() {
        return ResponseEntity.ok(
            ApiResponse.ok("Configuración de backup", backupService.obtenerConfiguracion())
        );
    }

    /** Lista todos los backups disponibles */
    @GetMapping("/listar")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listar() {
        return ResponseEntity.ok(
            ApiResponse.ok("Backups disponibles", backupService.listarBackups())
        );
    }

    /** Ejecuta un backup manual ahora */
    @PostMapping("/ejecutar")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ejecutar() {
        Map<String, Object> resultado = backupService.realizarBackup("manual");
        boolean exito = Boolean.TRUE.equals(resultado.get("exito"));
        if (exito) {
            return ResponseEntity.ok(ApiResponse.ok("Backup completado exitosamente", resultado));
        } else {
            // HTTP 500 para que el frontend lo detecte como error real
            String errorMsg = (String) resultado.get("error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Error al realizar backup: " + errorMsg));
        }
    }

    /**
     * Descarga un archivo de backup específico al navegador.
     * Solo el ADMIN puede descargar — el nombre del archivo se valida
     * para evitar path traversal.
     */
    @GetMapping("/descargar/{nombreArchivo}")
    public ResponseEntity<byte[]> descargar(@PathVariable String nombreArchivo) {
        // Seguridad: rechazar cualquier intento de path traversal
        if (nombreArchivo.contains("..") || nombreArchivo.contains("/") || nombreArchivo.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        // Solo permitir archivos que empiecen con "backup_" y terminen en ".sql"
        if (!nombreArchivo.startsWith("backup_") || !nombreArchivo.endsWith(".sql")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path archivo = Paths.get(directorioBackup).toAbsolutePath().resolve(nombreArchivo);
            if (!Files.exists(archivo)) {
                return ResponseEntity.notFound().build();
            }
            byte[] contenido = Files.readAllBytes(archivo);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(contenido.length)
                .body(contenido);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Limpia backups más antiguos que N días configurados */
    @DeleteMapping("/limpiar")
    public ResponseEntity<ApiResponse<Map<String, Object>>> limpiar() {
        int eliminados = backupService.limpiarBackupsAntiguos();
        return ResponseEntity.ok(
            ApiResponse.ok("Limpieza completada",
                Map.of("backupsEliminados", eliminados))
        );
    }
}
