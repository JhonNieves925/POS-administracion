<template>
  <div class="reportes-page">

    <!-- Filtros comunes -->
    <div class="filtros-card">
      <div class="filtros-row">
        <div class="field-inline">
          <label>Desde</label>
          <Calendar v-model="filtros.desde" dateFormat="dd/mm/yy" />
        </div>
        <div class="field-inline">
          <label>Hasta</label>
          <Calendar v-model="filtros.hasta" dateFormat="dd/mm/yy" />
        </div>
        <div class="field-inline" v-if="activeTab !== 'inventario' && activeTab !== 'kardex' && activeTab !== 'nulas'">
          <label>Vendedor</label>
          <Select v-model="filtros.vendedorId" :options="vendedores" optionValue="id" :optionLabel="v => v.nombre" placeholder="Todos" showClear class="select-vend" />
        </div>
        <!-- Toggle desglose — solo pestaña ganancias -->
        <div class="field-inline" v-if="activeTab === 'ganancias'">
          <label>Desglose por producto</label>
          <ToggleButton v-model="mostrarDetalle" onLabel="Con desglose" offLabel="Solo cifras" onIcon="pi pi-list" offIcon="pi pi-chart-pie" />
        </div>
        <Button label="Generar reporte" icon="pi pi-chart-bar" @click="generarReporte" :loading="loading" />
        <Button v-if="activeTab !== 'ganancias' && activeTab !== 'kardex' && activeTab !== 'nulas'" label="Exportar Excel" icon="pi pi-file-excel" outlined severity="success" @click="exportarExcel" />
        <Button v-if="activeTab !== 'ganancias' && activeTab !== 'kardex' && activeTab !== 'nulas'" label="Exportar PDF" icon="pi pi-file-pdf" outlined severity="danger" @click="exportarPdf" />
      </div>
    </div>

    <!-- Reporte Ventas -->
    <div v-if="activeTab === 'ventas'" class="reporte-content">
      <div class="kpi-row">
        <div class="kpi-mini" v-for="k in kpisVentas" :key="k.label">
          <span class="kv">{{ k.value }}</span>
          <span class="kl">{{ k.label }}</span>
        </div>
      </div>

      <div class="card-table">
        <DataTable :value="reporteVentas" stripedRows paginator :rows="20" responsiveLayout="scroll">
          <Column header="Fecha">
            <template #body="{ data }">{{ formatFecha(data.fecha) }}</template>
          </Column>
          <Column header="Cargue">
            <template #body="{ data }">{{ data.numeroCargue }}</template>
          </Column>
          <Column header="Vendedor">
            <template #body="{ data }">{{ data.vendedor }}</template>
          </Column>
          <Column header="Producto">
            <template #body="{ data }">{{ data.producto }}</template>
          </Column>
          <Column field="cantidadVendida" header="Cant. vendida" />
          <Column header="Subtotal">
            <template #body="{ data }">{{ formatCOP(data.subtotal) }}</template>
          </Column>
          <template #empty>Genere un reporte para ver los datos</template>
        </DataTable>
      </div>
    </div>

    <!-- Reporte Inventario -->
    <div v-if="activeTab === 'inventario'" class="reporte-content">
      <div class="card-table">
        <DataTable :value="reporteInventario" stripedRows paginator :rows="20" responsiveLayout="scroll">
          <Column header="Producto">
            <template #body="{ data }">{{ data.nombreCompleto }}</template>
          </Column>
          <Column field="cantidadCajas" header="Cajas" />
          <Column field="cantidadUnidades" header="Unidades sueltas" />
          <Column header="Valor inventario">
            <template #body="{ data }">{{ formatCOP(data.valorInventario) }}</template>
          </Column>
          <template #empty>Genere un reporte para ver los datos</template>
        </DataTable>
      </div>
    </div>

    <!-- Reporte Devoluciones -->
    <div v-if="activeTab === 'devoluciones'" class="reporte-content">
      <div class="card-table">
        <DataTable :value="reporteDevoluciones" stripedRows paginator :rows="20" responsiveLayout="scroll">
          <Column header="Fecha">
            <template #body="{ data }">{{ formatFecha(data.fecha) }}</template>
          </Column>
          <Column field="vendedor" header="Vendedor" />
          <Column field="producto" header="Producto" />
          <Column field="cantidadDevuelta" header="Devuelto" />
          <Column header="Valor devolucion">
            <template #body="{ data }">{{ formatCOP(data.valorDevolucion) }}</template>
          </Column>
          <template #empty>Genere un reporte para ver los datos</template>
        </DataTable>
      </div>
    </div>

    <!-- Reporte Ganancias -->
    <div v-if="activeTab === 'ganancias'" class="reporte-content">
      <div v-if="!ganancias" class="empty-hint">
        <i class="pi pi-info-circle" /> Seleccione el período y presione <strong>Generar reporte</strong>
      </div>

      <template v-if="ganancias">
        <!-- KPI Cards -->
        <div class="ganancias-kpi-grid">
          <div class="gkpi gkpi-ingreso">
            <span class="gkpi-label">Ingreso base (sin impuestos)</span>
            <span class="gkpi-value">{{ formatCOP(ganancias.ingresoTotal) }}</span>
          </div>
          <div class="gkpi gkpi-costo">
            <span class="gkpi-label">Costo proveedor</span>
            <span class="gkpi-value">{{ formatCOP(ganancias.costoTotal) }}</span>
          </div>
          <div class="gkpi gkpi-ganancia" :class="ganancias.gananciaTotal < 0 ? 'negativo' : ''">
            <span class="gkpi-label">Ganancia neta</span>
            <span class="gkpi-value">{{ formatCOP(ganancias.gananciaTotal) }}</span>
            <span class="gkpi-sub">{{ ganancias.margenPorcentaje }}% margen</span>
          </div>
          <div class="gkpi gkpi-impuestos">
            <span class="gkpi-label">IVA recaudado</span>
            <span class="gkpi-value">{{ formatCOP(ganancias.ivaTotal) }}</span>
          </div>
          <div class="gkpi gkpi-impuestos">
            <span class="gkpi-label">IBUA recaudado</span>
            <span class="gkpi-value">{{ formatCOP(ganancias.ibuaTotal) }}</span>
          </div>
          <div class="gkpi gkpi-total">
            <span class="gkpi-label">Total facturado (con impuestos)</span>
            <span class="gkpi-value">{{ formatCOP(ganancias.totalConImpuestos) }}</span>
            <span class="gkpi-sub">{{ ganancias.totalRemisiones }} remisiones</span>
          </div>
        </div>

        <!-- Barra de margen visual -->
        <div class="margen-bar-card">
          <div class="margen-bar-label">
            <span>Margen bruto</span>
            <strong>{{ ganancias.margenPorcentaje }}%</strong>
          </div>
          <div class="margen-bar-track">
            <div
              class="margen-bar-fill"
              :style="{ width: Math.min(ganancias.margenPorcentaje, 100) + '%' }"
              :class="ganancias.margenPorcentaje < 10 ? 'bajo' : ganancias.margenPorcentaje < 25 ? 'medio' : 'alto'"
            />
          </div>
          <div class="margen-bar-hint">
            Período: {{ ganancias.desde }} → {{ ganancias.hasta }}
          </div>
        </div>

        <!-- Desglose por producto (solo si detalle=true) -->
        <div class="card-table" v-if="mostrarDetalle && gananciasDetalle.length">
          <div class="detalle-header">
            <i class="pi pi-list" />
            <span>Desglose por producto — ordenado por ganancia</span>
          </div>
          <DataTable :value="gananciasDetalle" stripedRows paginator :rows="20" responsiveLayout="scroll">

            <!-- Producto -->
            <Column field="producto" sortable style="min-width:200px">
              <template #header>
                <span class="col-header-tip" v-tooltip.top="'Nombre del producto tal como se vendió en el período seleccionado.'">
                  Producto <i class="pi pi-info-circle tip-icon" />
                </span>
              </template>
              <template #body="{ data }">{{ data.producto }}</template>
            </Column>

            <!-- Cajas vendidas -->
            <Column sortable field="cantidadVendida">
              <template #header>
                <span class="col-header-tip" v-tooltip.top="'Cajas completas despachadas. Se calcula dividiendo las unidades totales entre las unidades por caja del producto. Las unidades sueltas aparecen entre paréntesis.'">
                  Cajas vend. <i class="pi pi-info-circle tip-icon" />
                </span>
              </template>
              <template #body="{ data }">
                <span class="cant-cajas">
                  <template v-if="(data.unidadesPorCaja || 1) > 1">
                    <strong>{{ Math.floor(Number(data.cantidadVendida) / (data.unidadesPorCaja || 1)) }} caj</strong>
                    <span class="uds-hint">({{ Number(data.cantidadVendida) }} und)</span>
                  </template>
                  <template v-else>
                    {{ Number(data.cantidadVendida) }} und
                  </template>
                </span>
              </template>
            </Column>

            <!-- Ingreso -->
            <Column sortable field="ingreso">
              <template #header>
                <span class="col-header-tip" v-tooltip.top="'Valor total cobrado al cliente por este producto, sin IVA ni IBUA. Es el precio de venta × unidades vendidas.'">
                  Ingreso <i class="pi pi-info-circle tip-icon" />
                </span>
              </template>
              <template #body="{ data }">{{ formatCOP(data.ingreso) }}</template>
            </Column>

            <!-- Costo -->
            <Column sortable field="costo">
              <template #header>
                <span class="col-header-tip" v-tooltip.top="'Lo que le costó a la empresa comprarle este producto al proveedor. Se calcula con el precio de compra × unidades vendidas. Es la base para calcular la ganancia.'">
                  Costo <i class="pi pi-info-circle tip-icon" />
                </span>
              </template>
              <template #body="{ data }">{{ formatCOP(data.costo) }}</template>
            </Column>

            <!-- Ganancia -->
            <Column sortable field="ganancia">
              <template #header>
                <span class="col-header-tip" v-tooltip.top="'Ingreso − Costo. Es el dinero que queda en caja después de descontar lo que costó el producto. Si es negativo, se vendió por debajo del costo.'">
                  Ganancia <i class="pi pi-info-circle tip-icon" />
                </span>
              </template>
              <template #body="{ data }">
                <span :class="data.ganancia < 0 ? 'text-red' : 'text-green'">
                  {{ formatCOP(data.ganancia) }}
                </span>
              </template>
            </Column>

            <!-- Margen -->
            <Column>
              <template #header>
                <span class="col-header-tip" v-tooltip.top="'Porcentaje de ganancia sobre el ingreso. Ej: 55% significa que de cada $100 que entran, $55 son ganancia. Por encima del 25% es saludable; por debajo del 10% es bajo.'">
                  Margen <i class="pi pi-info-circle tip-icon" />
                </span>
              </template>
              <template #body="{ data }">
                <span :class="data.ingreso > 0 && (data.ganancia / data.ingreso * 100) < 10 ? 'text-red' : 'text-green'">
                  {{ data.ingreso > 0 ? Math.round(data.ganancia / data.ingreso * 100) : 0 }}%
                </span>
              </template>
            </Column>

            <template #empty>Sin datos de desglose</template>
          </DataTable>
        </div>
        <div class="empty-hint" v-else-if="mostrarDetalle && !gananciasDetalle.length">
          No hay datos de desglose para el período seleccionado.
        </div>
      </template>
    </div>

    <!-- ──────────────────────────────────────────────────────────
         Reporte Kardex — movimientos de inventario por producto
         ────────────────────────────────────────────────────────── -->
    <div v-if="activeTab === 'kardex'" class="reporte-content">

      <div v-if="!kardex" class="empty-hint">
        <i class="pi pi-info-circle" />
        Seleccione el período y presione <strong>Generar reporte</strong> para ver el movimiento de inventario
      </div>

      <template v-if="kardex">
        <!-- KPIs resumen del período -->
        <div class="kardex-kpi-row">
          <div class="kkpi kkpi-inicial">
            <span class="kkpi-label">Stock inicial total</span>
            <span class="kkpi-value">{{ kardexTotales.stockInicialCajas }} cajas</span>
            <span class="kkpi-sub">+ {{ kardexTotales.stockInicialSueltas }} sueltas</span>
          </div>
          <div class="kkpi kkpi-entradas">
            <span class="kkpi-label">Entradas (compras)</span>
            <span class="kkpi-value">{{ kardexTotales.entradasCajas }} cajas</span>
            <span class="kkpi-sub">+ {{ kardexTotales.entradasSueltas }} sueltas</span>
          </div>
          <div class="kkpi kkpi-ventas">
            <span class="kkpi-label">Ventas netas</span>
            <span class="kkpi-value">{{ kardexTotales.ventasCajas }} cajas</span>
            <span class="kkpi-sub">+ {{ kardexTotales.ventasSueltas }} sueltas</span>
          </div>
          <div class="kkpi kkpi-final">
            <span class="kkpi-label">Stock final actual</span>
            <span class="kkpi-value">{{ kardexTotales.stockFinalCajas }} cajas</span>
            <span class="kkpi-sub">+ {{ kardexTotales.stockFinalSueltas }} sueltas</span>
          </div>
        </div>

        <!-- Explicación de la fórmula -->
        <div class="kardex-formula-hint">
          <i class="pi pi-info-circle" />
          <span>
            <strong>Stock inicial</strong> + <strong>Entradas</strong> − <strong>Ventas</strong> = <strong>Stock final</strong>
            &nbsp;·&nbsp; Período: {{ filtros.desde ? formatLocalDate(filtros.desde) : '—' }} → {{ filtros.hasta ? formatLocalDate(filtros.hasta) : '—' }}
          </span>
        </div>

        <!-- Tabla detalle por producto -->
        <div class="card-table">
          <DataTable
            :value="kardex"
            stripedRows
            paginator
            :rows="25"
            responsiveLayout="scroll"
            :rowClass="(row) => row.stockFinalCajas === 0 && row.stockFinalSueltas === 0 ? 'row-agotado' : ''"
          >
            <Column field="producto" header="Producto" sortable style="min-width:200px" />

            <!-- Stock Inicial -->
            <Column header="Stock inicial" sortable field="stockInicialCajas">
              <template #body="{ data }">
                <span class="cajas-cell">
                  <span class="cajas-num">{{ data.stockInicialCajas }}</span>
                  <span class="cajas-label">cjs</span>
                  <span v-if="data.stockInicialSueltas > 0" class="sueltas-num">
                    + {{ data.stockInicialSueltas }}u
                  </span>
                </span>
              </template>
            </Column>

            <!-- Entradas (compras del período) -->
            <Column header="Entradas (compras)" sortable field="entradasCajas">
              <template #body="{ data }">
                <span class="cajas-cell entrada-cell" v-if="data.entradasCajas > 0 || data.entradasSueltas > 0">
                  <i class="pi pi-arrow-up" style="color:#2E7D32;font-size:0.75rem" />
                  <span class="cajas-num">{{ data.entradasCajas }}</span>
                  <span class="cajas-label">cjs</span>
                  <span v-if="data.entradasSueltas > 0" class="sueltas-num">+ {{ data.entradasSueltas }}u</span>
                </span>
                <span v-else class="text-muted">—</span>
              </template>
            </Column>

            <!-- Ventas netas -->
            <Column header="Ventas netas" sortable field="ventasCajas">
              <template #body="{ data }">
                <span class="cajas-cell salida-cell" v-if="data.ventasCajas > 0 || data.ventasSueltas > 0">
                  <i class="pi pi-arrow-down" style="color:#D32F2F;font-size:0.75rem" />
                  <span class="cajas-num">{{ data.ventasCajas }}</span>
                  <span class="cajas-label">cjs</span>
                  <span v-if="data.ventasSueltas > 0" class="sueltas-num">+ {{ data.ventasSueltas }}u</span>
                </span>
                <span v-else class="text-muted">—</span>
              </template>
            </Column>

            <!-- Devoluciones -->
            <Column header="Devoluciones" sortable field="devolucionesCajas">
              <template #body="{ data }">
                <span v-if="data.devolucionesCajas > 0 || data.devolucionesSueltas > 0" class="cajas-cell devol-cell">
                  <span class="cajas-num">{{ data.devolucionesCajas }}</span>
                  <span class="cajas-label">cjs</span>
                  <span v-if="data.devolucionesSueltas > 0" class="sueltas-num">+ {{ data.devolucionesSueltas }}u</span>
                </span>
                <span v-else class="text-muted">—</span>
              </template>
            </Column>

            <!-- Stock Final -->
            <Column header="Stock final" sortable field="stockFinalCajas">
              <template #body="{ data }">
                <span
                  class="cajas-cell"
                  :class="data.stockFinalCajas === 0 && data.stockFinalSueltas === 0 ? 'stock-agotado' : 'stock-ok'"
                >
                  <span class="cajas-num">{{ data.stockFinalCajas }}</span>
                  <span class="cajas-label">cjs</span>
                  <span v-if="data.stockFinalSueltas > 0" class="sueltas-num">+ {{ data.stockFinalSueltas }}u</span>
                  <Tag
                    v-if="data.stockFinalCajas === 0 && data.stockFinalSueltas === 0"
                    value="Agotado"
                    severity="danger"
                    style="font-size:0.7rem;margin-left:0.4rem"
                  />
                </span>
              </template>
            </Column>

            <template #empty>Sin datos de movimientos para el período seleccionado</template>
          </DataTable>
        </div>
      </template>
    </div>

    <!-- ──────────────────────────────────────────────────────────
         Reporte Estado de Cuenta — Créditos por período
         ────────────────────────────────────────────────────────── -->
    <div v-if="activeTab === 'estado-cuenta'" class="reporte-content">

      <div v-if="!reporteEC" class="empty-hint">
        <i class="pi pi-info-circle" />
        Seleccione el período y presione <strong>Generar reporte</strong>
        para ver los créditos otorgados en el rango
      </div>

      <template v-if="reporteEC">
        <!-- KPIs resumen -->
        <div class="ec-kpi-row">
          <div class="ec-kpi ec-kpi-total">
            <span class="ec-kpi-lbl">Créditos otorgados</span>
            <span class="ec-kpi-val">{{ formatCOP(reporteEC.totalVentas) }}</span>
          </div>
          <div class="ec-kpi ec-kpi-cobrado">
            <span class="ec-kpi-lbl">Total cobrado</span>
            <span class="ec-kpi-val">{{ formatCOP(reporteEC.totalCobrado) }}</span>
          </div>
          <div class="ec-kpi ec-kpi-pendiente">
            <span class="ec-kpi-lbl">Pendiente por cobrar</span>
            <span class="ec-kpi-val">{{ formatCOP(reporteEC.totalPendiente) }}</span>
          </div>
        </div>

        <!-- Tabla detallada -->
        <div class="card-table">
          <DataTable
            :value="reporteEC.filas"
            stripedRows paginator :rows="20"
            responsiveLayout="scroll"
          >
            <Column header="Remisión">
              <template #body="{ data }"><strong>{{ data.numero }}</strong></template>
            </Column>
            <Column header="Fecha">
              <template #body="{ data }">{{ formatFecha(data.fecha) }}</template>
            </Column>
            <Column header="Cliente">
              <template #body="{ data }">
                <div>{{ data.cliente }}</div>
                <small style="color:#aaa">{{ data.nit }}</small>
              </template>
            </Column>
            <Column header="Total crédito">
              <template #body="{ data }">{{ formatCOP(data.totalVenta) }}</template>
            </Column>
            <Column header="Cobrado">
              <template #body="{ data }">
                <span style="color:#2E7D32;font-weight:600">{{ formatCOP(data.montoPagado) }}</span>
              </template>
            </Column>
            <Column header="Saldo">
              <template #body="{ data }">
                <span :style="{ color: data.saldo > 0 ? '#C62828' : '#2E7D32', fontWeight: 700 }">
                  {{ data.saldo > 0 ? formatCOP(data.saldo) : '✓ Saldado' }}
                </span>
              </template>
            </Column>
            <Column header="Vence">
              <template #body="{ data }">{{ data.vencimiento ? formatFecha(data.vencimiento) : '—' }}</template>
            </Column>
            <Column header="Estado">
              <template #body="{ data }">
                <span :class="['ec-estado', 'ec-estado-' + (data.estadoPago || 'PENDIENTE').toLowerCase()]">
                  {{ { PENDIENTE: 'Pendiente', PARCIAL: 'Con abono', PAGADO: 'Pagado' }[data.estadoPago] || data.estadoPago }}
                </span>
              </template>
            </Column>
            <template #empty>Sin créditos en este período</template>
          </DataTable>
        </div>
      </template>
    </div>

    <!-- ──────────────────────────────────────────────────────────
         Reporte Nulas — remisiones anuladas
         ────────────────────────────────────────────────────────── -->
    <div v-if="activeTab === 'nulas'" class="reporte-content">

      <div v-if="!reporteNulas" class="empty-hint">
        <i class="pi pi-ban" />
        Seleccione el período y presione <strong>Generar reporte</strong>
        para ver las remisiones anuladas
      </div>

      <template v-if="reporteNulas">

        <!-- KPI Cards -->
        <div class="nulas-kpi-row">
          <div class="nulas-kpi nulas-kpi-count">
            <span class="nulas-kpi-lbl">Remisiones anuladas</span>
            <span class="nulas-kpi-val">{{ reporteNulas.totalAnuladas }}</span>
            <span class="nulas-kpi-sub">en el período</span>
          </div>
          <div class="nulas-kpi nulas-kpi-valor">
            <span class="nulas-kpi-lbl">Valor total anulado</span>
            <span class="nulas-kpi-val">{{ formatCOP(reporteNulas.totalValorAnulado) }}</span>
            <span class="nulas-kpi-sub">suma de todas las remisiones anuladas</span>
          </div>
          <div class="nulas-kpi nulas-kpi-productos" v-if="reporteNulas.desglosePorProducto?.length">
            <span class="nulas-kpi-lbl">Productos afectados</span>
            <span class="nulas-kpi-val">{{ reporteNulas.desglosePorProducto.length }}</span>
            <span class="nulas-kpi-sub">tipos de producto anulados</span>
          </div>
        </div>

        <!-- Desglose por producto -->
        <div class="card-table" v-if="reporteNulas.desglosePorProducto?.length">
          <div class="nulas-section-header">
            <i class="pi pi-chart-bar" />
            <span>Desglose por producto — unidades anuladas en el período</span>
          </div>
          <DataTable
            :value="reporteNulas.desglosePorProducto"
            stripedRows
            paginator
            :rows="20"
            responsiveLayout="scroll"
          >
            <Column field="producto" header="Producto" sortable style="min-width:200px" />

            <Column header="Unidades anuladas" sortable field="unidadesAnuladas">
              <template #body="{ data }">
                <span class="nulas-cant">
                  <template v-if="(data.unidadesPorCaja || 1) > 1">
                    <strong>{{ Math.floor(Number(data.unidadesAnuladas) / (data.unidadesPorCaja || 1)) }} caj</strong>
                    <span class="nulas-und">({{ Number(data.unidadesAnuladas) }} und)</span>
                  </template>
                  <template v-else>{{ Number(data.unidadesAnuladas) }} und</template>
                </span>
              </template>
            </Column>

            <Column header="Valor anulado" sortable field="valorAnulado">
              <template #body="{ data }">
                <span class="nulas-valor-cell">{{ formatCOP(data.valorAnulado) }}</span>
              </template>
            </Column>

            <Column header="Remisiones" sortable field="cantidadRemisiones">
              <template #body="{ data }">
                <Tag
                  :value="data.cantidadRemisiones + (data.cantidadRemisiones === 1 ? ' remisión' : ' remisiones')"
                  severity="danger"
                  style="font-size:0.75rem"
                />
              </template>
            </Column>

            <template #empty>Sin productos en remisiones anuladas</template>
          </DataTable>
        </div>

        <!-- Listado detallado de remisiones anuladas -->
        <div class="card-table">
          <div class="nulas-section-header">
            <i class="pi pi-list" />
            <span>Listado de remisiones anuladas</span>
          </div>
          <DataTable
            :value="reporteNulas.listado"
            stripedRows
            paginator
            :rows="20"
            responsiveLayout="scroll"
          >
            <Column header="Remisión">
              <template #body="{ data }"><strong>{{ data.numero }}</strong></template>
            </Column>
            <Column header="Fecha">
              <template #body="{ data }">{{ formatFecha(data.fecha) }}</template>
            </Column>
            <Column header="Cliente">
              <template #body="{ data }">{{ data.cliente }}</template>
            </Column>
            <Column header="Subtotal">
              <template #body="{ data }">{{ formatCOP(data.subtotal) }}</template>
            </Column>
            <Column header="IVA">
              <template #body="{ data }">{{ formatCOP(data.totalIva) }}</template>
            </Column>
            <Column header="Total">
              <template #body="{ data }">
                <strong style="color:#C62828">{{ formatCOP(data.total) }}</strong>
              </template>
            </Column>
            <Column header="Estado">
              <template #body>
                <Tag value="ANULADA" severity="danger" style="font-size:0.75rem" />
              </template>
            </Column>
            <template #empty>No hay remisiones anuladas en este período</template>
          </DataTable>
        </div>

      </template>
    </div>

    <!-- ── Relación de Ventas (Contable / PUC) ───────────────── -->
    <div v-if="activeTab === 'relacion-ventas'" class="reporte-content">

      <div v-if="!relacionVentas" class="empty-hint">
        <i class="pi pi-info-circle" />
        Seleccione el período y presione <strong>Generar reporte</strong> para ver la relación contable
      </div>

      <template v-if="relacionVentas">

        <!-- KPIs -->
        <div class="rv-kpi-row">
          <div class="rv-kpi">
            <span class="rv-kpi-lbl">Ventas de contado</span>
            <span class="rv-kpi-val">{{ formatCOP(relacionVentas.ventasContado) }}</span>
          </div>
          <div class="rv-kpi">
            <span class="rv-kpi-lbl">Ventas a crédito</span>
            <span class="rv-kpi-val">{{ formatCOP(relacionVentas.ventasCredito) }}</span>
          </div>
          <div class="rv-kpi rv-kpi-iva">
            <span class="rv-kpi-lbl">IVA generado</span>
            <span class="rv-kpi-val">{{ formatCOP(relacionVentas.totalIva) }}</span>
          </div>
          <div class="rv-kpi rv-kpi-nc" v-if="relacionVentas.totalNotasNC > 0">
            <span class="rv-kpi-lbl">Notas crédito emitidas</span>
            <span class="rv-kpi-val" style="color:#C62828">- {{ formatCOP(relacionVentas.totalNotasNC) }}</span>
          </div>
          <div class="rv-kpi rv-kpi-nd" v-if="relacionVentas.totalNotasND > 0">
            <span class="rv-kpi-lbl">Notas débito emitidas</span>
            <span class="rv-kpi-val" style="color:#1565C0">+ {{ formatCOP(relacionVentas.totalNotasND) }}</span>
          </div>
          <div class="rv-kpi rv-kpi-total">
            <span class="rv-kpi-lbl">Total neto del período</span>
            <span class="rv-kpi-val">{{ formatCOP(relacionVentas.totalGeneral) }}</span>
            <span class="rv-kpi-sub">{{ relacionVentas.totalRemisiones }} remisiones</span>
          </div>
        </div>

        <!-- Resumen por cuentas PUC para el contador -->
        <div class="rv-puc-card">
          <div class="rv-puc-header">
            <i class="pi pi-book" />
            <span>Resumen por cuentas PUC — Para importar a Siigo</span>
            <span class="rv-puc-periodo">Período: {{ relacionVentas.desde }} → {{ relacionVentas.hasta }}</span>
          </div>
          <div class="rv-puc-tabla">
            <div class="rv-puc-row rv-puc-head">
              <span>Cuenta PUC</span>
              <span>Concepto</span>
              <span>Grupo</span>
              <span style="text-align:right">Valor</span>
            </div>
            <div
              class="rv-puc-row"
              v-for="(row, i) in relacionVentas.resumenPuc"
              :key="i"
              :class="'rv-grupo-' + row.grupo.toLowerCase()"
            >
              <span class="rv-cuenta">{{ row.cuentaPuc }}</span>
              <span>{{ row.nombre }}</span>
              <span><Tag :value="row.grupo" :severity="grupoPucSeverity(row.grupo)" style="font-size:0.7rem" /></span>
              <span class="rv-valor" :class="row.valor < 0 ? 'negativo' : ''">{{ formatCOP(row.valor) }}</span>
            </div>
          </div>
        </div>

        <!-- Tabla detalle de remisiones -->
        <div class="card-table">
          <div class="rv-detalle-header">
            <i class="pi pi-list" />
            <span>Detalle de remisiones del período</span>
          </div>
          <DataTable :value="relacionVentas.filas" stripedRows paginator :rows="25" responsiveLayout="scroll">
            <Column header="Fecha">
              <template #body="{ data }">{{ formatFecha(data.fecha) }}</template>
            </Column>
            <Column header="Remisión" field="numero" />
            <Column header="Factura DIAN">
              <template #body="{ data }">
                <span v-if="data.facturaDian" class="tag-factura">{{ data.facturaDian }}</span>
                <span v-else class="text-muted">—</span>
              </template>
            </Column>
            <Column header="Cliente">
              <template #body="{ data }">
                <div>{{ data.cliente }}</div>
                <small style="color:#aaa">{{ data.nit }}</small>
              </template>
            </Column>
            <Column header="Forma pago">
              <template #body="{ data }">
                <Tag :value="data.formaPago" :severity="data.formaPago === 'CREDITO' || data.formaPago === 'CHEQUE' ? 'warn' : 'success'" style="font-size:0.72rem" />
              </template>
            </Column>
            <Column header="Subtotal">
              <template #body="{ data }">{{ formatCOP(data.subtotal) }}</template>
            </Column>
            <Column header="IVA">
              <template #body="{ data }">{{ formatCOP(data.iva) }}</template>
            </Column>
            <Column header="Total">
              <template #body="{ data }"><strong>{{ formatCOP(data.total) }}</strong></template>
            </Column>
            <Column header="Cta. contable">
              <template #body="{ data }">
                <span class="rv-cuenta">{{ data.cuentaVenta }}</span>
              </template>
            </Column>
            <template #empty>Sin remisiones en el período</template>
          </DataTable>
        </div>
      </template>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import api from '@/api/axios'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Calendar from 'primevue/calendar'
