-- ============================================================
-- MIGRACION: columnas IBUA, precio_compra, siigo y costos
-- Ejecutar una sola vez contra la BD de PostgreSQL
-- ============================================================

-- ── 1. tabla remisiones ──────────────────────────────────────
-- IBUA total de la remision
ALTER TABLE remisiones
    ADD COLUMN IF NOT EXISTS total_ibua NUMERIC(14,2) NOT NULL DEFAULT 0;

-- Campos facturacion electronica Siigo / DIAN
ALTER TABLE remisiones
    ADD COLUMN IF NOT EXISTS siigo_invoice_id     VARCHAR(80);
ALTER TABLE remisiones
    ADD COLUMN IF NOT EXISTS numero_factura_dian  VARCHAR(20);
ALTER TABLE remisiones
    ADD COLUMN IF NOT EXISTS fecha_facturacion    TIMESTAMP;
ALTER TABLE remisiones
    ADD COLUMN IF NOT EXISTS error_facturacion    VARCHAR(500);

-- ── 2. tabla remision_detalles ───────────────────────────────
-- Costo unitario al momento de la venta (para reporte de ganancias)
ALTER TABLE remision_detalles
    ADD COLUMN IF NOT EXISTS costo_unitario      NUMERIC(14,4);

-- IBUA por unidad (snapshot del producto al momento de vender)
ALTER TABLE remision_detalles
    ADD COLUMN IF NOT EXISTS valor_ibua_unidad   NUMERIC(14,2) NOT NULL DEFAULT 0;

-- ── 3. tabla productos ───────────────────────────────────────
-- Precio de compra al proveedor (piso para precio de venta del vendedor)
ALTER TABLE productos
    ADD COLUMN IF NOT EXISTS precio_compra       NUMERIC(14,2);

-- IBUA en COP por unidad (0 = no aplica)
ALTER TABLE productos
    ADD COLUMN IF NOT EXISTS valor_ibua_unidad   NUMERIC(14,2) NOT NULL DEFAULT 0;

-- ── Verificacion ─────────────────────────────────────────────
SELECT column_name, data_type, is_nullable, column_default
FROM   information_schema.columns
WHERE  table_name IN ('remisiones','remision_detalles','productos')
  AND  column_name IN (
         'total_ibua','siigo_invoice_id','numero_factura_dian',
         'fecha_facturacion','error_facturacion',
         'costo_unitario','valor_ibua_unidad','precio_compra'
       )
ORDER  BY table_name, column_name;
