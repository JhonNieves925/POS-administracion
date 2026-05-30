<template>
  <div class="nueva-venta">
    <div class="info-card">
      <h3>Registrar Venta</h3>
      <p>Genera una remision para un cliente de tu ruta.</p>
    </div>

    <!-- Toggle tipo de venta -->
    <div class="tipo-venta-toggle">
      <button
        class="tipo-btn"
        :class="{ active: !ventaRapida }"
        @click="ventaRapida = false"
      >
        <i class="pi pi-building" />
        Cliente registrado
      </button>
      <button
        class="tipo-btn rapida"
        :class="{ active: ventaRapida }"
        @click="ventaRapida = true"
      >
        <i class="pi pi-bolt" />
        Venta rápida
      </button>
    </div>

    <!-- MODO: Cliente registrado -->
    <div class="section-card" v-if="!ventaRapida">
      <label class="sec-label">Cliente</label>
      <Select
        v-model="form.clienteId"
        :options="clientes"
        optionValue="id"
        :optionLabel="c => c.razonSocial"
        filter
        filterPlaceholder="Buscar por nombre o NIT..."
        placeholder="Seleccionar cliente..."
        class="w-full"
        @change="onClienteChange"
      />
      <div v-if="clienteSeleccionado" class="cliente-info">
        <i class="pi pi-map-marker" />
        <span>{{ clienteSeleccionado.municipio }} {{ clienteSeleccionado.municipio && clienteSeleccionado.telefono ? '·' : '' }} {{ clienteSeleccionado.telefono }}</span>
      </div>
    </div>

    <!-- MODO: Venta rápida (Consumidor Final) -->
    <div class="section-card venta-rapida-card" v-else>
      <div v-if="consumidorFinal" class="cf-info">
        <i class="pi pi-bolt" style="color:#F57F17; font-size:1.4rem" />
        <div>
          <div class="cf-titulo">Consumidor Final</div>
          <div class="cf-nit">NIT {{ consumidorFinal.nit }} · La remisión queda a nombre del sistema</div>
        </div>
      </div>
      <div v-else class="cf-alerta">
        <i class="pi pi-exclamation-triangle" style="color:#D32F2F" />
        <span>
          No existe el cliente <strong>CONSUMIDOR FINAL</strong> (NIT 222222222).
          Créalo en el panel de administración para usar ventas rápidas.
        </span>
      </div>

      <div class="field" v-if="consumidorFinal">
        <label>Referencia del comprador <span style="color:#aaa">(opcional)</span></label>
        <InputText
          v-model="form.referenciaComprador"
          class="w-full"
          placeholder="Ej: Tienda Doña Rosa, Sr. Martínez..."
        />
        <small class="hint">Aparece en las observaciones de la remisión como referencia interna.</small>
      </div>
    </div>

    <!-- Productos del cargue -->
    <div class="section-card" v-if="cargue">
      <label class="sec-label">Productos disponibles</label>

      <div
        class="producto-venta-item"
        v-for="det in cargue.detalles"
        :key="det.id"
        :class="{ 'item-agotado': disponibleReal(det) === 0 }"
      >
        <div class="pv-header">
          <span class="pv-nombre">{{ det.producto?.nombreCompleto }}</span>
          <span
            class="pv-disponible"
            :class="disponibleReal(det) <= (det.producto?.unidadesPorCaja || 6) ? 'disp-bajo' : ''"
          >
            Disp: <strong>{{ disponibleReal(det) }}</strong> uds
            ({{ Math.floor(disponibleReal(det) / (det.producto?.unidadesPorCaja || 1)) }} cajas)
          </span>
        </div>

        <div v-if="disponibleReal(det) === 0" class="agotado-msg">
          <i class="pi pi-ban" /> Producto agotado en este cargue
        </div>

        <div class="pv-controls" v-else>
          <div class="pv-control">
            <span class="pv-label">Cajas (máx {{ Math.floor(disponibleReal(det) / (det.producto?.unidadesPorCaja || 1)) }})</span>
            <InputNumber
              v-model="getVenta(det.id).cajas"
              :min="0"
              :max="Math.floor(disponibleReal(det) / (det.producto?.unidadesPorCaja || 1))"
              showButtons
              buttonLayout="horizontal"
              inputStyle="width:48px;text-align:center"
              @update:modelValue="calcVenta(det)"
            />
          </div>
          <div class="pv-control">
            <span class="pv-label">Uds sueltas (máx {{ disponibleReal(det) % (det.producto?.unidadesPorCaja || 1) + (det.producto?.unidadesPorCaja || 1) - 1 }})</span>
            <InputNumber
              v-model="getVenta(det.id).unidades"
              :min="0"
              :max="disponibleReal(det)"
              showButtons
              buttonLayout="horizontal"
              inputStyle="width:48px;text-align:center"
              @update:modelValue="calcVenta(det)"
            />
          </div>
          <div class="pv-subtotal">
            {{ formatCOP(getVenta(det.id).subtotal) }}
          </div>
        </div>

        <!-- Precio de venta negociado -->
        <div class="pv-precio-row" v-if="disponibleReal(det) > 0">

          <!-- Toggle unidad / caja -->
          <div class="modo-precio-toggle">
            <button
              class="modo-btn"
              :class="{ active: getVenta(det.id).modoPrecio === 'unidad' }"
              @click="setModoPrecio(det, 'unidad')"
            >$ / Unidad</button>
            <button
              class="modo-btn"
              :class="{ active: getVenta(det.id).modoPrecio === 'caja' }"
              @click="setModoPrecio(det, 'caja')"
            >$ / Caja</button>
          </div>

          <!-- Chips de referencia -->
          <div class="pv-precio-info">
            <span class="precio-chip sugerido">
              Sugerido:
              {{ getVenta(det.id).modoPrecio === 'caja'
                  ? formatCOP(precioSugeridoCaja(det))  + '/caja'
                  : formatCOP(precioSugeridoUnidad(det)) + '/u' }}
            </span>
          </div>

          <!-- Input de precio -->
          <div class="pv-precio-input">
            <span class="pv-label">
              Precio negociado /
              {{ getVenta(det.id).modoPrecio === 'caja' ? 'caja' : 'unidad' }}
            </span>
            <InputNumber
              v-model="getVenta(det.id).precioVenta"
              :min="getVenta(det.id).modoPrecio === 'caja'
                      ? (precioMinimoCaja(det) || 0)
                      : (precioMinimoUnidad(det) || 0)"
              mode="currency"
              currency="COP"
              locale="es-CO"
              :maxFractionDigits="0"
              :placeholder="String(
                getVenta(det.id).modoPrecio === 'caja'
                  ? precioSugeridoCaja(det)
                  : precioSugeridoUnidad(det)
              )"
              inputStyle="width:130px"
              :class="{'precio-bajo': precioVentaEsBajo(det)}"
              @update:modelValue="calcVenta(det)"
            />
            <small v-if="precioVentaEsBajo(det)" class="precio-error">
              No puede ser menor al precio mínimo
            </small>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="!cargueLoading" class="sin-cargue">
      <i class="pi pi-inbox" style="font-size:2rem;color:#ccc" />
      <p>No tienes cargue activo hoy</p>
    </div>

    <!-- Total y observaciones -->
    <div class="section-card" v-if="totalVenta > 0">
      <div class="total-row">
        <span>Total remisión:</span>
        <strong class="total-val">{{ formatCOP(totalVenta) }}</strong>
      </div>

      <!-- Forma de pago -->
      <div class="field" style="margin-top:1rem">
        <label class="sec-label" style="margin-bottom:0.4rem;display:block">
          <i class="pi pi-wallet" style="margin-right:0.3rem" />
          Forma de pago
        </label>
        <div class="fp-toggle">
          <button
            v-for="fp in formasPago"
            :key="fp.value"
            class="fp-btn"
            :class="{ active: form.formaPago === fp.value }"
            @click="form.formaPago = fp.value; if (fp.value !== 'CREDITO') form.diasCredito = null"
          >
            <i :class="fp.icon" />
            {{ fp.label }}
          </button>
        </div>

        <!-- Días de crédito (solo si CREDITO) -->
        <div v-if="form.formaPago === 'CREDITO'" class="credito-row">
          <i class="pi pi-calendar" style="color:#1565C0" />
          <span>Días de crédito:</span>
          <div class="dias-btns">
            <button
              v-for="d in [15, 30, 45, 60, 90]"
              :key="d"
              class="dia-btn"
              :class="{ active: form.diasCredito === d }"
              @click="form.diasCredito = d"
            >{{ d }}</button>
          </div>
          <span v-if="form.diasCredito" class="vence-hint">
            Vence: {{ fechaVencimiento }}
          </span>
        </div>
      </div>

      <div class="field" style="margin-top:0.75rem">
        <label>Observaciones adicionales</label>
        <Textarea v-model="form.observaciones" rows="2" class="w-full" placeholder="Opcional..." />
      </div>
    </div>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <!-- Confirmación exitosa — reemplaza el formulario -->
    <div v-if="remisionCreada" class="confirmacion-card">
      <i class="pi pi-check-circle" style="font-size:2.5rem;color:#2E7D32" />
      <div class="conf-titulo">Venta registrada</div>
      <div class="conf-numero">{{ remisionCreada.numero }}</div>
      <div class="conf-total">{{ formatCOP(remisionCreada.total) }}</div>
      <div class="conf-cliente">{{ remisionCreada.cliente?.razonSocial || 'Consumidor Final' }}</div>
      <div class="conf-acciones">
        <Button
          label="Descargar remisión PDF"
          icon="pi pi-file-pdf"
          severity="danger"
          outlined
          class="w-full"
          :loading="descargandoPdf"
          @click="descargarPdf"
        />
        <Button
          label="Nueva venta"
          icon="pi pi-plus"
          class="w-full"
          @click="remisionCreada = null; error = ''"
        />
      </div>
    </div>

    <Button
      v-if="!remisionCreada"
      label="Generar remisión"
      icon="pi pi-file-check"
      class="w-full btn-generar"
      :loading="generando"
      :disabled="!puedeGenerar"
      @click="generarRemision"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import api from '@/api/axios'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Textarea from 'primevue/textarea'