import ToggleButton from 'primevue/togglebutton'

const toast = useToast()
const route = useRoute()
const auth  = useAuthStore()

// ── Control de acceso por rol ─────────────────────────────────────────────
// ADMIN     → todos los reportes
// AUXILIAR  → solo los operativos (sin cifras de ganancia ni costos)
const TABS_ADMIN = ['ventas','inventario','devoluciones','ganancias','kardex']
const TABS_COMUNES = ['estado-cuenta','relacion-ventas','nulas']

const tabsPermitidos = computed(() =>
  auth.esAdmin ? [...TABS_ADMIN, ...TABS_COMUNES] : TABS_COMUNES
)

// Sincroniza el tab activo con ?tab=xxx en la URL (vía submenú del sidebar)
// Si el tab pedido no está permitido para este rol, aterriza en el primero permitido
const tabsValidos = ['ventas','inventario','devoluciones','ganancias','kardex','estado-cuenta','relacion-ventas','nulas']
const resolverTab = (tab) =>
  tabsPermitidos.value.includes(tab) ? tab : tabsPermitidos.value[0]

const activeTab = ref(resolverTab(tabsValidos.includes(route.query.tab) ? route.query.tab : 'estado-cuenta'))

watch(() => route.query.tab, (tab) => {
  if (tab && tabsValidos.includes(tab)) activeTab.value = resolverTab(tab)
})
const loading = ref(false)
const vendedores = ref([])
const reporteVentas = ref([])
const reporteInventario = ref([])
const reporteDevoluciones = ref([])

