<template>
  <div class="inventario-page">
    <div class="page-toolbar">
      <div class="toolbar-left">
        <div class="search-wrapper">
          <i class="pi pi-search search-icon" />
          <InputText v-model="filtro" placeholder="Buscar producto..." class="search-input" />
        </div>
      </div>
      <div class="toolbar-right">
        <Button label="Ajustar stock" icon="pi pi-sliders-h" outlined @click="abrirAjuste" />
        <Button label="Importar Excel" icon="pi pi-file-excel" outlined severity="success" @click="abrirImportar" />
      </div>
    </div>

    <!-- Resumen -->
    <div class="resumen-grid">
      <div class="resumen-card">
        <span class="resumen-label">Total productos</span>
        <span class="resumen-value">{{ inventario.length }}</span>
      </div>
      <div class="resumen-card alerta" v-if="bajoStock.length > 0">
        <span class="resumen-label"><i class="pi pi-exclamation-triangle"></i> Stock critico</span>
        <span class="resumen-value">{{ bajoStock.length }}</span>
      </div>
    </div>

    <div class="card-table">
      <DataTable
        :value="inventarioFiltrado"
        :loading="loading"
        stripedRows
        paginator
        :rows="15"
        responsiveLayout="scroll"
        :rowClass="rowClass"
      >
        <Column field="nombreCompleto" header="Producto" sortable />
        <Column field="codigo" header="Cod. barras" sortable />
        <Column field="marca" header="Marca" sortable />
        <Column field="presentacion" header="Presentacion" />
        <Column field="unidadesPorCaja" header="Uds/Caja" style="width:90px" />
        <Column field="stockActual" header="Stock (unidades)" sortable style="width:130px">
          <template #body="{ data }">
            <span :class="{'stock-bajo': data.stockActual <= (data.stockMinimo || 0)}">
              {{ data.stockActual ?? 0 }}
            </span>
          </template>
        </Column>
        <Column header="Stock (cajas equiv.)">
          <template #body="{ data }">
            {{ data.unidadesPorCaja ? Math.floor(data.stockActual / data.unidadesPorCaja) : '-' }} cajas
          </template>
        </Column>
        <Column header="Valor inventario">
          <template #body="{ data }">
            {{ formatCOP((data.stockActual || 0) * (data.precioCaja / (data.unidadesPorCaja || 1))) }}
          </template>
        </Column>
        <Column header="Acciones" style="width:80px">
          <template #body="{ data }">
            <Button icon="pi pi-pencil" text rounded size="small" @click="editarInventario(data)" />
          </template>
        </Column>
        <template #empty>Sin productos en inventario</template>
      </DataTable>
    </div>

    <!-- Dialog ajuste -->
    <Dialog v-model:visible="dialogAjuste" header="Movimiento de Inventario" modal :style="{width:'520px'}" :draggable="false">
      <div class="ajuste-form">

        <!-- Tipo de movimiento -->
        <div class="tipo-movimiento-selector">
          <button
            class="tipo-btn"
            :class="{ 'tipo-btn--active tipo-btn--entrada': ajuste.tipo === 'ENTRADA' }"
            @click="ajuste.tipo = 'ENTRADA'; ajuste.cajas = 0; ajuste.sueltas = 0"
          >
            <i class="pi pi-arrow-down-circle" />
            Entrada de mercancía
          </button>
          <button
            class="tipo-btn"
            :class="{ 'tipo-btn--active tipo-btn--salida': ajuste.tipo === 'SALIDA' }"
            @click="ajuste.tipo = 'SALIDA'; ajuste.cajas = 0; ajuste.sueltas = 0"
          >
            <i class="pi pi-arrow-up-circle" />
            Salida / Merma
          </button>
          <button
            class="tipo-btn"
            :class="{ 'tipo-btn--active tipo-btn--correccion': ajuste.tipo === 'CORRECCION' }"
            @click="cambiarACorreccion"
          >
            <i class="pi pi-wrench" />
            Corrección física
          </button>
        </div>

        <div class="tipo-desc">
          <template v-if="ajuste.tipo === 'ENTRADA'">
            <i class="pi pi-info-circle" /> Ingresa cuántas unidades/cajas <strong>llegaron</strong>. Se sumarán al stock actual.
          </template>
          <template v-else-if="ajuste.tipo === 'SALIDA'">
            <i class="pi pi-info-circle" /> Ingresa cuántas unidades/cajas <strong>salieron</strong> (merma, daño, muestra). Se restarán.
          </template>
          <template v-else>
            <i class="pi pi-info-circle" /> Usá esto solo cuando hagas un <strong>conteo físico real</strong>. El stock se fijará al valor exacto que ingreses.
          </template>
        </div>

        <!-- Selector de producto (solo cuando se abre desde el botón general) -->
        <div class="field" v-if="!ajuste.fijado">
          <label>Producto</label>
          <Select
            v-model="ajuste.productoId"
            :options="inventario"
            optionValue="id"
            :optionLabel="p => p.nombreCompleto"
            filter
            placeholder="Seleccionar producto..."
            class="w-full"
            @change="onProductoSeleccionado"
          />
        </div>

        <!-- Stock actual (siempre visible, solo lectura) -->
        <div v-if="ajuste.productoId" class="stock-actual-bar">
          <i class="pi pi-box" style="color:#666" />
          <span>
            <strong>{{ productoSeleccionado?.nombreCompleto }}</strong> —
            Stock actual: <strong>{{ productoSeleccionado?.stockActual ?? 0 }}</strong> uds
            ({{ stockActualEnCajas }} cajas + {{ stockActualSueltas }} sueltas)
          </span>
        </div>

        <!-- Conversor cajas + sueltas → total -->
        <div class="conversor-box" v-if="ajuste.productoId">
          <div class="conversor-title">
            <i class="pi pi-calculator" />
            <span v-if="ajuste.tipo === 'ENTRADA'">Cajas / unidades que llegaron</span>
            <span v-else-if="ajuste.tipo === 'SALIDA'">Cajas / unidades que salen</span>
            <span v-else>Stock total (conteo físico) — {{ productoSeleccionado?.unidadesPorCaja ?? '?' }} uds/caja</span>
          </div>

          <div class="conversor-inputs">
            <div class="conversor-campo">
              <label>{{ ajuste.tipo === 'CORRECCION' ? 'Cajas contadas' : 'Cajas' }}</label>
              <InputNumber v-model="ajuste.cajas" :min="0" class="w-full" />
            </div>
            <div class="conversor-sep">+</div>
            <div class="conversor-campo">
              <label>Unidades sueltas</label>
              <InputNumber
                v-model="ajuste.sueltas"
                :min="0"
                :max="(productoSeleccionado?.unidadesPorCaja ?? 999) - 1"
                class="w-full"
              />
            </div>
          </div>

          <!-- Preview del resultado -->
          <div class="total-resultado" :class="{
            'total-entrada':    ajuste.tipo === 'ENTRADA',
            'total-salida':     ajuste.tipo === 'SALIDA',
            'total-correccion': ajuste.tipo === 'CORRECCION'
          }">
            <template v-if="ajuste.tipo === 'ENTRADA'">
              <span class="total-label">Stock resultante:</span>
              <span class="total-valor">
                {{ (productoSeleccionado?.stockActual ?? 0) + totalUnidades }} uds
              </span>
              <span class="total-cajas">
                (+{{ totalUnidades }} que llegan)
              </span>
            </template>
            <template v-else-if="ajuste.tipo === 'SALIDA'">
              <span class="total-label">Stock resultante:</span>
              <span class="total-valor">
                {{ Math.max(0, (productoSeleccionado?.stockActual ?? 0) - totalUnidades) }} uds
              </span>
              <span class="total-cajas">
                (-{{ totalUnidades }} que salen)
              </span>
            </template>
            <template v-else>
              <span class="total-label">Se fijará en:</span>
              <span class="total-valor">{{ totalUnidades }} uds</span>
              <span class="total-cajas">({{ cajasEquiv }} cajas completas)</span>
            </template>
          </div>
        </div>

        <div class="field">
          <label>Motivo *</label>
          <Textarea
            v-model="ajuste.motivo"
            rows="2"
            class="w-full"
            :placeholder="ajuste.tipo === 'ENTRADA'
              ? 'Ej: Pedido #123 — llegaron 2 cajas de Redbull'
              : ajuste.tipo === 'SALIDA'
              ? 'Ej: Merma por vencimiento, muestra a cliente'
              : 'Ej: Conteo físico mensual de mayo'"
          />
        </div>

        <Message v-if="ajusteError" severity="error" :closable="false">{{ ajusteError }}</Message>

        <div class="dialog-footer">
          <Button label="Cancelar" text @click="dialogAjuste = false" />
          <Button
            :label="ajuste.tipo === 'ENTRADA' ? 'Registrar entrada' : ajuste.tipo === 'SALIDA' ? 'Registrar salida' : 'Guardar corrección'"
            :icon="ajuste.tipo === 'ENTRADA' ? 'pi pi-arrow-down-circle' : ajuste.tipo === 'SALIDA' ? 'pi pi-arrow-up-circle' : 'pi pi-wrench'"
            :severity="ajuste.tipo === 'ENTRADA' ? 'success' : ajuste.tipo === 'SALIDA' ? 'warn' : 'secondary'"
            @click="guardarAjuste"
            :loading="guardando"
            :disabled="!ajuste.productoId || totalUnidades === 0"
          />
        </div>
      </div>
    </Dialog>

    <!-- Dialog importar Excel -->
    <Dialog v-model:visible="dialogImportar" header="Importar desde Excel" modal :style="{width:'440px'}" :draggable="false">
      <div class="importar-content">
        <p class="info-text">Descargue la plantilla, complete los datos de stock y suba el archivo.</p>
        <div class="import-actions">
          <Button label="Descargar plantilla" icon="pi pi-download" outlined severity="success" @click="descargarPlantilla" />
        </div>
        <div class="field" style="margin-top:1rem">
          <label>Seleccionar archivo Excel (.xlsx)</label>
          <input type="file" accept=".xlsx,.xls" @change="archivoSeleccionado" ref="fileInput" class="file-input" />
        </div>
        <div v-if="archivoExcel" class="archivo-info">
          <i class="pi pi-file-excel" style="color:#2E7D32"></i>
          {{ archivoExcel.name }}
        </div>
        <Message v-if="importError" severity="error" :closable="false">{{ importError }}</Message>
        <Message v-if="importExito" severity="success" :closable="false">{{ importExito }}</Message>
        <div class="dialog-footer">
          <Button label="Cancelar" text @click="dialogImportar = false" />
          <Button label="Importar" icon="pi pi-upload" @click="importarExcel" :loading="importando" :disabled="!archivoExcel" />
        </div>
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import api from '@/api/axios'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'