import Message from 'primevue/message'

const toast = useToast()
const clientes = ref([])
const consumidorFinal = ref(null)   // cliente CONSUMIDOR FINAL del sistema
const cargue = ref(null)
const cargueLoading = ref(true)
const generando = ref(false)
const descargandoPdf = ref(false)
const error = ref('')
const ventaRapida = ref(false)
const remisionCreada = ref(null)   // null = formulario, objeto = confirmación

const ventas = ref({})
const form = ref({ clienteId: null, observaciones: '', referenciaComprador: '', formaPago: 'CONTADO', diasCredito: 30 })

const formasPago = [
  { value: 'CONTADO',       label: 'Contado',       icon: 'pi pi-money-bill' },
  { value: 'CREDITO',       label: 'Crédito',       icon: 'pi pi-calendar-clock' },
  { value: 'TRANSFERENCIA', label: 'Transferencia', icon: 'pi pi-send' },
]

const fechaVencimiento = computed(() => {
  if (form.value.formaPago !== 'CREDITO' || !form.value.diasCredito) return ''
  const d = new Date()
  d.setDate(d.getDate() + form.value.diasCredito)
  return d.toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
})

// Mapa: productoId → unidades ya vendidas en remisiones del cargue actual
const vendidoMap = ref({})

function construirVendidoMap(rems) {
  const map = {}
  for (const rem of (rems || [])) {
    for (const det of (rem.detalles || [])) {
      const pid = det.producto?.id
      if (pid) map[pid] = (map[pid] || 0) + Number(det.cantidad || 0)
    }
  }
  vendidoMap.value = map
}