// Ganancias
const ganancias = ref(null)          // resumen general
const gananciasDetalle = ref([])     // desglose por producto
const mostrarDetalle = ref(false)    // toggle desglose

// Kardex
const kardex = ref(null)

// Estado de Cuenta
const reporteEC = ref(null)

// Relación de ventas contable
const relacionVentas = ref(null)

// Nulas — remisiones anuladas
const reporteNulas = ref(null)

const kardexTotales = computed(() => {
  if (!kardex.value) return {}
  return kardex.value.reduce((acc, row) => {
    acc.stockInicialCajas  = (acc.stockInicialCajas  || 0) + (row.stockInicialCajas  || 0)
    acc.stockInicialSueltas= (acc.stockInicialSueltas|| 0) + (row.stockInicialSueltas|| 0)
    acc.entradasCajas      = (acc.entradasCajas      || 0) + (row.entradasCajas      || 0)
    acc.entradasSueltas    = (acc.entradasSueltas    || 0) + (row.entradasSueltas    || 0)
    acc.ventasCajas        = (acc.ventasCajas        || 0) + (row.ventasCajas        || 0)
    acc.ventasSueltas      = (acc.ventasSueltas      || 0) + (row.ventasSueltas      || 0)
    acc.stockFinalCajas    = (acc.stockFinalCajas    || 0) + (row.stockFinalCajas    || 0)
    acc.stockFinalSueltas  = (acc.stockFinalSueltas  || 0) + (row.stockFinalSueltas  || 0)
    return acc
  }, {})
})

