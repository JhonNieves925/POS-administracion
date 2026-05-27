package com.distriasociados.inventario.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BackupService {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${backup.directorio:./backups}")
    private String directorioBackup;

    @Value("${backup.retener-dias:30}")
    private int retenerDias;

    @Value("${backup.habilitado:true}")
    private boolean habilitado;

    /**
     * Ruta completa o nombre del ejecutable pg_dump.
     * Si PostgreSQL no está en el PATH, configura la ruta completa en application.properties:
     *   backup.pgdump-path=C:\\Program Files\\PostgreSQL\\17\\bin\\pg_dump.exe
     */
    @Value("${backup.pgdump-path:pg_dump}")
    private String pgDumpPath;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    // ─── Backup automático — todos los días a las 2:00 AM ────────
    @Scheduled(cron = "${backup.cron:0 0 2 * * *}")
    public void backupAutomatico() {
        if (!habilitado) return;
        log.info("⏰ Iniciando backup automático programado...");
        try {
            Map<String, Object> resultado = realizarBackup("automatico");
            if (Boolean.TRUE.equals(resultado.get("exito"))) {
                log.info("✅ Backup automático completado: {}", resultado.get("archivo"));
                limpiarBackupsAntiguos();
            } else {
                log.error("❌ Backup automático falló: {}", resultado.get("error"));
            }
        } catch (Exception e) {
            log.error("❌ Error en backup automático: {}", e.getMessage(), e);
        }
    }

    // ─── Ejecutar backup manual o automático ─────────────────────
    public Map<String, Object> realizarBackup(String tipo) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("tipo", tipo);
        resultado.put("fechaHora", LocalDateTime.now().format(FMT));

        try {
            // Crear directorio si no existe
            Path dir = Paths.get(directorioBackup).toAbsolutePath();
            Files.createDirectories(dir);

            // Nombre del archivo
            String nombreArchivo = "backup_" + tipo + "_" + LocalDateTime.now().format(FMT) + ".sql";
            Path archivoDestino = dir.resolve(nombreArchivo);

            // Extraer datos de conexión de la URL JDBC
            // jdbc:postgresql://localhost:5432/distriasociados_db
            String[] partes = parsearJdbcUrl(datasourceUrl);
            String host = partes[0];
            String port = partes[1];
            String database = partes[2];

            // Resolver ejecutable pg_dump: usar ruta configurada o detectar por SO
            String pgDump;
            if (!pgDumpPath.equals("pg_dump")) {
                // Ruta configurada explícitamente en application.properties
                pgDump = pgDumpPath;
            } else {
                boolean esWindows = System.getProperty("os.name").toLowerCase().contains("win");
                pgDump = esWindows ? "pg_dump.exe" : "pg_dump";
                // Intentar auto-detectar en rutas comunes de Windows
                if (esWindows) {
                    String[] rutasComunes = {
                        "C:\\Program Files\\PostgreSQL\\17\\bin\\pg_dump.exe",
                        "C:\\Program Files\\PostgreSQL\\16\\bin\\pg_dump.exe",
                        "C:\\Program Files\\PostgreSQL\\15\\bin\\pg_dump.exe",
                        "C:\\Program Files\\PostgreSQL\\14\\bin\\pg_dump.exe"
                    };
                    for (String ruta : rutasComunes) {
                        if (new File(ruta).exists()) {
                            pgDump = ruta;
                            log.info("pg_dump encontrado automáticamente en: {}", ruta);
                            break;
                        }
                    }
                }
            }
            log.info("Usando pg_dump: {}", pgDump);

            // Comando pg_dump
            List<String> comando = new ArrayList<>(Arrays.asList(
                pgDump,
                "-h", host,
                "-p", port,
                "-U", dbUser,
                "-d", database,
                "--no-password",
                "-F", "p",           // formato plain SQL
                "-f", archivoDestino.toString()
            ));

            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.environment().put("PGPASSWORD", dbPassword);
            pb.redirectErrorStream(true);

            Process proceso = pb.start();
            String salidaError = new String(proceso.getInputStream().readAllBytes());
            int exitCode = proceso.waitFor();

            if (exitCode == 0 && Files.exists(archivoDestino)) {
                long tamanoBytes = Files.size(archivoDestino);
                resultado.put("exito", true);
                resultado.put("archivo", nombreArchivo);
                resultado.put("ruta", archivoDestino.toString());
                resultado.put("tamanoKb", tamanoBytes / 1024);
                log.info("✅ Backup creado: {} ({} KB)", nombreArchivo, tamanoBytes / 1024);
            } else {
                resultado.put("exito", false);
                resultado.put("error", salidaError.isBlank()
                    ? "pg_dump retornó código " + exitCode
                    : salidaError.trim());
                log.error("❌ pg_dump falló (código {}): {}", exitCode, salidaError);
            }

        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("No such file") || msg.contains("cannot find") ||
                msg.contains("CreateProcess") || msg.contains("No existe")) {
                resultado.put("exito", false);
                resultado.put("error",
                    "pg_dump no encontrado. Configura la ruta en application.properties: " +
                    "backup.pgdump-path=C:\\\\Program Files\\\\PostgreSQL\\\\17\\\\bin\\\\pg_dump.exe");
            } else {
                resultado.put("exito", false);
                resultado.put("error", "Error de I/O: " + msg);
            }
            log.error("❌ IOException al hacer backup: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resultado.put("exito", false);
            resultado.put("error", "Proceso interrumpido");
        } catch (Exception e) {
            resultado.put("exito", false);
            resultado.put("error", e.getMessage());
            log.error("❌ Error inesperado en backup: {}", e.getMessage(), e);
        }

        return resultado;
    }

    // ─── Listar backups existentes ────────────────────────────────
    public List<Map<String, Object>> listarBackups() {
        try {
            Path dir = Paths.get(directorioBackup).toAbsolutePath();
            if (!Files.exists(dir)) return Collections.emptyList();

            return Files.list(dir)
                .filter(p -> p.getFileName().toString().startsWith("backup_") &&
                             p.getFileName().toString().endsWith(".sql"))
                .sorted(Comparator.reverseOrder())
                .map(p -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    try {
                        info.put("archivo", p.getFileName().toString());
                        info.put("tamanoKb", Files.size(p) / 1024);
                        info.put("fechaModificacion",
                            Files.getLastModifiedTime(p).toInstant().toString());
                        // Extraer tipo del nombre: backup_automatico_... o backup_manual_...
                        String nombre = p.getFileName().toString();
                        info.put("tipo", nombre.contains("_manual_") ? "manual" : "automatico");
                    } catch (IOException e) {
                        info.put("tamanoKb", 0);
                    }
                    return info;
                })
                .collect(Collectors.toList());

        } catch (IOException e) {
            log.error("Error al listar backups: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─── Eliminar backups más antiguos que N días ─────────────────
    public int limpiarBackupsAntiguos() {
        int eliminados = 0;
        try {
            Path dir = Paths.get(directorioBackup).toAbsolutePath();
            if (!Files.exists(dir)) return 0;

            long limiteMs = System.currentTimeMillis() - ((long) retenerDias * 24 * 60 * 60 * 1000);

            List<Path> antiguos = Files.list(dir)
                .filter(p -> p.getFileName().toString().startsWith("backup_"))
                .filter(p -> {
                    try { return Files.getLastModifiedTime(p).toMillis() < limiteMs; }
                    catch (IOException e) { return false; }
                })
                .collect(Collectors.toList());

            for (Path viejo : antiguos) {
                Files.deleteIfExists(viejo);
                eliminados++;
                log.info("🗑 Backup antiguo eliminado: {}", viejo.getFileName());
            }

        } catch (IOException e) {
            log.error("Error al limpiar backups: {}", e.getMessage());
        }
        return eliminados;
    }

    // ─── Configuración actual ─────────────────────────────────────
    public Map<String, Object> obtenerConfiguracion() {
        Path dir = Paths.get(directorioBackup).toAbsolutePath();
        long totalKb = 0;
        int cantidad = 0;
        try {
            if (Files.exists(dir)) {
                List<Path> archivos = Files.list(dir)
                    .filter(p -> p.getFileName().toString().startsWith("backup_"))
                    .collect(Collectors.toList());
                cantidad = archivos.size();
                for (Path p : archivos) {
                    try { totalKb += Files.size(p) / 1024; } catch (IOException ignored) {}
                }
            }
        } catch (IOException ignored) {}

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("habilitado", habilitado);
        config.put("directorio", dir.toString());
        config.put("retenerDias", retenerDias);
        config.put("cantidadBackups", cantidad);
        config.put("espacioUsadoKb", totalKb);
        return config;
    }

    // ─── Helper: parsear URL JDBC ─────────────────────────────────
    private String[] parsearJdbcUrl(String url) {
        // jdbc:postgresql://localhost:5432/distriasociados_db
        String sin = url.replace("jdbc:postgresql://", "");
        String[] hostParte = sin.split("/");
        String[] hostPort = hostParte[0].split(":");
        return new String[]{
            hostPort[0],                          // host
            hostPort.length > 1 ? hostPort[1] : "5432",  // port
            hostParte[1]                          // database
        };
    }
}