// Unidades realmente disponibles para vender (respetando lo ya vendido hoy)
function disponibleReal(det) {
  const yaVendido = vendidoMap.value[det.producto?.id] || 0
  return Math.max(0, det.cantidadCargue - yaVendido)
}

const clienteSeleccionado = computed(() =>
  clientes.value.find(c => c.id === form.value.clienteId)
)

const totalVenta = computed(() =>
  Object.values(ventas.value).reduce((s, v) => s + (v.subtotal || 0), 0)
)

// El botón se habilita cuando:
// - Venta rápida: existe consumidorFinal + hay productos
// - Cliente registrado: hay clienteId seleccionado + hay productos
const puedeGenerar = computed(() => {
  if (totalVenta.value === 0) return false
  if (ventaRapida.value) return !!consumidorFinal.value
  return !!form.value.clienteId
})

function disponible(det) {
  return det.cantidadCargue - (det.cantidadDevolucion || 0)
}

function getVenta(detalleId) {
  if (!ventas.value[detalleId]) {
    ventas.value[detalleId] = {
      cajas: 0, unidades: 0, subtotal: 0,
      precioVenta: null,
      modoPrecio: 'caja'   // 'unidad' | 'caja'
    }
  }
  return ventas.value[detalleId]
}

// ── Helpers de precio ───────────────────────────────────────
function precioSugeridoCaja(det) {
  return Math.round(det.producto?.precioCaja || 0)
}
function precioSugeridoUnidad(det) {
  const upk = det.producto?.unidadesPorCaja || 1
  return Math.round((det.producto?.precioCaja || 0) / upk)
}
function precioMinimoCaja(det) {
  const precio = det.producto?.precioCaja
  if (!precio) return null
  return Math.round(precio)
}
function precioMinimoUnidad(det) {
  const upk = det.producto?.unidadesPorCaja || 1
  const precio = det.producto?.precioCaja
  if (!precio) return null
  return Math.round(precio / upk)
}

