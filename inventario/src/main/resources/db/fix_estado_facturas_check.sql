-- ─────────────────────────────────────────────────────────────────────────────
-- Migración: ampliar CHECK constraint de facturas.estado
--
-- Problema: Hibernate generó el constraint cuando solo existían
--   BORRADOR | EMITIDA | CON_NOTA_CREDITO | ANULADA
-- pero luego se agregó CON_NOTA_DEBITO al enum EstadoFactura de Java.
-- Al intentar guardar una factura con ese nuevo estado PostgreSQL rechaza la fila.
--
-- Ejecutar una sola vez contra la base de datos de producción/desarrollo:
--   psql -U <usuario> -d <base_de_datos> -f fix_estado_facturas_check.sql
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Eliminar el constraint viejo (el nombre lo genera Hibernate con el patrón
--    <tabla>_<columna>_check, pero usamos IF EXISTS por seguridad)
ALTER TABLE facturas DROP CONSTRAINT IF EXISTS facturas_estado_check;

-- 2. Recrear con los cinco valores actuales del enum EstadoFactura
ALTER TABLE facturas
  ADD CONSTRAINT facturas_estado_check
  CHECK (estado IN (
    'BORRADOR',
    'EMITIDA',
    'CON_NOTA_CREDITO',
    'CON_NOTA_DEBITO',
    'ANULADA'
  ));