// Definición completa — se filtra por rol antes de mostrarse
const TODOS_LOS_TABS = [
  { id: 'ventas',          label: 'Ventas',              icon: 'pi pi-chart-line',  soloAdmin: true  },
  { id: 'inventario',      label: 'Inventario',          icon: 'pi pi-box',         soloAdmin: true  },
  { id: 'devoluciones',    label: 'Devoluciones',        icon: 'pi pi-replay',      soloAdmin: true  },
  { id: 'ganancias',       label: 'Ganancias',           icon: 'pi pi-dollar',      soloAdmin: true  },
  { id: 'kardex',          label: 'Kardex',              icon: 'pi pi-arrows-v',    soloAdmin: true  },
  { id: 'estado-cuenta',   label: 'Estado de Cuenta',   icon: 'pi pi-wallet',      soloAdmin: false },
  { id: 'relacion-ventas', label: 'Relación de Ventas',  icon: 'pi pi-book',        soloAdmin: false },
  { id: 'nulas',           label: 'Nulas',               icon: 'pi pi-ban',         soloAdmin: false }
]

// Solo muestra los tabs que el rol actual puede ver
const tabs = computed(() =>
  TODOS_LOS_TABS.filter(t => !t.soloAdmin || auth.esAdmin)
)

const ahora = new Date()
const filtros = ref({
  desde: new Date(ahora.getFullYear(), ahora.getMonth(), 1),
  hasta: new Date(),
  vendedorId: null
})

