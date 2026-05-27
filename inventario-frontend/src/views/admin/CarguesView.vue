<template>
  <div class="cargues-page">
    <div class="page-toolbar">
      <div class="toolbar-left">
        <div class="search-wrapper">
          <i class="pi pi-search search-icon" />
          <InputText v-model="filtro" placeholder="Buscar vendedor, numero..." class="search-input" />
        </div>
        <Select v-model="filtroEstado" :options="estados" placeholder="Todos los estados" showClear class="select-estado" />
        <Calendar v-model="filtroDia" dateFormat="dd/mm/yy" placeholder="Filtrar por dia" showClear />
      </div>
      <div class="toolbar-right">
        <Button
          label="Limpiar todo"
          icon="pi pi-trash"
          severity="danger"
          outlined
          size="small"
          @click="confirmarLimpiarTodo"
          v-tooltip.top="'Eliminar todos los cargues y sus remisiones'"
        />
        <Button label="Nuevo cargue" icon="pi pi-plus" @click="abrirNuevoCargue" />
      </div>
    </div>

    <div class="card-table">
      <DataTable :value="carguesFiltrados" :loading="loading" stripedRows paginator :rows="15" responsiveLayout="scroll">
        <Column field="numero" header="Numero" sortable style="width:150px" />
        <Column header="Vendedor">
          <template #body="{ data }">{{ data.vendedor?.nombre }}</template>
        </Column>
        <Column header="Fecha">
          <template #body="{ data }">{{ formatFecha(data.fecha) }}</template>
        </Column>
        <Column header="Estado">
          <template #body="{ data }">
            <Tag :value="estadoLabel(data.estado)" :severity="getSeverity(data.estado)" />
          </template>
        </Column>
        <Column header="Productos">
          <template #body="{ data }">{{ data.detalles?.length || 0 }} items</template>
        </Column>
        <Column header="Total unidades">
          <template #body="{ data }">
            {{ data.detalles?.reduce((s, d) => s + d.cantidadCargue, 0) || 0 }}
          </template>
        </Column>
        <Column header="Total venta *">
          <template #body="{ data }">
            <span v-tooltip.top="'Calculado como (cargado − devuelto) × precio snapshot. Puede diferir del total de remisiones.'">
              {{ formatCOP(calcularTotal(data)) }}
            </span>
          </template>
        </Column>
        <Column header="Grupo" style="width:70px">
          <template #body="{ data }">
            <Tag v-if="data.grupoId" value="Grupal" severity="info" style="font-size:0.7rem" v-tooltip.top="'Cargue compartido'" />
          </template>
        </Column>
        <Column header="Acciones" style="width:180px">
          <template #body="{ data }">
            <Button icon="pi pi-eye" text rounded size="small" @click="$router.push('/cargues/' + data.id)" v-tooltip.top="'Ver detalle'" />
            <Button
              icon="pi pi-file-pdf"
              text rounded size="small" severity="danger"
              @click="verRemisionNuevaPestana(data)"
              v-tooltip.top="'Ver remisión en nueva pestaña'"
            />
            <Button
              v-if="data.estado === 'CREADO'"
              icon="pi pi-truck"
              text rounded size="small" severity="info"
              @click="marcarEnRuta(data)"
              v-tooltip.top="'Marcar en ruta'"
            />
            <Button
              icon="pi pi-copy"
              text rounded size="small" severity="secondary"
              @click="abrirDuplicar(data)"
              v-tooltip.top="'Duplicar para otros vendedores'"
            />
            <Button
              icon="pi pi-trash"
              text rounded size="small" severity="danger"
              @click="confirmarEliminar(data)"
              v-tooltip.top="'Eliminar cargue'"
            />
          </template>
        </Column>
        <template #empty>No hay cargues registrados</template>
      </DataTable>
      <p class="nota-total">
        * "Total venta" = (unidades cargadas − devueltas) × precio snapshot del cargue.
        El total real a facturar a la DIAN se basa en las remisiones registradas, que puede diferir.
        Verifica siempre en <strong>Facturación → Ver detalle</strong> antes de enviar.
      </p>
    </div>

    <!-- Dialog Nuevo Cargue -->
    <Dialog v-model:visible="dialogNuevo" header="Nuevo Cargue" modal :style="{width:'700px'}" :draggable="false" :maximizable="true">
      <div class="nuevo-cargue-form">
        <div class="form-row">
          <div class="field">
            <label>Vendedor *</label>
            <Select
              v-model="nuevoCargue.vendedorId"
              :options="vendedores"
              optionValue="id"
              :optionLabel="v => v.nombre + ' - CC ' + v.cedula"
              filter
              placeholder="Seleccionar vendedor"
              class="w-full"
            />
          </div>
          <div class="field">
            <label>Fecha de cargue</label>
            <Calendar v-model="nuevoCargue.fecha" dateFormat="dd/mm/yy" class="w-full" />
          </div>
        </div>

        <div class="detalles-section">
          <div class="detalles-header">
            <h4>Productos a cargar</h4>
            <Button label="Agregar producto" icon="pi pi-plus" size="small" outlined @click="agregarDetalle" />
          </div>

          <div v-for="(det, idx) in nuevoCargue.detalles" :key="idx" class="detalle-row">
            <Select
              v-model="det.productoId"
              :options="productos"
              optionValue="id"
              :optionLabel="p => p.nombreCompleto"
              filter
              placeholder="Producto"
              class="detalle-producto"
              @change="onProductoChange(det)"
            />
            <div class="detalle-qty">
              <label>Cajas</label>
              <InputNumber v-model="det.cantidadCajas" :min="0" @update:modelValue="calcDetalle(det)" style="width:90px" />
            </div>
            <div class="detalle-qty">
              <label>Unidades</label>
              <InputNumber v-model="det.cantidadUnidades" :min="0" @update:modelValue="calcDetalle(det)" style="width:90px" />
            </div>
            <div class="detalle-qty">
              <label>Total uds</label>
              <span class="detalle-total">{{ det.totalUnidades || 0 }}</span>
            </div>
            <Button icon="pi pi-times" text rounded severity="danger" @click="quitarDetalle(idx)" />
          </div>

          <div v-if="nuevoCargue.detalles.length === 0" class="no-detalles">
            Agregue al menos un producto al cargue
          </div>
        </div>

        <div class="resumen-cargue" v-if="nuevoCargue.detalles.length > 0">
          <span>Total unidades: <strong>{{ totalUnidadesCargue }}</strong></span>
          <span>Valor estimado: <strong>{{ formatCOP(valorEstimado) }}</strong></span>
        </div>

        <Message v-if="cargueError" severity="error" :closable="false">{{ cargueError }}</Message>

        <div class="dialog-footer">
          <Button label="Cancelar" text @click="dialogNuevo = false" />
          <Button label="Crear cargue" icon="pi pi-save" @click="crearCargue" :loading="creando" />
        </div>
      </div>
    </Dialog>
    <!-- Dialog Duplicar Cargue -->
    <Dialog v-model:visible="dialogDuplicar" header="Duplicar cargue para otros vendedores" modal :style="{width:'480px'}" :draggable="false">
      <div class="duplicar-form">
        <p class="dup-info">
          El cargue <strong>{{ cargueADuplicar?.numero }}</strong> se copiará con los mismos productos
          para cada vendedor seleccionado. Todos compartirán el mismo stock — si uno vende, los demás
          ven el disponible actualizado en tiempo real.
        </p>
        <div class="field">
          <label>Vendedores a asignar *</label>
          <MultiSelect
            v-model="dupVendedorIds"
            :options="vendedoresDisponibles"
            optionValue="id"
            :optionLabel="v => v.nombre + ' — CC ' + v.cedula"
            filter
            placeholder="Seleccionar vendedores"
            class="w-full"
            display="chip"
          />
        </div>
        <Message v-if="dupError" severity="error" :closable="false">{{ dupError }}</Message>
        <div class="dialog-footer">
          <Button label="Cancelar" text @click="dialogDuplicar = false" />
          <Button label="Crear cargues" icon="pi pi-copy" @click="confirmarDuplicar" :loading="duplicando" :disabled="!dupVendedorIds.length" />
        </div>
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import api from '@/api/axios'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import MultiSelect from 'primevue/multiselect'
import Calendar from 'primevue/calendar'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