// Cambia el modo y limpia el precio para evitar confusiones entre escalas
function setModoPrecio(det, modo) {
  const v = getVenta(det.id)
  v.modoPrecio = modo
  v.precioVenta = null
  calcVenta(det)
}

// Retorna siempre el precio POR UNIDAD que se enviará al backend
function precioVentaUnidadEfectivo(det) {
  const v = ventas.value[det.id]
  if (!v?.precioVenta || v.precioVenta <= 0) return null
  if (v.modoPrecio === 'caja') {
    const upk = det.producto?.unidadesPorCaja || 1
    return v.precioVenta / upk
  }
  return v.precioVenta
}

function precioVentaEsBajo(det) {
  const v = ventas.value[det.id]
  if (!v?.precioVenta || v.precioVenta <= 0) return false
  if (v.modoPrecio === 'caja') {
    const min = precioMinimoCaja(det)
    return min !== null && v.precioVenta < min
  } else {
    const min = precioMinimoUnidad(det)
    return min !== null && v.precioVenta < min
  }
}

function calcVenta(det) {
  const v = ventas.value[det.id]
  if (!v) return
  const upk = det.producto?.unidadesPorCaja || 1
  // Precio efectivo por unidad para calcular subtotal
  const precioUnitario = precioVentaUnidadEfectivo(det) ?? precioSugeridoUnidad(det)
  let totalUds = (v.cajas || 0) * upk + (v.unidades || 0)
  const maxUds = disponibleReal(det)
  if (totalUds > maxUds) {
    totalUds = maxUds
    v.cajas = Math.floor(maxUds / upk)
    v.unidades = maxUds % upk
  }
  v.subtotal = totalUds * precioUnitario
}

function onClienteChange() {}

function formatCOP(val) {
  if (!val && val !== 0) return '$0'
  return new Intl.NumberFormat('es-CO', {
    style: 'currency', currency: 'COP', maximumFractionDigits: 0
  }).format(val)
}

