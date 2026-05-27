package com.distriasociados.inventario.util;

import com.distriasociados.inventario.entity.Producto.Presentacion;
import lombok.Value;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsea nombres de productos tal como vienen de Siigo, por ejemplo:
 *
 *   "MONSTER PARADISE 473ML LAT(12) MQ"
 *   "COCA COLA ORIGINAL 600ML PET(24) MQ"
 *   "SPRITE LIMÓN 350ML LAT(24)"
 *   "AGUA BRISA GAS 1.5L PET(6)"
 *
 * Extrae automáticamente:
 *   - tamanoMl     (600, 1.5, 473…)
 *   - unidadMedida (ML, L, OZ, CC)
 *   - presentacion (PET, LAT, VIR, NR, TETRA, SA, BIDON)
 *   - unidadesPorCaja (número entre paréntesis)
 *   - marca        (primera palabra, ej: MONSTER, COCA, SPRITE)
 *   - sabor        (palabras restantes antes del tamaño, ej: PARADISE, COLA ORIGINAL)
 *
 * Casos que se detectan:
 *   marca + sabor + tamaño + presentacion + (uds)  →  caso completo
 *   marca + tamaño + presentacion + (uds)          →  sin sabor
 *   nombre sin patrón reconocible                  →  todo en marca, resto null
 *
 * Sufijos de ruido conocidos (MQ, NRV, PQ, etc.) se eliminan automáticamente.
 */
public class SiigoNombreParser {

    // ── Patrones ───────────────────────────────────────────────────────────────

    /** Tamaño: 473, 600, 1.5, 1,5  seguido de unidad */
    private static final Pattern SIZE_UNIT = Pattern.compile(
        "(\\d+(?:[.,]\\d+)?)\\s*(ML|CC|LT|L|OZ|MG|G|KG)\\b",
        Pattern.CASE_INSENSITIVE
    );

    /** Presentaciones conocidas — BIDON/TETRA primero para evitar match parcial */
    private static final Pattern PRESENTACION = Pattern.compile(
        "\\b(BIDON|TETRA|LAT|PET|VIR|NR|SA)\\b",
        Pattern.CASE_INSENSITIVE
    );

    /** Unidades por caja: (12), (24), (6) */
    private static final Pattern UNIDADES_CAJA = Pattern.compile(
        "\\((\\d+)\\)"
    );

    /**
     * Sufijos de ruido al final del nombre (MQ, NRV, PQ, SB…).
     * Son 1-3 letras mayúsculas que Siigo agrega como código interno.
     */
    private static final Pattern NOISE_SUFFIX = Pattern.compile(
        "(?:\\s+[A-Z]{1,3})+$"
    );

    // ── Método principal ───────────────────────────────────────────────────────

    /**
     * Parsea el nombre y devuelve un {@link ParsedProducto} con los campos extraídos.
     * Nunca lanza excepción: si no puede parsear algo, ese campo queda null/default.
     *
     * @param nombre  Nombre del producto tal como viene de Siigo
     * @return        Campos extraídos
     */
    public static ParsedProducto parse(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return new ParsedProducto(nombre, null, null, "ML", null, 1);
        }

        String work = nombre.trim().toUpperCase();

        // ── 1. Extraer unidades por caja: (12), (24) ──────────────────────────
        Integer unidadesPorCaja = null;
        Matcher uMatcher = UNIDADES_CAJA.matcher(work);
        if (uMatcher.find()) {
            unidadesPorCaja = Integer.parseInt(uMatcher.group(1));
            work = splice(work, uMatcher.start(), uMatcher.end());
        }

        // ── 2. Extraer presentación ────────────────────────────────────────────
        Presentacion presentacion = null;
        Matcher pMatcher = PRESENTACION.matcher(work);
        if (pMatcher.find()) {
            presentacion = toEnum(pMatcher.group(1).toUpperCase());
            work = splice(work, pMatcher.start(), pMatcher.end());
        }

        // ── 3. Extraer tamaño + unidad de medida ──────────────────────────────
        String tamanoMl   = null;
        String unidadMedida = "ML";
        Matcher sMatcher = SIZE_UNIT.matcher(work);
        if (sMatcher.find()) {
            tamanoMl    = sMatcher.group(1).replace(",", ".");
            String unit = sMatcher.group(2).toUpperCase();
            unidadMedida = "LT".equals(unit) ? "L" : unit;  // normalizar LT→L
            // Quedarse con lo que hay ANTES del tamaño (la parte marca/sabor)
            work = work.substring(0, sMatcher.start()).trim();
        }

        // ── 4. Eliminar sufijos de ruido al final (MQ, NRV, PQ…) ─────────────
        work = NOISE_SUFFIX.matcher(work).replaceAll("").trim();
        work = work.replaceAll("\\s{2,}", " ").trim();

        // ── 5. Dividir en marca (1ª palabra) y sabor (el resto) ───────────────
        String marca = work;
        String sabor = null;
        int firstSpace = work.indexOf(' ');
        if (firstSpace > 0) {
            marca = work.substring(0, firstSpace).trim();
            sabor = work.substring(firstSpace + 1).trim();
            if (sabor.isEmpty()) sabor = null;
        }

        // Si después del parseo marca quedó vacía, poner el nombre original
        if (marca.isBlank()) {
            marca = nombre.trim().toUpperCase();
            sabor = null;
        }

        return new ParsedProducto(
            marca,
            sabor,
            tamanoMl,
            unidadMedida,
            presentacion,
            unidadesPorCaja != null ? unidadesPorCaja : 1
        );
    }

    // ── Helpers privados ───────────────────────────────────────────────────────

    /** Elimina el segmento [start, end) de la cadena y normaliza espacios */
    private static String splice(String s, int start, int end) {
        return (s.substring(0, start) + " " + s.substring(end)).replaceAll("\\s{2,}", " ").trim();
    }

    /** Convierte una cadena al enum Presentacion; null si no coincide */
    private static Presentacion toEnum(String val) {
        try {
            return Presentacion.valueOf(val);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── DTO de resultado ───────────────────────────────────────────────────────

    /**
     * Resultado del parseo.  Todos los campos pueden ser null salvo
     * marca (siempre tiene algo) y unidadesPorCaja (default 1).
     */
    @Value
    public static class ParsedProducto {
        /** Ej: MONSTER, COCA, SPRITE */
        String marca;

        /** Ej: PARADISE, COLA ORIGINAL, LIMÓN — null si no se detectó */
        String sabor;

        /** Ej: "600", "1.5", "473" — null si no se detectó */
        String tamanoMl;

        /** ML, L, OZ, CC — default "ML" */
        String unidadMedida;

        /** PET, LAT, VIR, NR, TETRA, SA, BIDON — null si no se detectó */
        Presentacion presentacion;

        /** Unidades por caja — default 1 si no se detectó */
        int unidadesPorCaja;
    }
}