const kpisVentas = computed(() => {
  const total = reporteVentas.value.reduce((s, r) => s + (r.subtotal || 0), 0)
  const totalUds = reporteVentas.value.reduce((s, r) => s + (r.cantidadVendida || 0), 0)
  const cargues = new Set(reporteVentas.value.map(r => r.numeroCargue)).size
  return [
    { label: 'Total ventas', value: formatCOP(total) },
    { label: 'Unidades vendidas', value: totalUds },
    { label: 'Cargues incluidos', value: cargues }
  ]
})

function formatLocalDate(date) {
  if (!date) return ''
  const d = date instanceof Date ? date : new Date(date)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

function grupoPucSeverity(grupo) {
  const map = { INGRESOS: 'success', ACTIVO: 'info', PASIVO: 'warn', COSTO: 'danger' }
  return map[grupo] || 'secondary'
}

function formatFecha(f) { return f ? new Date(f).toLocaleDateString('es-CO') : '-' }
function formatCOP(v) {
  if (!v && v !== 0) return '-'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(v)
}

function buildParams() {
  return {
    desde: filtros.value.desde ? formatLocalDate(filtros.value.desde) : undefined,
    hasta: filtros.value.hasta ? formatLocalDate(filtros.value.hasta) : undefined,
    vendedorId: filtros.value.vendedorId
  }
}

async function generarReporte() {
  loading.value = true
  try {
    if (activeTab.value === 'ventas') {
      const res = await api.get('/reportes/ventas', { params: buildParams() })
      reporteVentas.value = res.data
    } else if (activeTab.value === 'inventario') {
      const res = await api.get('/reportes/inventario')
      reporteInventario.value = res.data
    } else if (activeTab.value === 'devoluciones') {
      const res = await api.get('/reportes/devoluciones', { params: buildParams() })
      reporteDevoluciones.value = res.data
    } else if (activeTab.value === 'ganancias') {
      const res = await api.get('/reportes/ganancias', {
        params: { ...buildParams(), detalle: mostrarDetalle.value }
      })
      ganancias.value = res.data
      gananciasDetalle.value = res.data.detallePorProducto || []
    } else if (activeTab.value === 'kardex') {
      const res = await api.get('/reportes/kardex', { params: buildParams() })
      kardex.value = res.data
    } else if (activeTab.value === 'estado-cuenta') {
      const p = buildParams()
      const res = await api.get('/estado-cuenta/reporte', {
        params: { inicio: p.desde, fin: p.hasta }
      })
      reporteEC.value = res.data
    } else if (activeTab.value === 'relacion-ventas') {
      const res = await api.get('/reportes/relacion-ventas', { params: buildParams() })
      relacionVentas.value = res.data
    } else if (activeTab.value === 'nulas') {
      const res = await api.get('/reportes/nulas', { params: buildParams() })
      reporteNulas.value = res.data
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo generar el reporte', life: 4000 })
  } finally {
    loading.value = false
  }
}

async function exportarExcel() {
  try {
    const res = await api.get(`/reportes/${activeTab.value}/excel`, {
      params: buildParams(),
      responseType: 'blob'
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `reporte_${activeTab.value}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo exportar el Excel', life: 4000 })
  }
}

async function exportarPdf() {
  try {
    const res = await api.get(`/reportes/${activeTab.value}/pdf`, {
      params: buildParams(),
      responseType: 'blob'
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `reporte_${activeTab.value}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo exportar el PDF', life: 4000 })
  }
}

async function cargarVendedores() {
  try {
    const res = await api.get('/vendedores')
    vendedores.value = res.data
  } catch {}
}

onMounted(cargarVendedores)
</script>

<style scoped>
.reportes-page { display: flex; flex-direction: column; gap: 1rem; }


.filtros-card {
  background: white; padding: 1rem 1.25rem;
  border-radius: 10px; box-shadow: 0 2px 6px rgba(0,0,0,0.05);
}

.filtros-row {
  display: flex; gap: 1rem; flex-wrap: wrap; align-items: flex-end;
}

.field-inline { display: flex; flex-direction: column; gap: 0.35rem; }
.field-inline label { font-size: 0.82rem; font-weight: 600; color: #555; }
.select-vend { width: 180px; }

.kpi-row {
  display: flex; gap: 1rem; flex-wrap: wrap;
}

.kpi-mini {
  background: white; border-radius: 10px; padding: 1rem 1.5rem;
  display: flex; flex-direction: column; gap: 0.25rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05); min-width: 160px;
}

.kv { font-size: 1.4rem; font-weight: 700; color: #D32F2F; }
.kl { font-size: 0.82rem; color: #888; }

.card-table { background: white; border-radius: 10px; padding: 1rem; box-shadow: 0 2px 6px rgba(0,0,0,0.05); }
.reporte-content { display: flex; flex-direction: column; gap: 1rem; }

/* ── Ganancias ── */
.ganancias-kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.gkpi {
  background: white;
  border-radius: 12px;
  padding: 1.1rem 1.25rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  border-left: 4px solid transparent;
}
.gkpi-label { font-size: 0.75rem; color: #888; font-weight: 600; text-transform: uppercase; letter-spacing: 0.3px; }
.gkpi-value { font-size: 1.35rem; font-weight: 800; color: #333; }
.gkpi-sub   { font-size: 0.78rem; color: #aaa; }

.gkpi-ingreso  { border-left-color: #1565C0; }
.gkpi-ingreso .gkpi-value { color: #1565C0; }

.gkpi-costo    { border-left-color: #E65100; }
.gkpi-costo .gkpi-value { color: #E65100; }

.gkpi-ganancia { border-left-color: #2E7D32; }
.gkpi-ganancia .gkpi-value { color: #2E7D32; }
.gkpi-ganancia.negativo { border-left-color: #D32F2F; }
.gkpi-ganancia.negativo .gkpi-value { color: #D32F2F; }

.gkpi-impuestos { border-left-color: #6A1B9A; }
.gkpi-impuestos .gkpi-value { color: #6A1B9A; }

.gkpi-total { border-left-color: #00695C; background: #E0F2F1; }
.gkpi-total .gkpi-value { color: #00695C; }

/* Barra margen */
.margen-bar-card {
  background: white;
  border-radius: 12px;
  padding: 1rem 1.25rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.margen-bar-label {
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
  color: #666;
}
.margen-bar-label strong { font-size: 1rem; color: #333; }
.margen-bar-track {
  height: 12px;
  background: #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
}
.margen-bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.5s ease;
}
.margen-bar-fill.bajo  { background: #D32F2F; }
.margen-bar-fill.medio { background: #F57F17; }
.margen-bar-fill.alto  { background: #2E7D32; }
.margen-bar-hint { font-size: 0.75rem; color: #aaa; }

/* Desglose */
.detalle-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  color: #555;
  margin-bottom: 0.75rem;
  font-size: 0.9rem;
}

.text-green { color: #2E7D32; font-weight: 700; }
.text-red   { color: #D32F2F; font-weight: 700; }

/* Cabeceras con tooltip */
.col-header-tip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  cursor: default;
  white-space: nowrap;
}
.tip-icon {
  font-size: 0.72rem;
  color: #bbb;
  transition: color 0.15s;
}
.col-header-tip:hover .tip-icon { color: #1565C0; }

/* Cajas + unidades en desglose */
.cant-cajas { display: flex; flex-direction: column; gap: 0.05rem; }
.cant-cajas strong { font-size: 0.95rem; color: #333; }
.uds-hint { font-size: 0.72rem; color: #aaa; }

.empty-hint {
  background: white;
  border-radius: 10px;
  padding: 2rem;
  text-align: center;
  color: #aaa;
  font-size: 0.9rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
}

/* ── Kardex ─────────────────────────────────────────────────── */
.kardex-kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 1rem;
}

.kkpi {
  background: white;
  border-radius: 12px;
  padding: 1rem 1.25rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  border-left: 4px solid transparent;
}
.kkpi-label { font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.3px; color: #888; }
.kkpi-value { font-size: 1.4rem; font-weight: 800; }
.kkpi-sub   { font-size: 0.78rem; color: #aaa; }

.kkpi-inicial  { border-left-color: #546E7A; }
.kkpi-inicial .kkpi-value  { color: #546E7A; }

.kkpi-entradas { border-left-color: #2E7D32; }
.kkpi-entradas .kkpi-value { color: #2E7D32; }

.kkpi-ventas   { border-left-color: #D32F2F; }
.kkpi-ventas .kkpi-value   { color: #D32F2F; }

.kkpi-final    { border-left-color: #1565C0; }
.kkpi-final .kkpi-value    { color: #1565C0; }

/* Fórmula hint */
.kardex-formula-hint {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: #E3F2FD;
  border-radius: 8px;
  padding: 0.6rem 1rem;
  font-size: 0.85rem;
  color: #1565C0;
}
.kardex-formula-hint i { flex-shrink: 0; }

/* Celdas de cajas */
.cajas-cell {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
}
.cajas-num   { font-weight: 700; font-size: 0.95rem; }
.cajas-label { font-size: 0.72rem; color: #888; }
.sueltas-num { font-size: 0.78rem; color: #999; }

.entrada-cell .cajas-num { color: #2E7D32; }
.salida-cell  .cajas-num { color: #D32F2F; }
.devol-cell   .cajas-num { color: #E65100; }

.stock-ok     .cajas-num { color: #1565C0; font-weight: 800; }
.stock-agotado .cajas-num { color: #D32F2F; }

.text-muted { color: #ccc; font-size: 0.82rem; }

/* Fila de producto agotado */
:deep(.row-agotado) { background: #fff8f8 !important; }

/* ── Estado de Cuenta ───────────────────────────────────────── */
.ec-kpi-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin-bottom: 1rem;
}
.ec-kpi {
  background: white; border-radius: 12px;
  padding: 1rem 1.25rem; box-shadow: 0 2px 6px rgba(0,0,0,0.05);
  display: flex; flex-direction: column; gap: 0.25rem;
  border-left: 4px solid transparent;
}
.ec-kpi-total    { border-left-color: #1565C0; }
.ec-kpi-cobrado  { border-left-color: #2E7D32; }
.ec-kpi-pendiente{ border-left-color: #C62828; }
.ec-kpi-lbl { font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.3px; color: #888; }
.ec-kpi-val { font-size: 1.3rem; font-weight: 800; }
.ec-kpi-total    .ec-kpi-val { color: #1565C0; }
.ec-kpi-cobrado  .ec-kpi-val { color: #2E7D32; }
.ec-kpi-pendiente .ec-kpi-val { color: #C62828; }

/* Badges de estado */
.ec-estado {
  padding: 2px 8px; border-radius: 10px;
  font-size: 0.73rem; font-weight: 700;
}
.ec-estado-pendiente { background: #FFF3E0; color: #E65100; }
.ec-estado-parcial   { background: #E3F2FD; color: #1565C0; }
.ec-estado-pagado    { background: #E8F5E9; color: #2E7D32; }

/* ── Relación de Ventas / PUC ───────────────────────────────── */
.rv-kpi-row {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 0.75rem;
  margin-bottom: 1rem;
}
.rv-kpi {
  background: white; border-radius: 10px; padding: 0.9rem 1rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06); display: flex; flex-direction: column; gap: 0.2rem;
  border-left: 4px solid #1565C0;
}
.rv-kpi-iva   { border-left-color: #E65100; }
.rv-kpi-nc    { border-left-color: #C62828; }
.rv-kpi-nd    { border-left-color: #1565C0; }
.rv-kpi-total { border-left-color: #2E7D32; }
.rv-kpi-lbl { font-size: 0.72rem; font-weight: 700; text-transform: uppercase; color: #888; }
.rv-kpi-val { font-size: 1.2rem; font-weight: 800; color: #222; }
.rv-kpi-sub { font-size: 0.72rem; color: #aaa; }

.rv-puc-card {
  background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  overflow: hidden; margin-bottom: 1rem;
}
.rv-puc-header {
  background: #1565C0; color: white; padding: 0.75rem 1rem;
  display: flex; align-items: center; gap: 0.6rem; font-size: 0.92rem; font-weight: 600;
}
.rv-puc-periodo { margin-left: auto; font-size: 0.8rem; opacity: 0.85; }

.rv-puc-tabla { padding: 0.5rem 0; }
.rv-puc-row {
  display: grid; grid-template-columns: 90px 1fr 100px 140px;
  padding: 0.55rem 1rem; font-size: 0.85rem; align-items: center;
  border-bottom: 1px solid #f5f5f5;
}
.rv-puc-head {
  font-size: 0.72rem; font-weight: 700; text-transform: uppercase;
  color: #888; background: #f8f9fa; border-bottom: 2px solid #eee;
}
.rv-cuenta {
  font-family: monospace; font-weight: 700; color: #1565C0;
  background: #EEF2FF; padding: 2px 6px; border-radius: 4px; font-size: 0.82rem;
}
.rv-valor { text-align: right; font-weight: 700; color: #222; }
.rv-valor.negativo { color: #C62828; }

.rv-detalle-header {
  display: flex; align-items: center; gap: 0.5rem;
  padding: 0.75rem 1rem; font-size: 0.9rem; font-weight: 600; color: #444;
  border-bottom: 1px solid #eee;
}
.tag-factura {
  background: #E8F5E9; color: #2E7D32; font-weight: 700;
  padding: 2px 8px; border-radius: 4px; font-size: 0.8rem;
}

/* ── Nulas ──────────────────────────────────────────────────── */
.nulas-kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}
.nulas-kpi {
  background: white;
  border-radius: 12px;
  padding: 1.1rem 1.25rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  border-left: 4px solid transparent;
}
.nulas-kpi-lbl { font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.3px; color: #888; }
.nulas-kpi-val { font-size: 1.5rem; font-weight: 800; }
.nulas-kpi-sub { font-size: 0.75rem; color: #aaa; }

.nulas-kpi-count   { border-left-color: #C62828; }
.nulas-kpi-count   .nulas-kpi-val { color: #C62828; }
.nulas-kpi-valor   { border-left-color: #E65100; }
.nulas-kpi-valor   .nulas-kpi-val { color: #E65100; }
.nulas-kpi-productos { border-left-color: #6A1B9A; }
.nulas-kpi-productos .nulas-kpi-val { color: #6A1B9A; }

.nulas-section-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  color: #555;
  margin-bottom: 0.75rem;
  font-size: 0.9rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #eee;
}

.nulas-cant { display: flex; flex-direction: column; gap: 0.05rem; }
.nulas-cant strong { font-size: 0.95rem; color: #C62828; }
.nulas-und { font-size: 0.72rem; color: #aaa; }
.nulas-valor-cell { color: #C62828; font-weight: 700; }
</style>