const toast = useToast()
const inventario = ref([])
const loading = ref(true)
const filtro = ref('')
const dialogAjuste = ref(false)
const dialogImportar = ref(false)
const guardando = ref(false)
const importando = ref(false)
const ajusteError = ref('')
const importError = ref('')
const importExito = ref('')
const archivoExcel = ref(null)
const fileInput = ref(null)

// Estado del diálogo de ajuste
// tipo: 'ENTRADA' (suma), 'SALIDA' (resta), 'CORRECCION' (fija valor absoluto)
const ajuste = ref({ productoId: null, cajas: 0, sueltas: 0, motivo: '', fijado: false, tipo: 'ENTRADA' })

// Producto actualmente seleccionado en el diálogo
const productoSeleccionado = computed(() =>
  inventario.value.find(p => p.id === ajuste.value.productoId) ?? null
)

// Total de unidades que se guardará = cajas × uds_por_caja + sueltas
const totalUnidades = computed(() => {
  const upc = productoSeleccionado.value?.unidadesPorCaja ?? 1
  return (ajuste.value.cajas ?? 0) * upc + (ajuste.value.sueltas ?? 0)
})

// Para mostrar en el resumen: cuántas cajas completas equivalen al total
const cajasEquiv = computed(() => {
  const upc = productoSeleccionado.value?.unidadesPorCaja ?? 1
  return Math.floor(totalUnidades.value / upc)
})

