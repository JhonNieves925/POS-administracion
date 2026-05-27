<template>
  <div class="detalle-cargue">
    <div class="page-back">
      <Button icon="pi pi-arrow-left" text @click="$router.push('/cargues')" label="Volver a cargues" />
    </div>

    <div v-if="loading" class="loading-center">
      <ProgressSpinner />
    </div>

    <template v-else-if="cargue">
      <!-- Header info -->
      <div class="cargue-header-card">
        <div class="cargue-info">
          <h2>{{ cargue.numero }}</h2>
          <Tag :value="estadoLabel(cargue.estado)" :severity="getSeverity(cargue.estado)" style="font-size:1rem" />
        </div>
        <div class="cargue-meta">
          <div class="meta-item">
            <i class="pi pi-user"></i>
            <span>{{ cargue.vendedor?.nombre }}</span>
          </div>
          <div class="meta-item">
            <i class="pi pi-calendar"></i>
            <span>{{ formatFecha(cargue.fecha) }}</span>
          </div>
          <div class="meta-item" v-if="cargue.creadoEn">
            <i class="pi pi-truck"></i>
            <span>Salida: {{ formatFecha(cargue.creadoEn) }}</span>
          </div>
        </div>
        <div class="cargue-acciones">
          <Button
            v-if="cargue.estado === 'CREADO'"
            label="Marcar en ruta"
            icon="pi pi-truck"
            severity="info"
            @click="marcarEnRuta"
            :loading="procesando"
          />
          <Button
            v-if="cargue.estado === 'EN_RUTA'"
            label="Registrar devoluciones"
            icon="pi pi-replay"
            severity="warn"
            @click="abrirDevoluciones"
          />
          <Button
            v-if="cargue.estado === 'DEVUELTO'"
            label="Editar devoluciones"
            icon="pi pi-pencil"
            severity="warn"
            outlined
            @click="abrirDevoluciones"
          />
          <Button
            v-if="cargue.estado === 'DEVUELTO'"
            label="Liquidar cargue"
            icon="pi pi-check-circle"
            severity="success"
            @click="liquidar"
            :loading="procesando"
          />
          <Button
            label="Remision PDF"
            icon="pi pi-file-pdf"
            outlined severity="danger"
            @click="descargarRemision"
          />
        </div>
      </div>

      <!-- Ventas del día (remisiones individuales) -->
      <div class="card-table" v-if="remisiones.length > 0">
        <div class="rem-header">
          <h3>Ventas del día</h3>
          <span class="rem-count">{{ remisiones.length }} remisión{{ remisiones.length !== 1 ? 'es' : '' }}</span>
        </div>
        <div class="remision-item" v-for="rem in remisiones" :key="rem.id">
          <div class="rem-top">
            <div class="rem-info">
              <span class="rem-numero">{{ rem.numero }}</span>
              <Tag
                :value="rem.estado === 'ANULADA' ? 'Anulada' : 'Activa'"
                :severity="rem.estado === 'ANULADA' ? 'danger' : 'success'"
                style="font-size:0.72rem"
              />
              <!-- Forma de pago -->
              <span :class="['fp-badge-dc', 'fp-' + (rem.formaPago || 'CONTADO').toLowerCase()]">
                {{ fpLabelDc(rem.formaPago) }}
                <template v-if="rem.formaPago === 'CREDITO' && rem.diasCredito"> · {{ rem.diasCredito }}d</template>
              </span>
            </div>
            <div class="rem-right">
              <span class="rem-cliente">{{ rem.cliente?.razonSocial || 'Consumidor Final' }}</span>
              <span class="rem-total" :class="{ 'rem-anulada': rem.estado === 'ANULADA' }">
                {{ formatCOP(rem.total) }}
              </span>
              <Button
                v-if="rem.estado !== 'ANULADA' && rem.estado !== 'FACTURADA'"
                icon="pi pi-pencil"
                text rounded size="small" severity="info"
                v-tooltip.top="'Editar venta'"
                @click="abrirEditarRem(rem)"
              />
            </div>
          </div>
          <div class="rem-detalles" v-if="rem.detalles?.length">
            <div class="rem-det-row" v-for="det in rem.detalles" :key="det.id">
              <span class="rem-prod">{{ det.descripcionProducto }}</span>
              <span class="rem-cant">
                <template v-if="(det.producto?.unidadesPorCaja || 1) > 1">
                  {{ Math.floor(det.cantidad / (det.producto?.unidadesPorCaja || 1)) }} caj
                  <span class="cajas-hint">({{ det.cantidad }} und)</span>
                </template>
                <template v-else>{{ det.cantidad }} und</template>
              </span>
              <span class="rem-sub">{{ formatCOP(det.subtotal) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Tabla detalles -->
      <div class="card-table">
        <h3>Detalle del cargue</h3>
        <DataTable :value="cargue.detalles" stripedRows size="small">
          <Column header="Producto">
            <template #body="{ data }">{{ data.producto?.nombreCompleto }}</template>
          </Column>
          <Column header="Cantidad cargue">
            <template #body="{ data }">
              <template v-if="upcDet(data) > 1">
                <strong>{{ Math.floor(data.cantidadCargue / upcDet(data)) }} cajas</strong>
                <span class="cajas-hint">({{ data.cantidadCargue }} und)</span>
              </template>
              <template v-else>{{ data.cantidadCargue }}</template>
            </template>
          </Column>
          <Column header="Devolucion">
            <template #body="{ data }">
              <span :class="{ 'dev-positive': data.cantidadDevolucion > 0 }">
                <template v-if="upcDet(data) > 1 && data.cantidadDevolucion > 0">
                  <strong>{{ Math.floor(data.cantidadDevolucion / upcDet(data)) }} cajas</strong>
                  <span class="cajas-hint">({{ data.cantidadDevolucion }} und)</span>
                </template>
                <template v-else>{{ data.cantidadDevolucion || 0 }}</template>
              </span>
            </template>
          </Column>
          <Column header="Vendido">
            <template #body="{ data }">
              <template v-if="upcDet(data) > 1">
                <strong>{{ Math.floor((data.cantidadVenta ?? (data.cantidadCargue - (data.cantidadDevolucion || 0))) / upcDet(data)) }} cajas</strong>
                <span class="cajas-hint">({{ data.cantidadVenta ?? (data.cantidadCargue - (data.cantidadDevolucion || 0)) }} und)</span>
              </template>
              <template v-else>
                <strong>{{ data.cantidadVenta ?? (data.cantidadCargue - (data.cantidadDevolucion || 0)) }}</strong>
              </template>
            </template>
          </Column>
          <Column header="Precio caja snap.">
            <template #body="{ data }">{{ formatCOP(data.precioCajaSnapshot) }}</template>
          </Column>
          <Column header="Subtotal venta">
            <template #body="{ data }">{{ formatCOP(data.subtotalVenta) }}</template>
          </Column>
        </DataTable>

        <div class="totales-row">
          <div class="total-item">
            <span>Total cargado:</span>
            <strong>{{ totalCargadoStr }}</strong>
          </div>
          <div class="total-item">
            <span>Total devuelto:</span>
            <strong>{{ totalDevueltoStr }}</strong>
          </div>
          <div class="total-item">
            <span>Total vendido:</span>
            <strong>{{ totalVendidoStr }}</strong>
          </div>
          <div class="total-item total-valor">
            <span>Valor total venta:</span>
            <strong>{{ formatCOP(totalValor) }}</strong>
          </div>
        </div>
      </div>
    </template>

    <!-- Dialog devoluciones -->
    <Dialog v-model:visible="dialogDevolucion" header="Registrar Devoluciones" modal :style="{width:'600px'}" :draggable="false">
      <div class="devolucion-form">
        <p class="dev-info">Ingrese las cantidades devueltas por el vendedor.</p>
        <DataTable :value="devolucionItems" size="small">
          <Column header="Producto">
            <template #body="{ data }">{{ data.producto?.nombreCompleto }}</template>
          </Column>
          <Column header="Cargado">
            <template #body="{ data }">{{ data.cantidadCargue }}</template>
          </Column>
          <Column header="Devuelve">
            <template #body="{ data }">
              <InputNumber
                v-model="data.devolucion"
                :min="0"
                :max="data.cantidadCargue"
                style="width:90px"
              />
            </template>
          </Column>
          <Column header="Vende">
            <template #body="{ data }">
              <strong>{{ data.cantidadCargue - (data.devolucion || 0) }}</strong>
            </template>
          </Column>
        </DataTable>
        <Message v-if="devError" severity="error" :closable="false">{{ devError }}</Message>
        <div class="dialog-footer">
          <Button label="Cancelar" text @click="dialogDevolucion = false" />
          <Button label="Registrar devoluciones" icon="pi pi-check" @click="registrarDevoluciones" :loading="procesando" />
        </div>
      </div>
    </Dialog>

    <!-- Dialog editar venta individual -->
    <Dialog
      v-model:visible="dialogEditarRem"
      :header="`Editar venta ${editandoRem?.numero || ''}`"
      modal
      :style="{ width: '680px', maxWidth: '98vw' }"
      :draggable="false"
    >
      <div class="editar-rem-form" v-if="editandoRem">

        <!-- Selector de cliente -->
        <div class="field">
          <label class="field-label-dc"><i class="pi pi-user" style="margin-right:0.3rem"/>Cliente *</label>
          <Select
            v-model="editClienteIdRem"
            :options="clientesLista"
            optionValue="id"
            :optionLabel="c => c.razonSocial + ' · ' + c.nit"
            filter
            filterPlaceholder="Buscar cliente..."
            placeholder="Seleccionar cliente"
            class="w-full"
          />
        </div>

        <div class="editar-aviso">
          <i class="pi pi-info-circle" />
          <span>Marca los productos que quieres incluir. Desmarca para quitarlos. Puedes agregar productos que no estaban.</span>
        </div>

        <!-- Resumen seleccionados -->
        <div class="sel-resumen-dc">
          <i class="pi pi-check-square" style="color:#2E7D32;margin-right:0.3rem"/>
          <span>{{ editLineasRem.filter(r=>r.seleccionado).length }} de {{ editLineasRem.length }} productos seleccionados</span>
        </div>

        <table class="editar-tabla">
          <thead>
            <tr>
              <th class="col-chk-dc">✓</th>
              <th>Producto</th>
              <th class="col-num-ed">Cajas</th>
              <th class="col-num-ed">Uds</th>
              <th class="col-num-ed">Precio/caja</th>
              <th class="col-num-ed">Subtotal</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in editLineasRem" :key="i"
                :class="{ 'row-activa-dc': row.seleccionado, 'row-vacia-dc': !row.seleccionado }">
              <td class="col-chk-dc">
                <input type="checkbox" class="chk-prod-dc" v-model="row.seleccionado" @change="toggleProdDc(row)" />
              </td>
              <td class="edit-prod-name-c">{{ row.descripcion }}</td>
              <td class="col-num-ed">
                <InputNumber v-model="row.cajas" :min="0" :disabled="!row.seleccionado"
                  inputStyle="width:55px;text-align:center" @update:modelValue="calcLinea(row)" />
              </td>
              <td class="col-num-ed">
                <InputNumber v-model="row.unidades" :min="0" :disabled="!row.seleccionado"
                  inputStyle="width:55px;text-align:center" @update:modelValue="calcLinea(row)" />
              </td>
              <td class="col-num-ed">
                <InputNumber v-model="row.precioCaja" mode="currency" currency="COP" locale="es-CO"
                  :maxFractionDigits="0" :disabled="!row.seleccionado" inputStyle="width:100px"
                  @update:modelValue="calcLinea(row)" />
              </td>
              <td class="col-num-ed edit-sub-c">{{ row.seleccionado ? formatCOP(row.subtotal) : '—' }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="5" class="tfoot-label-c"><strong>Total estimado</strong></td>
              <td class="col-num-ed edit-sub-c"><strong>{{ formatCOP(editTotalRem) }}</strong></td>
            </tr>
          </tfoot>
        </table>

        <!-- Forma de pago -->
        <div class="field" style="margin-top:1rem">
          <label style="font-size:0.82rem;font-weight:600;color:#555;display:block;margin-bottom:0.4rem">
            <i class="pi pi-wallet" style="margin-right:0.3rem" /> Forma de pago
          </label>
          <div class="fp-toggle-dc">
            <button
              v-for="fp in [{value:'CONTADO',label:'Contado'},{value:'CREDITO',label:'Crédito'},{value:'TRANSFERENCIA',label:'Transferencia'}]"
              :key="fp.value"
              class="fp-btn-dc"
              :class="{ active: editFpRem === fp.value }"
              @click="editFpRem = fp.value; if (fp.value !== 'CREDITO') editDcRem = 30"
            >{{ fp.label }}</button>
          </div>
          <div v-if="editFpRem === 'CREDITO'" class="credito-row-dc">
            <span style="font-size:0.82rem;color:#1565C0">Días:</span>
            <div style="display:flex;gap:0.3rem">
              <button
                v-for="d in [15,30,45,60,90]" :key="d"
                class="dia-btn-dc" :class="{ active: editDcRem === d }"
                @click="editDcRem = d"
              >{{ d }}</button>
            </div>
          </div>
        </div>

        <div class="field" style="margin-top:0.75rem">
          <label>Observaciones</label>
          <Textarea v-model="editObsRem" rows="2" class="w-full" placeholder="Opcional..." />
        </div>

        <Message v-if="editRemError" severity="error" :closable="false">{{ editRemError }}</Message>

        <div class="dialog-footer">
          <Button label="Cancelar" text @click="dialogEditarRem = false" />
          <Button label="Guardar cambios" icon="pi pi-save" @click="guardarEdicionRem" :loading="guardandoRem" />
        </div>
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import api from '@/api/axios'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import ProgressSpinner from 'primevue/progressspinner'

const route = useRoute()
const toast = useToast()
const cargue = ref(null)
const remisiones = ref([])
const loading = ref(true)
const procesando = ref(false)
const dialogDevolucion = ref(false)
const devolucionItems = ref([])
const devError = ref('')

const totalCargado = computed(() =>
  cargue.value?.detalles?.reduce((s, d) => s + d.cantidadCargue, 0) || 0
)
const totalDevuelto = computed(() =>
  cargue.value?.detalles?.reduce((s, d) => s + (d.cantidadDevolucion || 0), 0) || 0
)
const totalVendido = computed(() => totalCargado.value - totalDevuelto.value)

const totalCargadoStr = computed(() => {
  const detalles = cargue.value?.detalles || []
  let totalCajas = 0, totalSueltas = 0
  for (const d of detalles) {
    const upk = d.producto?.unidadesPorCaja || 1
    totalCajas += Math.floor(d.cantidadCargue / upk)
    totalSueltas += d.cantidadCargue % upk
  }
  // Normalizar sueltas en cajas si hay un único producto (raro) — simplemente mostrar suma
  if (totalSueltas > 0) return `${totalCajas} caj (${totalCargado.value} und)`
  return `${totalCajas} cajas`
})

const totalDevueltoStr = computed(() => {
  const detalles = cargue.value?.detalles || []
  let totalCajas = 0, totalSueltas = 0
  for (const d of detalles) {
    const upk = d.producto?.unidadesPorCaja || 1
    const dev = d.cantidadDevolucion || 0
    totalCajas += Math.floor(dev / upk)
    totalSueltas += dev % upk
  }
  if (totalSueltas > 0) return `${totalCajas} caj (${totalDevuelto.value} und)`
  return `${totalCajas} cajas`
})

const totalVendidoStr = computed(() => {
  const detalles = cargue.value?.detalles || []
  let totalCajas = 0, totalSueltas = 0
  for (const d of detalles) {
    const upk = d.producto?.unidadesPorCaja || 1
    const vendido = d.cantidadVenta ?? (d.cantidadCargue - (d.cantidadDevolucion || 0))
    totalCajas += Math.floor(vendido / upk)
    totalSueltas += vendido % upk
  }
  if (totalSueltas > 0) return `${totalCajas} caj (${totalVendido.value} und)`
  return `${totalCajas} cajas`
})
const totalValor = computed(() =>
  cargue.value?.detalles?.reduce((s, d) => s + (d.subtotalVenta || 0), 0) || 0
)

function upcDet(det) {
  return det.producto?.unidadesPorCaja || 1
}

function estadoLabel(e) {
  const map = { CREADO: 'Creado', EN_RUTA: 'En ruta', DEVUELTO: 'Devuelto', LIQUIDADO: 'Liquidado' }
  return map[e] || e
}
function getSeverity(e) {
  const map = { CREADO: 'secondary', EN_RUTA: 'info', DEVUELTO: 'warn', LIQUIDADO: 'success' }
  return map[e] || 'secondary'
}
function formatFecha(f) {
  if (!f) return '-'
  return new Date(f).toLocaleString('es-CO')
}
function fpLabelDc(fp) {
  const m = { CONTADO: 'Contado', CREDITO: 'Crédito', TRANSFERENCIA: 'Transf.', CHEQUE: 'Cheque' }
  return m[fp] || 'Contado'
}

function formatCOP(v) {
  if (!v && v !== 0) return '-'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(v)
}

async function cargarCargue() {
  loading.value = true
  try {
    const [resCargue, resRem] = await Promise.allSettled([
      api.get(`/cargues/${route.params.id}`),
      api.get(`/remisiones/cargue/${route.params.id}`)
    ])
    if (resCargue.status === 'fulfilled') cargue.value = resCargue.value.data
    if (resRem.status === 'fulfilled') remisiones.value = resRem.value.data || []
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo cargar el cargue', life: 4000 })
  } finally {
    loading.value = false
  }
}

async function marcarEnRuta() {
  procesando.value = true
  try {
    await api.put(`/cargues/${cargue.value.id}/en-ruta`)
    toast.add({ severity: 'info', summary: 'Actualizado', detail: 'Cargue marcado en ruta', life: 3000 })
    cargarCargue()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'Error', life: 4000 })
  } finally {
    procesando.value = false
  }
}

function abrirDevoluciones() {
  devolucionItems.value = cargue.value.detalles.map(d => ({
    ...d,
    devolucion: d.cantidadDevolucion || 0
  }))
  devError.value = ''
  dialogDevolucion.value = true
}

async function registrarDevoluciones() {
  devError.value = ''
  procesando.value = true
  try {
    const payload = devolucionItems.value.map(d => ({
      detalleId: d.id,
      cantidadDevolucion: d.devolucion || 0
    }))
    await api.put(`/cargues/${cargue.value.id}/devoluciones`, payload)
    toast.add({ severity: 'success', summary: 'Devoluciones registradas', detail: 'Se registraron las devoluciones', life: 3000 })
    dialogDevolucion.value = false
    cargarCargue()
  } catch (e) {
    devError.value = e.response?.data?.mensaje || 'Error al registrar'
  } finally {
    procesando.value = false
  }
}

async function liquidar() {
  procesando.value = true
  try {
    await api.put(`/cargues/${cargue.value.id}/liquidar`)
    toast.add({ severity: 'success', summary: 'Liquidado', detail: 'Cargue liquidado correctamente', life: 3000 })
    cargarCargue()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'Error al liquidar', life: 4000 })
  } finally {
    procesando.value = false
  }
}