async function generarRemision() {
  error.value = ''
  remisionCreada.value = null

  // Determinar clienteId a enviar
  const clienteId = ventaRapida.value ? null : form.value.clienteId

  if (!ventaRapida.value && !clienteId) {
    error.value = 'Seleccione un cliente'
    return
  }
  if (totalVenta.value === 0) {
    error.value = 'Ingrese al menos un producto'
    return
  }

  // Validar precios antes de enviar
  for (const [detalleId, v] of Object.entries(ventas.value)) {
    if (v.cajas > 0 || v.unidades > 0) {
      const det = cargue.value?.detalles?.find(d => d.id === parseInt(detalleId))
      if (det && precioVentaEsBajo(det)) {
        const modo = v.modoPrecio === 'caja' ? 'por caja' : 'por unidad'
        error.value = `El precio de "${det.producto?.nombreCompleto}" (${modo}) está por debajo del mínimo permitido.`
        return
      }
    }
  }

  const detalles = Object.entries(ventas.value)
    .filter(([, v]) => (v.cajas > 0 || v.unidades > 0))
    .map(([detalleId, v]) => {
      const det = cargue.value?.detalles?.find(d => d.id === parseInt(detalleId))
      return {
        cargueDetalleId: parseInt(detalleId),
        cantidadCajas: v.cajas || 0,
        cantidadUnidades: v.unidades || 0,
        // Siempre enviamos precio POR UNIDAD al backend, independiente del modo
        precioVentaUnidad: det ? precioVentaUnidadEfectivo(det) : null
      }
    })

  // Armar observaciones: incluir referencia del comprador si la hay
  let obsCompleta = form.value.observaciones || ''
  if (ventaRapida.value && form.value.referenciaComprador?.trim()) {
    const refComprador = `Comprador: ${form.value.referenciaComprador.trim()}`
    obsCompleta = obsCompleta ? `${refComprador} | ${obsCompleta}` : refComprador
  }

  generando.value = true
  try {
    const payload = {
      cargueId: cargue.value.id,
      clienteId: clienteId,
      observaciones: obsCompleta,
      formaPago: form.value.formaPago || 'CONTADO',
      detalles
    }
    if (form.value.formaPago === 'CREDITO' && form.value.diasCredito) {
      payload.diasCredito = form.value.diasCredito
    }
    const res = await api.post('/remisiones', payload)
    // Mostrar pantalla de confirmación en lugar de descargar automáticamente
    remisionCreada.value = res.data
    ventas.value = {}
    form.value = { clienteId: null, observaciones: '', referenciaComprador: '', formaPago: 'CONTADO', diasCredito: 30 }
    refrescarDisponibles()   // actualiza los límites disponibles para la próxima venta
  } catch (e) {
    error.value = e.response?.data?.mensaje || 'Error al generar la remisión'
  } finally {
    generando.value = false
  }
}