const toast   = useToast()
const confirm = useConfirm()
const cargues = ref([])
const vendedores = ref([])
const productos = ref([])
const loading = ref(true)
const filtro = ref('')
const filtroEstado = ref(null)
const filtroDia = ref(null)
const dialogNuevo = ref(false)
const creando = ref(false)
const cargueError = ref('')

// Duplicar
const dialogDuplicar = ref(false)
const cargueADuplicar = ref(null)
const dupVendedorIds = ref([])
const duplicando = ref(false)
const dupError = ref('')

const vendedoresDisponibles = computed(() => {
  if (!cargueADuplicar.value) return vendedores.value
  return vendedores.value.filter(v => v.id !== cargueADuplicar.value.vendedor?.id)
})

const estados = ['CREADO', 'EN_RUTA', 'DEVUELTO', 'LIQUIDADO']

const nuevoCargueInicial = () => ({
  vendedorId: null,
  fecha: new Date(),
  detalles: []
})
const nuevoCargue = ref(nuevoCargueInicial())

const carguesFiltrados = computed(() => {
  return cargues.value.filter(c => {
    const matchFiltro = !filtro.value ||
      c.numero?.toLowerCase().includes(filtro.value.toLowerCase()) ||
      c.vendedor?.nombre?.toLowerCase().includes(filtro.value.toLowerCase())
    const matchEstado = !filtroEstado.value || c.estado === filtroEstado.value
    // Parsear la fecha del backend como local (no UTC) para evitar desfase de zona horaria
    const matchFecha = !filtroDia.value ||
      parseFechaLocal(c.fecha).toDateString() === filtroDia.value.toDateString()
    return matchFiltro && matchEstado && matchFecha
  })
})