async function descargarRemision() {
  try {
    const res = await api.get(`/cargues/${cargue.value.id}/remision-pdf`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `remision_${cargue.value.numero}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo generar el PDF', life: 4000 })
  }
}

// ── Editar venta individual ────────────────────────────────
const dialogEditarRem     = ref(false)
const editandoRem         = ref(null)
const editLineasRem       = ref([])
const editObsRem          = ref('')
const editClienteIdRem    = ref(null)
const editFpRem           = ref('CONTADO')
const editDcRem           = ref(30)
const editRemError        = ref('')
const guardandoRem        = ref(false)
const clientesLista       = ref([])

const editTotalRem = computed(() =>
  editLineasRem.value.reduce((s, r) => s + (r.subtotal || 0), 0)
)

function calcLinea(row) {
  const upk = row.upk || 1
  const uds = (row.cajas || 0) * upk + (row.unidades || 0)
  row.subtotal = uds * ((row.precioCaja || 0) / upk)
  // Auto-seleccionar si el usuario tecleó una cantidad
  if (uds > 0) row.seleccionado = true
}

function toggleProdDc(row) {
  if (!row.seleccionado) {
    row.cajas = 0
    row.unidades = 0
    row.subtotal = 0
  } else if (row.cajas === 0 && row.unidades === 0) {
    row.cajas = 1
    calcLinea(row)
  }
}

async function abrirEditarRem(rem) {
  editandoRem.value = rem
  editObsRem.value = rem.observaciones || ''
  editClienteIdRem.value = rem.cliente?.id || null
  editFpRem.value = rem.formaPago || 'CONTADO'
  editDcRem.value = rem.diasCredito || 30
  editRemError.value = ''
  editLineasRem.value = []
  // Cargar clientes si no están cargados aún
  if (clientesLista.value.length === 0) {
    try {
      const res = await api.get('/clientes')
      clientesLista.value = Array.isArray(res.data) ? res.data : []
    } catch { clientesLista.value = [] }
  }
  try {
    const cargueId = cargue.value?.id
    const cargueDetalles = cargue.value?.detalles || []

    // Mapa de lo que ya tiene la remision
    const remMap = {}
    for (const det of (rem.detalles || [])) {
      const upk = det.producto?.unidadesPorCaja || 1
      const qty = Number(det.cantidad || 0)
      remMap[det.descripcionProducto] = {
        cajas: Math.floor(qty / upk),
        unidades: qty % upk,
        precioCaja: Number(det.precioUnitario || 0) * upk
      }
    }

    // Construir líneas desde todos los productos del cargue
    editLineasRem.value = cargueDetalles.map(cd => {
      const upk = cd.producto?.unidadesPorCaja || 1
      const nombre = cd.producto?.nombreCompleto || ''
      const actual = remMap[nombre] || { cajas: 0, unidades: 0, precioCaja: cd.producto?.precioCaja || 0 }
      const row = {
        cargueDetalleId: cd.id,
        descripcion: nombre,
        upk,
        cajas: actual.cajas,
        unidades: actual.unidades,
        precioCaja: actual.precioCaja || (cd.producto?.precioCaja || 0),
        seleccionado: actual.cajas > 0 || actual.unidades > 0,
        subtotal: 0
      }
      calcLinea(row)
      return row
    })
    dialogEditarRem.value = true
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo preparar la edición', life: 4000 })
  }
}

async function guardarEdicionRem() {
  editRemError.value = ''
  const hayProductos = editLineasRem.value.some(r => (r.cajas || 0) > 0 || (r.unidades || 0) > 0)
  if (!hayProductos) {
    editRemError.value = 'Debe ingresar al menos un producto con cantidad mayor a 0'
    return
  }
  guardandoRem.value = true
  try {
    const rem = editandoRem.value
    const detalles = editLineasRem.value
      .filter(r => (r.cajas || 0) > 0 || (r.unidades || 0) > 0)
      .map(r => ({
        cargueDetalleId: r.cargueDetalleId,
        cantidadCajas: r.cajas || 0,
        cantidadUnidades: r.unidades || 0,
        precioVentaUnidad: (r.precioCaja || 0) / (r.upk || 1)
      }))

    const payloadRem = {
      clienteId: editClienteIdRem.value,
      observaciones: editObsRem.value,
      formaPago: editFpRem.value,
      detalles
    }
    if (editFpRem.value === 'CREDITO') payloadRem.diasCredito = editDcRem.value || 30
    await api.put(`/remisiones/${rem.id}/actualizar`, payloadRem)
    toast.add({ severity: 'success', summary: 'Venta actualizada', detail: `${rem.numero} modificada correctamente`, life: 3000 })
    dialogEditarRem.value = false
    cargarCargue() // Recargar todo
  } catch (e) {
    editRemError.value = e.response?.data?.mensaje || 'Error al actualizar la venta'
  } finally {
    guardandoRem.value = false
  }
}

onMounted(cargarCargue)
</script>

<style scoped>
.detalle-cargue { display: flex; flex-direction: column; gap: 1rem; }
.page-back { display: flex; }

.cargue-header-card {
  background: white; border-radius: 12px; padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  display: flex; flex-direction: column; gap: 1rem;
}
.cargue-info { display: flex; align-items: center; gap: 1rem; }
.cargue-info h2 { margin: 0; color: #333; font-size: 1.5rem; }
.cargue-meta { display: flex; gap: 1.5rem; flex-wrap: wrap; }
.meta-item { display: flex; align-items: center; gap: 0.4rem; font-size: 0.9rem; color: #666; }
.cargue-acciones { display: flex; gap: 0.75rem; flex-wrap: wrap; }

.card-table { background: white; border-radius: 12px; padding: 1.5rem; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.card-table h3 { margin: 0 0 1rem; color: #333; }

.dev-positive { color: #D32F2F; font-weight: 600; }
.cajas-hint { font-size: 0.75rem; color: #999; font-weight: 400; margin-left: 0.25rem; }

.totales-row {
  display: flex; gap: 2rem; flex-wrap: wrap;
  background: #f9f9f9; padding: 1rem; border-radius: 8px;
  margin-top: 1rem;
}
.total-item { display: flex; flex-direction: column; gap: 0.2rem; font-size: 0.85rem; color: #666; }
.total-item strong { font-size: 1.1rem; color: #333; }
.total-valor { border-left: 3px solid #D32F2F; padding-left: 1rem; }
.total-valor strong { color: #D32F2F; font-size: 1.3rem; }

.loading-center { display: flex; justify-content: center; padding: 4rem; }

.devolucion-form { display: flex; flex-direction: column; gap: 1rem; }
.dev-info { color: #555; font-size: 0.9rem; background: #fff8e1; padding: 0.5rem 0.75rem; border-radius: 6px; }

.dialog-footer {
  display: flex; justify-content: flex-end; gap: 0.75rem;
  padding-top: 0.5rem; border-top: 1px solid #f0f0f0; margin-top: 0.5rem;
}

/* ── Ventas del día ── */
.rem-header {
  display: flex; align-items: center; gap: 1rem; margin-bottom: 1rem;
}
.rem-header h3 { margin: 0; color: #333; }
.rem-count {
  font-size: 0.78rem; background: #E3F2FD; color: #1565C0;
  padding: 2px 10px; border-radius: 20px; font-weight: 600;
}

.remision-item {
  border: 1px solid #f0f0f0; border-radius: 8px; padding: 0.75rem;
  margin-bottom: 0.6rem;
}
.remision-item:last-child { margin-bottom: 0; }

.rem-top {
  display: flex; justify-content: space-between; align-items: flex-start;
  margin-bottom: 0.5rem;
}
.rem-info { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
.rem-numero { font-weight: 700; font-size: 0.88rem; color: #333; }

/* Forma de pago badge en DetalleCargue */
.fp-badge-dc {
  display: inline-flex; align-items: center; gap: 0.2rem;
  padding: 1px 7px; border-radius: 10px;
  font-size: 0.7rem; font-weight: 700;
}
.fp-badge-dc.fp-contado       { background: #E8F5E9; color: #2E7D32; }
.fp-badge-dc.fp-credito       { background: #E3F2FD; color: #1565C0; }
.fp-badge-dc.fp-transferencia { background: #F3E5F5; color: #6A1B9A; }
.fp-badge-dc.fp-cheque        { background: #FFF3E0; color: #E65100; }
.rem-right { display: flex; flex-direction: column; align-items: flex-end; gap: 0.15rem; }
.rem-cliente { font-size: 0.8rem; color: #666; }
.rem-total { font-size: 1rem; font-weight: 700; color: #2E7D32; }
.rem-total.rem-anulada { color: #999; text-decoration: line-through; }

.rem-detalles { border-top: 1px dashed #f0f0f0; padding-top: 0.4rem; display: flex; flex-direction: column; gap: 0.2rem; }
.rem-det-row { display: flex; align-items: center; gap: 0.5rem; font-size: 0.82rem; }
.rem-prod { flex: 1; color: #555; }
.rem-cant { color: #1565C0; font-weight: 600; white-space: nowrap; }
.rem-sub { margin-left: auto; color: #D32F2F; font-weight: 600; white-space: nowrap; }

/* ── Dialog editar remision ─────────────────────────────── */
.editar-rem-form { display: flex; flex-direction: column; gap: 1rem; }
.field-label-dc { font-size: 0.82rem; font-weight: 600; color: #555; display: block; margin-bottom: 0.4rem; }
/* Filas seleccionadas / deseleccionadas */
.row-activa-dc td { background: #f0faf0 !important; }
.row-activa-dc .edit-prod-name-c { color: #1B5E20 !important; font-weight: 700 !important; }
.row-vacia-dc  td { background: #fafafa !important; opacity: 0.55; }
.row-vacia-dc  .edit-prod-name-c { color: #999 !important; }
/* Checkbox columna */
.col-chk-dc { width: 36px; text-align: center !important; }
.chk-prod-dc { width: 17px; height: 17px; cursor: pointer; accent-color: #2E7D32; }
/* Resumen seleccionados */
.sel-resumen-dc {
  display: flex; align-items: center;
  background: #f5f5f5; border-radius: 8px; padding: 0.45rem 0.9rem;
  font-size: 0.82rem; color: #444; font-weight: 600;
}
.editar-aviso {
  display: flex; align-items: flex-start; gap: 0.5rem;
  background: #E3F2FD; color: #1565C0; padding: 0.6rem 0.8rem;
  border-radius: 8px; font-size: 0.85rem;
}
.editar-tabla {
  width: 100%; border-collapse: collapse; font-size: 0.84rem;
}
.editar-tabla th {
  background: #f5f5f5; padding: 0.45rem 0.5rem; text-align: left;
  font-weight: 700; color: #555; border-bottom: 2px solid #e0e0e0;
}
.editar-tabla td { padding: 0.35rem 0.5rem; border-bottom: 1px solid #f0f0f0; vertical-align: middle; }
.editar-tabla tfoot td { border-top: 2px solid #ddd; border-bottom: none; }
.edit-prod-name-c { font-weight: 500; color: #333; }
.col-num-ed { text-align: center; white-space: nowrap; }
.edit-sub-c { color: #D32F2F; font-weight: 700; text-align: right; }
.tfoot-label-c { text-align: right; color: #555; }
.field label { font-size: 0.82rem; font-weight: 600; color: #555; }

/* Forma de pago en edit dialog (DetalleCargue) */
.fp-toggle-dc { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.fp-btn-dc {
  padding: 0.3rem 0.8rem; border: 1.5px solid #e0e0e0; border-radius: 16px;
  background: white; font-size: 0.78rem; font-weight: 600; color: #888;
  cursor: pointer; transition: all 0.2s;
}
.fp-btn-dc.active { border-color: #1565C0; background: #E3F2FD; color: #1565C0; }
.credito-row-dc { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; margin-top: 0.4rem; }
.dia-btn-dc {
  padding: 0.15rem 0.55rem; border: 1.5px solid #90CAF9; border-radius: 12px;
  background: white; font-size: 0.75rem; font-weight: 700; color: #1565C0; cursor: pointer;
  transition: all 0.15s;
}
.dia-btn-dc.active { background: #1565C0; color: white; border-color: #1565C0; }
</style>