async function descargarPdf() {
  if (!remisionCreada.value) return
  descargandoPdf.value = true
  try {
    const res = await api.get(`/remisiones/${remisionCreada.value.id}/pdf`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `remision_${remisionCreada.value.numero}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    error.value = 'No se pudo descargar el PDF'
  } finally {
    descargandoPdf.value = false
  }
}

async function cargarDatos() {
  try {
    const [resC, resCargue, resCF, resRem] = await Promise.allSettled([
      api.get('/clientes'),
      api.get('/cargues/mi-cargue-hoy'),
      api.get('/clientes/consumidor-final'),
      api.get('/remisiones/mis-remisiones')
    ])
    if (resC.status === 'fulfilled') clientes.value = resC.value.data || []
    if (resCargue.status === 'fulfilled') cargue.value = resCargue.value.data
    if (resCF.status === 'fulfilled') consumidorFinal.value = resCF.value.data
    if (resCargue.status === 'fulfilled' && cargue.value?.grupoId) {
      try {
        const resGrupo = await api.get(`/cargues/${cargue.value.id}/grupo-vendido`)
        vendidoMap.value = resGrupo.data || {}
      } catch { /* silencioso */ }
    } else if (resRem.status === 'fulfilled') {
      construirVendidoMap(resRem.value.data)
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Error cargando datos', life: 4000 })
  } finally {
    cargueLoading.value = false
  }
}

// Refresca el mapa de disponibles después de generar una venta
// Si el cargue es grupal usa el endpoint de grupo para ver stock real compartido
async function refrescarDisponibles() {
  try {
    if (cargue.value?.grupoId) {
      const res = await api.get(`/cargues/${cargue.value.id}/grupo-vendido`)
      vendidoMap.value = res.data || {}
    } else {
      const res = await api.get('/remisiones/mis-remisiones')
      construirVendidoMap(res.data)
    }
  } catch { /* silencioso */ }
}

function onVisibilityChange() {
  // Al volver a la pestaña recarga clientes y cargue para ver datos nuevos
  // sin tocar 'ventas' ni 'form' para no perder la venta en curso
  if (document.visibilityState === 'visible') cargarDatos()
}

onMounted(() => {
  cargarDatos()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped>
.nueva-venta { display: flex; flex-direction: column; gap: 1rem; }

.info-card {
  background: white; border-radius: 12px; padding: 1rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.06);
}
.info-card h3 { margin: 0 0 0.25rem; color: #333; }
.info-card p { font-size: 0.85rem; color: #666; margin: 0; }

/* ── Toggle tipo de venta ── */
.tipo-venta-toggle {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
}
.tipo-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem;
  border: 2px solid #e0e0e0;
  border-radius: 10px;
  background: white;
  font-size: 0.85rem;
  font-weight: 600;
  color: #888;
  cursor: pointer;
  transition: all 0.2s;
}
.tipo-btn.active {
  border-color: #1565C0;
  background: #E3F2FD;
  color: #1565C0;
}
.tipo-btn.rapida.active {
  border-color: #F57F17;
  background: #FFF8E1;
  color: #F57F17;
}

/* ── Sección cards ── */
.section-card {
  background: white; border-radius: 12px; padding: 1rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.06);
  display: flex; flex-direction: column; gap: 0.75rem;
}

.sec-label { font-size: 0.78rem; font-weight: 700; color: #888; text-transform: uppercase; letter-spacing: 0.5px; }

.cliente-info { display: flex; align-items: center; gap: 0.4rem; font-size: 0.82rem; color: #666; }

/* ── Venta rápida ── */
.venta-rapida-card { border: 2px solid #FFE082; }
.cf-info {
  display: flex; align-items: flex-start; gap: 0.75rem;
  background: #FFF8E1; border-radius: 8px; padding: 0.75rem;
}
.cf-titulo { font-weight: 700; color: #333; }
.cf-nit { font-size: 0.8rem; color: #888; margin-top: 2px; }
.cf-alerta {
  display: flex; align-items: flex-start; gap: 0.75rem;
  background: #FFF3F3; border-radius: 8px; padding: 0.75rem;
  font-size: 0.85rem; color: #555;
}

/* ── Productos ── */
.producto-venta-item {
  border: 1px solid #f0f0f0; border-radius: 8px; padding: 0.75rem;
}
.pv-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem; }
.pv-nombre { font-weight: 600; font-size: 0.9rem; color: #333; }
.pv-disponible { font-size: 0.78rem; color: #1565C0; font-weight: 600; }
.pv-disponible.disp-bajo { color: #E65100; }
.item-agotado { opacity: 0.6; }
.agotado-msg {
  font-size: 0.82rem; color: #E53935;
  display: flex; align-items: center; gap: 0.4rem;
  padding: 0.4rem 0;
}
.pv-controls { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
.pv-control { display: flex; flex-direction: column; gap: 0.2rem; }
.pv-label { font-size: 0.7rem; color: #999; text-transform: uppercase; }
.pv-subtotal { margin-left: auto; font-weight: 700; color: #D32F2F; }

/* ── Precio negociado ── */
.pv-precio-row {
  margin-top: 0.6rem;
  padding-top: 0.6rem;
  border-top: 1px dashed #f0f0f0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.6rem;
  flex-wrap: wrap;
}

/* Toggle $ unidad / $ caja */
.modo-precio-toggle {
  display: flex;
  border: 1.5px solid #e0e0e0;
  border-radius: 8px;
  overflow: hidden;
  width: fit-content;
  align-self: flex-start;
}
.modo-btn {
  padding: 0.3rem 0.7rem;
  font-size: 0.72rem;
  font-weight: 700;
  background: white;
  border: none;
  cursor: pointer;
  color: #aaa;
  transition: all 0.15s;
  white-space: nowrap;
}
.modo-btn:first-child { border-right: 1.5px solid #e0e0e0; }
.modo-btn.active {
  background: #1565C0;
  color: white;
}
.pv-precio-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding-top: 0.15rem;
}
.precio-chip {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  width: fit-content;
}
.precio-chip.sugerido { background: #E8F5E9; color: #2E7D32; }
.precio-chip.minimo   { background: #FFF3E0; color: #E65100; }
.pv-precio-input {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  align-items: flex-end;
}
.precio-error {
  font-size: 0.7rem;
  color: #D32F2F;
  font-style: italic;
}
:deep(.precio-bajo input) {
  border-color: #D32F2F !important;
  background: #FFF8F8 !important;
}

/* ── Forma de pago ── */
.fp-toggle {
  display: flex; gap: 0.5rem; flex-wrap: wrap;
}
.fp-btn {
  display: flex; align-items: center; gap: 0.4rem;
  padding: 0.45rem 0.9rem;
  border: 1.5px solid #e0e0e0; border-radius: 20px;
  background: white; font-size: 0.8rem; font-weight: 600; color: #888;
  cursor: pointer; transition: all 0.2s;
}
.fp-btn.active {
  border-color: #1565C0; background: #E3F2FD; color: #1565C0;
}
.credito-row {
  display: flex; align-items: center; gap: 0.5rem;
  flex-wrap: wrap; margin-top: 0.6rem;
  padding: 0.6rem 0.75rem; background: #E8F0FE; border-radius: 8px;
  font-size: 0.82rem; color: #1565C0;
}
.dias-btns { display: flex; gap: 0.3rem; }
.dia-btn {
  padding: 0.2rem 0.6rem; border: 1.5px solid #90CAF9;
  border-radius: 14px; background: white; font-size: 0.78rem;
  font-weight: 700; color: #1565C0; cursor: pointer; transition: all 0.15s;
}
.dia-btn.active { background: #1565C0; color: white; border-color: #1565C0; }
.vence-hint { font-size: 0.75rem; color: #1976D2; font-style: italic; }

/* ── Total ── */
.total-row { display: flex; justify-content: space-between; align-items: center; font-size: 1rem; }
.total-val { font-size: 1.3rem; color: #D32F2F; }

.field { display: flex; flex-direction: column; gap: 0.35rem; }
.field label { font-size: 0.82rem; font-weight: 600; color: #666; }
.hint { font-size: 0.75rem; color: #aaa; font-style: italic; }

.sin-cargue { text-align: center; padding: 2rem; color: #aaa; display: flex; flex-direction: column; align-items: center; gap: 0.5rem; }

.btn-generar { margin-top: 0.5rem; }

/* ── Confirmación exitosa ── */
.confirmacion-card {
  background: white; border-radius: 16px; padding: 2rem 1.25rem;
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
  display: flex; flex-direction: column; align-items: center; gap: 0.75rem;
  text-align: center;
}
.conf-titulo { font-size: 1.1rem; font-weight: 700; color: #2E7D32; }
.conf-numero { font-size: 0.9rem; color: #888; }
.conf-total { font-size: 1.8rem; font-weight: 800; color: #D32F2F; }
.conf-cliente { font-size: 0.85rem; color: #555; }
.conf-acciones { display: flex; flex-direction: column; gap: 0.5rem; width: 100%; margin-top: 0.5rem; }
</style>