const totalUnidadesCargue = computed(() =>
  nuevoCargue.value.detalles.reduce((s, d) => s + (d.totalUnidades || 0), 0)
)

const valorEstimado = computed(() => {
  return nuevoCargue.value.detalles.reduce((s, d) => {
    const prod = productos.value.find(p => p.id === d.productoId)
    if (!prod) return s
    const precioU = prod.precioCaja / prod.unidadesPorCaja
    return s + (d.totalUnidades || 0) * precioU
  }, 0)
})

function estadoLabel(e) {
  const map = { CREADO: 'Creado', EN_RUTA: 'En ruta', DEVUELTO: 'Devuelto', LIQUIDADO: 'Liquidado' }
  return map[e] || e
}

function getSeverity(e) {
  const map = { CREADO: 'secondary', EN_RUTA: 'info', DEVUELTO: 'warn', LIQUIDADO: 'success' }
  return map[e] || 'secondary'
}

// Formatea Date a 'YYYY-MM-DD' usando la hora LOCAL del navegador
// (toISOString usa UTC y puede devolver el día anterior/siguiente según la zona horaria)
function formatLocalDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

// Parsea 'YYYY-MM-DD' como fecha LOCAL (evita que new Date('2026-04-29') retorne el día anterior)
function parseFechaLocal(fechaStr) {
  if (!fechaStr) return new Date()
  const [y, m, d] = fechaStr.split('-')
  return new Date(+y, +m - 1, +d)
}

function formatFecha(f) {
  if (!f) return '-'
  return parseFechaLocal(f).toLocaleDateString('es-CO')
}

function formatCOP(v) {
  if (!v) return '$0'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(v)
}

function calcularTotal(cargue) {
  if (!cargue.detalles) return 0
  return cargue.detalles.reduce((s, d) => s + (d.subtotalVenta || 0), 0)
}

function abrirNuevoCargue() {
  nuevoCargue.value = nuevoCargueInicial()
  cargueError.value = ''
  dialogNuevo.value = true
}

function agregarDetalle() {
  nuevoCargue.value.detalles.push({
    productoId: null, cantidadCajas: 0, cantidadUnidades: 0,
    totalUnidades: 0, precioCajaSnapshot: 0
  })
}

function quitarDetalle(idx) {
  nuevoCargue.value.detalles.splice(idx, 1)
}

function onProductoChange(det) {
  const prod = productos.value.find(p => p.id === det.productoId)
  if (prod) {
    det.precioCajaSnapshot = prod.precioCaja
    calcDetalle(det)
  }
}

function calcDetalle(det) {
  const prod = productos.value.find(p => p.id === det.productoId)
  const upk = prod?.unidadesPorCaja || 1
  det.totalUnidades = (det.cantidadCajas || 0) * upk + (det.cantidadUnidades || 0)
}