// Descompone el stock ACTUAL en cajas + sueltas (para la info del header)
const stockActualEnCajas = computed(() => {
  const p = productoSeleccionado.value
  if (!p) return 0
  return Math.floor((p.stockActual ?? 0) / p.unidadesPorCaja)
})
const stockActualSueltas = computed(() => {
  const p = productoSeleccionado.value
  if (!p) return 0
  return (p.stockActual ?? 0) % p.unidadesPorCaja
})

// Al seleccionar producto: para ENTRADA/SALIDA arranca en 0
// Para CORRECCION pre-carga el stock actual para que el usuario lo ajuste
function onProductoSeleccionado() {
  if (ajuste.value.tipo === 'CORRECCION') {
    ajuste.value.cajas   = stockActualEnCajas.value
    ajuste.value.sueltas = stockActualSueltas.value
  } else {
    ajuste.value.cajas   = 0
    ajuste.value.sueltas = 0
  }
}

// Cambia a modo corrección y pre-carga el stock actual
function cambiarACorreccion() {
  ajuste.value.tipo    = 'CORRECCION'
  ajuste.value.cajas   = stockActualEnCajas.value
  ajuste.value.sueltas = stockActualSueltas.value
}

function recalcular() {
  // La reactividad de computed hace el trabajo — este hook es solo para forzar
  // re-render si InputNumber no emite 'update:modelValue' de inmediato
}

