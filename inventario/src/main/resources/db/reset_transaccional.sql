-- ============================================================
-- RESET TRANSACCIONAL — borra historial de pruebas
-- Conserva: productos, clientes, usuarios, vendedores, rutas
-- Borra:    cargues, remisiones, facturas, compras, movimientos
-- ============================================================
-- EJECUTAR EN pgAdmin > Query Tool (o psql)
-- ¡Solo usar en entorno de desarrollo / pruebas!
-- ============================================================

BEGIN;

-- 1. Detalles primero (hijos de las tablas principales)
DELETE FROM remision_detalles;
DELETE FROM factura_detalles;
DELETE FROM cargue_detalles;
DELETE FROM compra_detalles;

-- 2. Tablas principales transaccionales
DELETE FROM remisiones;
DELETE FROM facturas;
DELETE FROM cargues;
DELETE FROM compras;

-- 3. Movimientos de inventario y logs de importacion
DELETE FROM movimientos_inventario;
DELETE FROM importaciones_excel;

-- 4. Resetear stock_actual de todos los productos a 0
--    (el stock real viene de las compras/cargues que acabamos de borrar)
UPDATE productos SET stock_actual = 0;

-- 5. Reiniciar secuencias de IDs a 1
ALTER SEQUENCE remisiones_id_seq          RESTART WITH 1;
ALTER SEQUENCE remision_detalles_id_seq   RESTART WITH 1;
ALTER SEQUENCE facturas_id_seq            RESTART WITH 1;
ALTER SEQUENCE factura_detalles_id_seq    RESTART WITH 1;
ALTER SEQUENCE cargues_id_seq             RESTART WITH 1;
ALTER SEQUENCE cargue_detalles_id_seq     RESTART WITH 1;
ALTER SEQUENCE compras_id_seq             RESTART WITH 1;
ALTER SEQUENCE compra_detalles_id_seq     RESTART WITH 1;
ALTER SEQUENCE movimientos_inventario_id_seq RESTART WITH 1;
ALTER SEQUENCE importaciones_excel_id_seq RESTART WITH 1;

COMMIT;

-- Verificacion: deben aparecer todas en 0
SELECT 'remisiones'          AS tabla, COUNT(*) FROM remisiones
UNION ALL
SELECT 'remision_detalles',            COUNT(*) FROM remision_detalles
UNION ALL
SELECT 'facturas',                     COUNT(*) FROM facturas
UNION ALL
SELECT 'cargues',                      COUNT(*) FROM cargues
UNION ALL
SELECT 'cargue_detalles',              COUNT(*) FROM cargue_detalles
UNION ALL
SELECT 'compras',                      COUNT(*) FROM compras
UNION ALL
SELECT 'movimientos_inventario',       COUNT(*) FROM movimientos_inventario
UNION ALL
SELECT 'productos (stock=0)',          COUNT(*) FROM productos WHERE stock_actual = 0;
