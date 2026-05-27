package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.ConfiguracionContable;
import com.distriasociados.inventario.repository.ConfiguracionContableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracionContableService {

    private final ConfiguracionContableRepository repo;

    // ─── Inicialización — carga cuentas por defecto si no existen ──
    @PostConstruct
    @Transactional
    public void inicializarDefaults() {
        List<Object[]> defaults = List.of(
            // { clave, descripcion, cuentaPuc, nombreCuenta, grupo, orden }
            new Object[]{"VENTA_CONTADO",   "Ingresos ventas de contado",          "4135", "Comercio al por mayor y al por menor", "INGRESOS", 1},
            new Object[]{"VENTA_CREDITO",   "Ingresos ventas a crédito",           "4135", "Comercio al por mayor y al por menor", "INGRESOS", 2},
            new Object[]{"IVA_19",          "IVA generado 19%",                    "2408", "IVA por pagar",                        "PASIVO",   3},
            new Object[]{"IVA_5",           "IVA generado 5%",                     "2408", "IVA por pagar",                        "PASIVO",   4},
            new Object[]{"IBUA",            "Impuesto bebidas azucaradas (IBUA)",  "2408", "Otros impuestos por pagar",            "PASIVO",   5},
            new Object[]{"CLIENTES",        "Cuentas por cobrar clientes",         "1305", "Clientes",                             "ACTIVO",   6},
            new Object[]{"CAJA",            "Caja — efectivo recibido",            "1105", "Caja general",                         "ACTIVO",   7},
            new Object[]{"BANCOS",          "Bancos — transferencias/consignaciones","1110","Bancos y corporaciones",              "ACTIVO",   8},
            new Object[]{"INVENTARIO",      "Inventario de mercancías",            "1435", "Mercancías no fabricadas",             "ACTIVO",   9},
            new Object[]{"COSTO_VENTAS",    "Costo de ventas",                     "6135", "Costo de ventas",                      "COSTO",   10},
            new Object[]{"DEVOLUCIONES_NC", "Devoluciones en ventas (notas crédito)","4175","Devoluciones en ventas",             "INGRESOS",11},
            new Object[]{"INTERESES_ND",    "Ingresos financieros (notas débito)", "4210", "Intereses",                           "INGRESOS",12}
        );

        for (Object[] d : defaults) {
            String clave = (String) d[0];
            if (repo.findByClave(clave).isEmpty()) {
                repo.save(ConfiguracionContable.builder()
                    .clave(clave)
                    .descripcion((String) d[1])
                    .cuentaPuc((String) d[2])
                    .nombreCuenta((String) d[3])
                    .grupo((String) d[4])
                    .orden((Integer) d[5])
                    .build());
            }
        }
        log.info("✅ Configuración contable PUC inicializada");
    }

    // ─── LISTAR ────────────────────────────────────────────────────
    public List<ConfiguracionContable> listar() {
        return repo.findAllByOrderByOrdenAsc();
    }

    // ─── OBTENER POR CLAVE ─────────────────────────────────────────
    public Optional<ConfiguracionContable> obtenerPorClave(String clave) {
        return repo.findByClave(clave);
    }

    public String cuentaPuc(String clave) {
        return repo.findByClave(clave).map(ConfiguracionContable::getCuentaPuc).orElse("—");
    }

    // ─── ACTUALIZAR (el contador cambia las cuentas PUC) ──────────
    @Transactional
    public List<ConfiguracionContable> actualizarBulk(List<Map<String, String>> cambios) {
        for (Map<String, String> cambio : cambios) {
            String clave      = cambio.get("clave");
            String cuentaPuc  = cambio.get("cuentaPuc");
            String nombreCuenta = cambio.get("nombreCuenta");

            repo.findByClave(clave).ifPresent(cfg -> {
                if (cuentaPuc  != null) cfg.setCuentaPuc(cuentaPuc.trim());
                if (nombreCuenta != null) cfg.setNombreCuenta(nombreCuenta.trim());
                repo.save(cfg);
            });
        }
        log.info("Cuentas PUC actualizadas — {} registros", cambios.size());
        return repo.findAllByOrderByOrdenAsc();
    }
}