const bajoStock = computed(() =>
  inventario.value.filter(p => p.stockActual <= (p.stockMinimo || 0))
)

const inventarioFiltrado = computed(() => {
  if (!filtro.value) return inventario.value
  const f = filtro.value.toLowerCase()
  return inventario.value.filter(p =>
    p.nombreCompleto?.toLowerCase().includes(f) ||
    p.marca?.toLowerCase().includes(f) ||
    p.codigo?.toLowerCase().includes(f)
  )
})

function rowClass(data) {
  return data.stockActual <= (data.stockMinimo || 0) ? 'row-alerta' : ''
}

function formatCOP(val) {
  if (!val && val !== 0) return '-'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(val)
}

// Abrir desde el botón general (sin producto preseleccionado)
function abrirAjuste() {
  ajuste.value = { productoId: null, cajas: 0, sueltas: 0, motivo: '', fijado: false, tipo: 'ENTRADA' }
  ajusteError.value = ''
  dialogAjuste.value = true
}

// Abrir desde el lápiz de una fila — producto fijo, empieza en ENTRADA desde 0
function editarInventario(prod) {
  ajuste.value = {
    productoId: prod.id,
    cajas:   0,
    sueltas: 0,
    motivo:  '',
    fijado:  true,
    tipo:    'ENTRADA'
  }
  ajusteError.value = ''
  dialogAjuste.value = true
}

async function guardarAjuste() {
  if (!ajuste.value.productoId) { ajusteError.value = 'Seleccione un producto'; return }
  if (!ajuste.value.motivo.trim()) { ajusteError.value = 'Ingrese el motivo'; return }
  if (totalUnidades.value === 0) { ajusteError.value = 'La cantidad debe ser mayor a 0'; return }

  const pid   = ajuste.value.productoId
  const uds   = totalUnidades.value
  const tipo  = ajuste.value.tipo
  const motivo = ajuste.value.motivo

  guardando.value = true
  try {
    if (tipo === 'CORRECCION') {
      // Fija el stock al valor exacto (conteo físico)
      await api.put(`/inventario/${pid}/corregir`, { stockReal: uds, motivo })
      toast.add({ severity: 'info', summary: 'Corrección registrada',
        detail: `Stock fijado en ${uds} unidades (${cajasEquiv.value} cajas)`, life: 3500 })
    } else {
      // ENTRADA suma, SALIDA resta (enviamos negativo para salida)
      const delta = tipo === 'SALIDA' ? -uds : uds
      await api.put(`/inventario/${pid}/ajustar`, { cantidad: delta, motivo })
      const prod = productoSeleccionado.value
      const nuevo = (prod?.stockActual ?? 0) + delta
      toast.add({
        severity: tipo === 'ENTRADA' ? 'success' : 'warn',
        summary: tipo === 'ENTRADA' ? '✅ Entrada registrada' : '⚠ Salida registrada',
        detail: `${tipo === 'ENTRADA' ? '+' : '-'}${uds} uds — Stock resultante: ${nuevo} uds`,
        life: 3500
      })
    }
    dialogAjuste.value = false
    cargarInventario()
  } catch (e) {
    ajusteError.value = e.response?.data?.mensaje || 'Error al ajustar'
  } finally {
    guardando.value = false
  }
}