function abrirDuplicar(cargue) {
  cargueADuplicar.value = cargue
  dupVendedorIds.value = []
  dupError.value = ''
  dialogDuplicar.value = true
}

async function confirmarDuplicar() {
  if (!dupVendedorIds.value.length) return
  dupError.value = ''
  duplicando.value = true
  try {
    const res = await api.post(`/cargues/${cargueADuplicar.value.id}/duplicar`, {
      vendedorIds: dupVendedorIds.value
    })
    toast.add({
      severity: 'success',
      summary: 'Cargues creados',
      detail: `Se crearon ${res.data.length} cargue(s) adicional(es) como grupo compartido`,
      life: 4000
    })
    dialogDuplicar.value = false
    cargarDatos()
  } catch (e) {
    dupError.value = e.response?.data?.mensaje || 'Error al duplicar'
  } finally {
    duplicando.value = false
  }
}

async function marcarEnRuta(cargue) {
  try {
    await api.put(`/cargues/${cargue.id}/en-ruta`)
    toast.add({ severity: 'info', summary: 'En ruta', detail: `Cargue ${cargue.numero} marcado en ruta`, life: 3000 })
    cargarDatos()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'No se pudo actualizar', life: 4000 })
  }
}

async function descargarRemision(cargue) {
  try {
    const res = await api.get(`/cargues/${cargue.id}/remision-pdf`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `remision_${cargue.numero}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo generar la remision', life: 4000 })
  }
}

async function verRemisionNuevaPestana(cargue) {
  try {
    const res = await api.get(`/cargues/${cargue.id}/remision-pdf/ver`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
    window.open(url, '_blank')
    // Liberar el objeto URL después de un momento para no acumular memoria
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo abrir la remisión', life: 4000 })
  }
}

function confirmarEliminar(cargue) {
  confirm.require({
    message: `¿Eliminar el cargue ${cargue.numero} y todas sus remisiones asociadas? Esta acción no se puede deshacer.`,
    header: 'Eliminar cargue',
    icon: 'pi pi-exclamation-triangle',
    acceptLabel: 'Sí, eliminar',
    rejectLabel: 'Cancelar',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await api.delete(`/cargues/${cargue.id}`)
        toast.add({ severity: 'success', summary: 'Eliminado', detail: `Cargue ${cargue.numero} eliminado`, life: 3000 })
        cargarDatos()
      } catch (e) {
        toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'No se pudo eliminar', life: 4000 })
      }
    }
  })
}

function confirmarLimpiarTodo() {
  confirm.require({
    message: `¿Eliminar TODOS los cargues y sus remisiones? Esta acción borrará todo el historial de cargues.`,
    header: 'Limpiar historial completo',
    icon: 'pi pi-exclamation-triangle',
    acceptLabel: 'Sí, limpiar todo',
    rejectLabel: 'Cancelar',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        const res = await api.delete('/cargues')
        toast.add({
          severity: 'success', summary: 'Historial limpiado',
          detail: `${res.data.carguesEliminados} cargues y ${res.data.remisionesEliminadas} remisiones eliminados`,
          life: 4000
        })
        cargarDatos()
      } catch (e) {
        toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'No se pudo limpiar', life: 4000 })
      }
    }
  })
}

async function crearCargue() {
  cargueError.value = ''
  if (!nuevoCargue.value.vendedorId) {
    cargueError.value = 'Seleccione un vendedor'
    return
  }
  if (nuevoCargue.value.detalles.length === 0) {
    cargueError.value = 'Agregue al menos un producto'
    return
  }
  const sinProducto = nuevoCargue.value.detalles.some(d => !d.productoId)
  if (sinProducto) {
    cargueError.value = 'Seleccione el producto en todos los renglones'
    return
  }

  creando.value = true
  try {
    // Usar hora LOCAL del navegador — toISOString() devuelve UTC y puede cambiar el día
    const fechaSeleccionada = nuevoCargue.value.fecha instanceof Date
      ? formatLocalDate(nuevoCargue.value.fecha)
      : nuevoCargue.value.fecha

    const payload = {
      vendedorId: nuevoCargue.value.vendedorId,
      fecha: fechaSeleccionada,
      detalles: nuevoCargue.value.detalles.map(d => ({
        productoId: d.productoId,
        cantidadCajas: d.cantidadCajas || 0,
        cantidadUnidades: d.cantidadUnidades || 0
      }))
    }
    await api.post('/cargues', payload)
    toast.add({ severity: 'success', summary: 'Cargue creado', detail: 'El cargue fue creado exitosamente', life: 3000 })
    dialogNuevo.value = false
    cargarDatos()
  } catch (e) {
    cargueError.value = e.response?.data?.mensaje || 'Error al crear el cargue'
  } finally {
    creando.value = false
  }
}

async function cargarDatos() {
  loading.value = true
  try {
    const [resCargues, resVend, resProd] = await Promise.all([
      api.get('/cargues'),
      api.get('/vendedores'),
      api.get('/productos')
    ])
    cargues.value = resCargues.data
    vendedores.value = resVend.data
    productos.value = resProd.data
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Error cargando datos', life: 4000 })
  } finally {
    loading.value = false
  }
}

onMounted(cargarDatos)
</script>

<style scoped>
.cargues-page { display: flex; flex-direction: column; gap: 1rem; }
.page-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  background: white; padding: 0.85rem 1.25rem; border-radius: 14px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm); gap: 1rem; flex-wrap: wrap;
}
.toolbar-left { display: flex; gap: 0.65rem; flex-wrap: wrap; align-items: center; }
.search-wrapper { position: relative; display: flex; align-items: center; }
.search-icon { position: absolute; left: 0.9rem; color: #9ca3af; font-size: 0.88rem; pointer-events: none; z-index: 1; }
:deep(.search-input) {
  padding-left: 2.4rem !important; padding-right: 1rem !important;
  height: 40px; width: 230px; border-radius: 99px !important;
  border-color: #e5e7eb !important; background: #f9fafb !important;
  font-size: 0.875rem !important;
  transition: width 0.25s ease, border-color 0.2s, box-shadow 0.2s, background 0.2s !important;
}
:deep(.search-input:focus) {
  border-color: var(--color-primary) !important; background: #fff !important;
  box-shadow: 0 0 0 3px rgba(211,47,47,0.1) !important; width: 280px !important; outline: none !important;
}
:deep(.search-input::placeholder) { color: #b0b8c4 !important; font-size: 0.85rem !important; }
.select-estado { width: 180px; }
.card-table { background: white; border-radius: 14px; padding: 1rem; border: 1px solid var(--color-border); box-shadow: var(--shadow-sm); }
.nota-total { font-size: 0.78rem; color: #999; margin: 0.5rem 0.25rem 0; line-height: 1.4; }

.nuevo-cargue-form { display: flex; flex-direction: column; gap: 1.2rem; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.field { display: flex; flex-direction: column; gap: 0.35rem; }
.field label { font-size: 0.85rem; font-weight: 600; color: #444; }

.detalles-section { border: 1px solid #e0e0e0; border-radius: 8px; padding: 1rem; }
.detalles-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.detalles-header h4 { margin: 0; color: #333; }

.detalle-row {
  display: flex; align-items: flex-end; gap: 0.75rem;
  padding: 0.5rem 0; border-bottom: 1px solid #f5f5f5;
}
.detalle-producto { flex: 1; min-width: 200px; }
.detalle-qty { display: flex; flex-direction: column; gap: 0.2rem; font-size: 0.78rem; color: #666; }
.detalle-total { font-size: 1rem; font-weight: 700; color: #D32F2F; padding: 0.4rem 0; }

.no-detalles { text-align: center; padding: 1.5rem; color: #aaa; font-size: 0.9rem; }

.resumen-cargue {
  display: flex; gap: 2rem; background: #f9f9f9;
  padding: 0.75rem 1rem; border-radius: 8px; font-size: 0.9rem;
}

.dialog-footer {
  display: flex; justify-content: flex-end; gap: 0.75rem;
  padding-top: 0.5rem; border-top: 1px solid #f0f0f0; margin-top: 0.5rem;
}

.duplicar-form { display: flex; flex-direction: column; gap: 1rem; }
.dup-info {
  font-size: 0.88rem; color: #555;
  background: #e3f2fd; border-radius: 8px;
  padding: 0.75rem 1rem; line-height: 1.5;
}
</style>