function abrirImportar() {
  importError.value = ''
  importExito.value = ''
  archivoExcel.value = null
  dialogImportar.value = true
}

function archivoSeleccionado(e) {
  archivoExcel.value = e.target.files[0] || null
}

async function descargarPlantilla() {
  try {
    const res = await api.get('/inventario/plantilla-excel', { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = 'plantilla_inventario.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo descargar la plantilla', life: 4000 })
  }
}

async function importarExcel() {
  if (!archivoExcel.value) return
  importando.value = true
  importError.value = ''
  importExito.value = ''
  const formData = new FormData()
  formData.append('file', archivoExcel.value)
  try {
    const res = await api.post('/inventario/importar-excel', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    importExito.value = `Importacion exitosa: ${res.data?.importados || 0} productos actualizados`
    cargarInventario()
  } catch (e) {
    importError.value = e.response?.data?.mensaje || 'Error al importar'
  } finally {
    importando.value = false
  }
}

async function cargarInventario() {
  loading.value = true
  try {
    const res = await api.get('/inventario')
    inventario.value = res.data || []
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo cargar el inventario', life: 4000 })
  } finally {
    loading.value = false
  }
}

// Auto-refresh cuando la pestaña del navegador vuelve a estar visible
function onVisibilityChange() {
  if (document.visibilityState === 'visible') cargarInventario()
}

onMounted(() => {
  cargarInventario()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped>
.inventario-page { display: flex; flex-direction: column; gap: 1rem; }
.page-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  background: white; padding: 0.85rem 1.25rem; border-radius: 14px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm); gap: 1rem; flex-wrap: wrap;
}
.toolbar-left { flex: 0 0 auto; }
.toolbar-right { display: flex; align-items: center; gap: 0.65rem; flex-wrap: wrap; }
.search-wrapper { position: relative; display: flex; align-items: center; }
.search-icon { position: absolute; left: 0.9rem; color: #9ca3af; font-size: 0.88rem; pointer-events: none; z-index: 1; }
:deep(.search-input) {
  padding-left: 2.4rem !important; padding-right: 1rem !important;
  height: 40px; width: 260px; border-radius: 99px !important;
  border-color: #e5e7eb !important; background: #f9fafb !important;
  font-size: 0.875rem !important;
  transition: width 0.25s ease, border-color 0.2s, box-shadow 0.2s, background 0.2s !important;
}
:deep(.search-input:focus) {
  border-color: var(--color-primary) !important; background: #fff !important;
  box-shadow: 0 0 0 3px rgba(211,47,47,0.1) !important; width: 310px !important; outline: none !important;
}
:deep(.search-input::placeholder) { color: #b0b8c4 !important; font-size: 0.85rem !important; }
.resumen-grid { display: flex; gap: 1rem; flex-wrap: wrap; }
.resumen-card {
  background: white; border-radius: 10px; padding: 0.75rem 1.25rem;
  display: flex; align-items: center; gap: 0.75rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
}
.resumen-card.alerta { border-left: 4px solid #F57F17; }
.resumen-label { font-size: 0.85rem; color: #888; }
.resumen-value { font-size: 1.5rem; font-weight: 700; color: #333; }
.card-table { background: white; border-radius: 14px; padding: 1rem; border: 1px solid var(--color-border); box-shadow: var(--shadow-sm); }
.stock-bajo { color: #D32F2F; font-weight: 700; }
:deep(.row-alerta) { background: #fff8f8 !important; }
.ajuste-form, .importar-content { display: flex; flex-direction: column; gap: 1rem; }
.field { display: flex; flex-direction: column; gap: 0.35rem; }
.field label { font-size: 0.85rem; font-weight: 600; color: #444; }
.hint { color: #888; font-size: 0.78rem; }
.producto-sel { font-weight: 600; color: #333; }
.dialog-footer {
  display: flex; justify-content: flex-end; gap: 0.75rem;
  padding-top: 0.5rem; border-top: 1px solid #f0f0f0; margin-top: 0.5rem;
}
.info-text { color: #555; font-size: 0.9rem; }
.import-actions { display: flex; gap: 0.75rem; }
.file-input { border: 1px solid #ccc; border-radius: 6px; padding: 0.5rem; font-size: 0.85rem; cursor: pointer; }
.archivo-info { display: flex; align-items: center; gap: 0.5rem; background: #e8f5e9; padding: 0.5rem 0.75rem; border-radius: 6px; font-size: 0.85rem; }

/* ── Selector de tipo de movimiento ── */
.tipo-movimiento-selector {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.5rem;
}
.tipo-btn {
  display: flex; flex-direction: column; align-items: center; gap: 0.3rem;
  padding: 0.65rem 0.5rem; border: 2px solid #e0e0e0; border-radius: 10px;
  background: #fafafa; cursor: pointer; font-size: 0.78rem; font-weight: 600;
  color: #666; transition: all 0.15s;
}
.tipo-btn .pi { font-size: 1.2rem; }
.tipo-btn:hover { border-color: #bbb; background: #f0f0f0; }
.tipo-btn--active { color: white; border-color: transparent; }
.tipo-btn--entrada  { background: #2E7D32; border-color: #2E7D32; }
.tipo-btn--salida   { background: #E65100; border-color: #E65100; }
.tipo-btn--correccion { background: #1565C0; border-color: #1565C0; }

.tipo-desc {
  font-size: 0.82rem; color: #555; background: #f9f9f9;
  border-radius: 8px; padding: 0.55rem 0.85rem;
  display: flex; align-items: flex-start; gap: 0.4rem;
}

/* ── Stock actual (solo lectura) ── */
.stock-actual-bar {
  display: flex; align-items: center; gap: 0.6rem;
  background: #f0f4ff; border: 1px solid #c5d3f0; border-radius: 8px;
  padding: 0.6rem 0.9rem; font-size: 0.85rem; color: #333;
}

/* ── Header de producto fijo (legacy, se mantiene por compat) ── */
.producto-header {
  display: flex; align-items: flex-start; gap: 0.75rem;
  background: #fafafa; border: 1px solid #e8e8e8; border-radius: 8px; padding: 0.75rem 1rem;
}
.producto-header .pi { font-size: 1.4rem; margin-top: 2px; }
.producto-nombre { font-weight: 700; font-size: 1rem; color: #222; }
.producto-meta { font-size: 0.8rem; color: #777; margin-top: 2px; }

/* ── Caja del conversor ── */
.conversor-box {
  background: #fff8e1;
  border: 1px solid #ffe082;
  border-radius: 10px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}
.conversor-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: #6d4c00;
}
.conversor-inputs {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
}
.conversor-campo {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.conversor-campo label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #555;
}
.conversor-sep {
  font-size: 1.5rem;
  font-weight: 700;
  color: #888;
  padding-bottom: 0.3rem;
}

/* ── Total resultado ── */
.total-resultado {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  background: #fff;
  border: 1px solid #ffe082;
  border-radius: 8px;
  padding: 0.6rem 0.9rem;
}
.total-label { font-size: 0.82rem; color: #888; }
.total-valor { font-size: 1.3rem; font-weight: 800; color: #555; }
.total-cajas { font-size: 0.82rem; color: #666; }

.total-entrada  { background: #e8f5e9; border-color: #a5d6a7; }
.total-entrada  .total-valor { color: #2E7D32; }
.total-salida   { background: #fff3e0; border-color: #ffcc80; }
.total-salida   .total-valor { color: #E65100; }
.total-correccion { background: #e3f2fd; border-color: #90caf9; }
.total-correccion .total-valor { color: #1565C0; }
</style>
