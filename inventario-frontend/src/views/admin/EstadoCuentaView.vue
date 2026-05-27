<template>
  <div class="estado-cuenta-page">

    <!-- ── Pestañas ─────────────────────────────────────────── -->
    <div class="tabs-bar">
      <button :class="['tab-btn', { active: vistaActiva === 'cartera' }]" @click="vistaActiva = 'cartera'">
        <i class="pi pi-list" /> Cartera individual
      </button>
      <button :class="['tab-btn', { active: vistaActiva === 'resumen' }]" @click="vistaActiva = 'resumen'; cargarResumen()">
        <i class="pi pi-chart-pie" /> Resumen global
      </button>
    </div>

    <!-- ════════════════════════════════════════════════════════ -->
    <!-- VISTA 1: CARTERA INDIVIDUAL (vista original)            -->
    <!-- ════════════════════════════════════════════════════════ -->
    <template v-if="vistaActiva === 'cartera'">

    <!-- ── KPIs ──────────────────────────────────────────────── -->
    <div class="kpi-strip">
      <div class="kpi-item kpi-total">
        <span class="kpi-val">{{ formatCOP(resumen.totalPendiente) }}</span>
        <span class="kpi-lbl"><i class="pi pi-dollar" /> Por cobrar</span>
      </div>
      <div class="kpi-item kpi-vencido">
        <span class="kpi-val">{{ resumen.vencidas }}</span>
        <span class="kpi-lbl"><i class="pi pi-times-circle" /> Vencidas</span>
      </div>
      <div class="kpi-item kpi-parcial">
        <span class="kpi-val">{{ resumen.parciales }}</span>
        <span class="kpi-lbl"><i class="pi pi-clock" /> Con abonos</span>
      </div>
      <div class="kpi-item kpi-ok">
        <span class="kpi-val">{{ resumen.pagadas }}</span>
        <span class="kpi-lbl"><i class="pi pi-check-circle" /> Pagadas (hoy)</span>
      </div>
    </div>

    <!-- ── Toolbar ────────────────────────────────────────────── -->
    <div class="toolbar-card">
      <div class="toolbar-left">
        <!-- Filtro estado -->
        <Select
          v-model="filtroEstado"
          :options="opcionesEstado"
          optionValue="value"
          optionLabel="label"
          placeholder="Todos"
          showClear
          class="sel-estado"
        />
        <!-- Búsqueda libre -->
        <span class="p-input-icon-left search-wrap">
          <i class="pi pi-search" />
          <InputText v-model="busqueda" placeholder="Buscar cliente o No. remisión..." class="search-input" />
        </span>
      </div>
      <div class="toolbar-right">
        <Button
          :label="soloActivos ? 'Ver todos' : 'Ver activos'"
          :icon="soloActivos ? 'pi pi-eye' : 'pi pi-eye-slash'"
          outlined size="small"
          @click="toggleSoloActivos"
        />
        <Button icon="pi pi-refresh" outlined size="small" @click="cargar" :loading="loading" />
      </div>
    </div>

    <!-- ── Tabla ──────────────────────────────────────────────── -->
    <div class="tabla-card">
      <div v-if="loading" class="loading-state"><i class="pi pi-spin pi-spinner" /> Cargando...</div>

      <div v-else-if="creditosFiltrados.length === 0" class="empty-state">
        <i class="pi pi-check-circle" style="font-size:2.5rem;color:#2E7D32" />
        <p>No hay créditos pendientes</p>
      </div>

      <template v-else>
        <!-- Cabecera -->
        <div class="tbl-header">
          <span>Remisión</span>
          <span>Cliente</span>
          <span>Fecha</span>
          <span>Vence</span>
          <span class="col-r">Total venta</span>
          <span class="col-r">Pagado</span>
          <span class="col-r">Saldo</span>
          <span>Estado</span>
          <span class="col-center">Acciones</span>
        </div>

        <!-- Filas -->
        <div
          v-for="c in creditosFiltrados"
          :key="c.remisionId"
          class="tbl-row"
          :class="rowClass(c)"
        >
          <!-- Remisión -->
          <span class="col-num">{{ c.numero }}</span>

          <!-- Cliente -->
          <span class="col-cliente">
            <span class="cli-nombre">{{ c.clienteNombre }}</span>
            <span class="cli-nit">{{ c.clienteNit }}</span>
          </span>

          <!-- Fecha remisión -->
          <span class="col-fecha">{{ formatFecha(c.fecha) }}</span>

          <!-- Vence -->
          <span class="col-vence" :class="claseVencimiento(c)">
            <template v-if="c.fechaVencimiento">
              {{ formatFecha(c.fechaVencimiento) }}
              <span class="dias-badge" :class="claseVencimiento(c)">
                {{ diasLabel(c.diasRestantes) }}
              </span>
            </template>
            <span v-else class="sin-vence">—</span>
          </span>

          <!-- Total venta -->
          <span class="col-r col-total">{{ formatCOP(c.totalVenta) }}</span>

          <!-- Pagado -->
          <span class="col-r col-pagado">{{ formatCOP(c.montoPagado) }}</span>

          <!-- Saldo -->
          <span class="col-r col-saldo" :class="{ 'saldo-cero': c.saldoPendiente == 0 }">
            {{ c.saldoPendiente > 0 ? formatCOP(c.saldoPendiente) : '✓ Saldado' }}
          </span>

          <!-- Estado badge -->
          <span>
            <span :class="['estado-badge', 'estado-' + c.estadoPago.toLowerCase()]">
              {{ estadoLabel(c.estadoPago) }}
            </span>
          </span>

          <!-- Acciones -->
          <span class="col-center col-acciones">
            <!-- Ver abonos -->
            <Button
              icon="pi pi-list"
              text rounded size="small"
              severity="secondary"
              v-tooltip.top="'Ver historial de abonos'"
              @click="abrirHistorial(c)"
            />
            <!-- Abonar -->
            <Button
              v-if="c.estadoPago !== 'PAGADO'"
              icon="pi pi-plus-circle"
              text rounded size="small"
              severity="info"
              v-tooltip.top="'Registrar abono'"
              @click="abrirAbono(c)"
            />
            <!-- Pagar total -->
            <Button
              v-if="c.estadoPago !== 'PAGADO'"
              icon="pi pi-check-circle"
              text rounded size="small"
              severity="success"
              v-tooltip.top="'Marcar como pagada total'"
              @click="confirmarPagarTotal(c)"
            />
          </span>
        </div>
      </template>
    </div>

  </template>
  <!-- ── fin vista cartera individual ── -->

  <!-- ════════════════════════════════════════════════════════ -->
  <!-- VISTA 2: RESUMEN GLOBAL DE CARTERA                      -->
  <!-- ════════════════════════════════════════════════════════ -->
  <!-- NOTE: ambos templates están dentro de .estado-cuenta-page -->
  <template v-if="vistaActiva === 'resumen'">

    <!-- Filtro de período -->
    <div class="toolbar-card">
      <div class="toolbar-left">
        <label class="field-lbl">Desde</label>
        <DatePicker v-model="resumenDesde" dateFormat="dd/mm/yy" class="date-pick" />
        <label class="field-lbl">Hasta</label>
        <DatePicker v-model="resumenHasta" dateFormat="dd/mm/yy" class="date-pick" />
        <Button label="Consultar" icon="pi pi-search" @click="cargarResumen" :loading="loadingResumen" />
      </div>
    </div>

    <!-- KPIs resumen -->
    <div v-if="resumen2" class="kpi-strip">
      <div class="kpi-item kpi-total">
        <span class="kpi-val">{{ formatCOP(resumen2.totalCartera) }}</span>
        <span class="kpi-lbl"><i class="pi pi-dollar" /> Cartera total</span>
      </div>
      <div class="kpi-item kpi-ok">
        <span class="kpi-val">{{ formatCOP(resumen2.recaudadoPeriodo) }}</span>
        <span class="kpi-lbl"><i class="pi pi-arrow-down" /> Recaudado en período</span>
      </div>
      <div class="kpi-item kpi-vencido">
        <span class="kpi-val">{{ formatCOP(resumen2.totalPendiente) }}</span>
        <span class="kpi-lbl"><i class="pi pi-clock" /> Pendiente por cobrar</span>
      </div>
      <div class="kpi-item kpi-parcial">
        <span class="kpi-val">{{ resumen2.pctCobranza }}%</span>
        <span class="kpi-lbl"><i class="pi pi-chart-bar" /> % Cobranza</span>
      </div>
    </div>

    <div v-if="loadingResumen" class="loading-state"><i class="pi pi-spin pi-spinner" /> Cargando...</div>

    <template v-else-if="resumen2">

      <!-- Tabla por cliente -->
      <div class="tabla-card">
        <div class="tabla-titulo">
          <i class="pi pi-users" /> Cartera por cliente
          <span class="badge-count">{{ resumen2.numClientesConDeuda }} con saldo pendiente</span>
        </div>
        <div class="tbl-header tbl-header-cli">
          <span>Cliente</span>
          <span>NIT</span>
          <span class="col-r">Total vendido</span>
          <span class="col-r">Cobrado</span>
          <span class="col-r">Saldo pendiente</span>
          <span class="col-center">Facturas</span>
          <span class="col-center">Estado</span>
        </div>
        <div
          v-for="c in resumen2.porCliente"
          :key="c.clienteId"
          class="tbl-row tbl-row-cli"
          :class="{ 'row-vencido': c.tieneVencida }"
        >
          <span class="cli-nombre">{{ c.clienteNombre }}</span>
          <span class="cli-nit">{{ c.clienteNit }}</span>
          <span class="col-r">{{ formatCOP(c.totalVendido) }}</span>
          <span class="col-r" style="color:#2E7D32;font-weight:600">{{ formatCOP(c.totalCobrado) }}</span>
          <span class="col-r" :style="c.saldoPendiente > 0 ? 'color:#C62828;font-weight:700' : 'color:#2E7D32'">
            {{ c.saldoPendiente > 0 ? formatCOP(c.saldoPendiente) : '✓ Saldado' }}
          </span>
          <span class="col-center">{{ c.numFacturas }}</span>
          <span class="col-center">
            <span v-if="c.tieneVencida" class="estado-badge estado-pendiente">Vencida</span>
            <span v-else-if="c.saldoPendiente > 0" class="estado-badge estado-parcial">Pendiente</span>
            <span v-else class="estado-badge estado-pagado">Al día</span>
          </span>
        </div>
        <div v-if="resumen2.porCliente.length === 0" class="empty-state">
          <i class="pi pi-check-circle" style="font-size:2rem;color:#2E7D32" />
          <p>No hay cartera registrada</p>
        </div>
      </div>

      <!-- Tabla recaudos del período -->
      <div class="tabla-card">
        <div class="tabla-titulo">
          <i class="pi pi-money-bill" /> Recaudos registrados en el período
          <span class="badge-count">{{ resumen2.recaudosPeriodo.length }} pagos</span>
        </div>
        <div v-if="resumen2.recaudosPeriodo.length === 0" class="empty-state" style="padding:1.5rem">
          <i class="pi pi-info-circle" style="color:#aaa" />
          <p>Sin recaudos en el período seleccionado</p>
        </div>
        <template v-else>
          <div class="tbl-header tbl-header-rec">
            <span>Fecha</span>
            <span>Cliente</span>
            <span>Remisión</span>
            <span class="col-r">Monto</span>
            <span>Observaciones</span>
            <span>Registrado por</span>
          </div>
          <div
            v-for="r in resumen2.recaudosPeriodo"
            :key="r.id"
            class="tbl-row tbl-row-rec"
          >
            <span class="col-fecha">{{ formatFecha(r.fecha) }}</span>
            <span class="cli-nombre">{{ r.cliente }}</span>
            <span class="col-num">{{ r.remision }}</span>
            <span class="col-r" style="color:#2E7D32;font-weight:700">{{ formatCOP(r.monto) }}</span>
            <span class="col-notas">{{ r.notas || '—' }}</span>
            <span style="color:#888;font-size:0.8rem">{{ r.registradoPor }}</span>
          </div>
        </template>
      </div>

    </template>

  </template>
  <!-- ── fin vista resumen global ── -->

  </div><!-- /.estado-cuenta-page -->

  <!-- ── Dialog Historial de abonos ──────────────────────────── -->
  <Dialog
    v-model:visible="dialogHistorial"
    :header="`Historial — ${cuentaActual?.numero}`"
    modal
    :style="{ width: '520px', maxWidth: '98vw' }"
    :draggable="false"
  >
    <div v-if="cuentaActual" class="historial-dialog">
      <!-- Resumen -->
      <div class="hist-resumen">
        <div class="hist-res-item">
          <span class="hist-res-lbl">Total venta</span>
          <span class="hist-res-val">{{ formatCOP(cuentaActual.totalVenta) }}</span>
        </div>
        <div class="hist-res-item">
          <span class="hist-res-lbl">Pagado</span>
          <span class="hist-res-val pagado-val">{{ formatCOP(cuentaActual.montoPagado) }}</span>
        </div>
        <div class="hist-res-item">
          <span class="hist-res-lbl">Saldo</span>
          <span class="hist-res-val saldo-val">{{ formatCOP(cuentaActual.saldoPendiente) }}</span>
        </div>
      </div>

      <!-- Barra de progreso pago -->
      <div class="pago-progreso-wrap">
        <div class="pago-progreso-bar">
          <div
            class="pago-progreso-fill"
            :style="{ width: porcentajePagado(cuentaActual) + '%' }"
          />
        </div>
        <span class="pago-progreso-pct">{{ porcentajePagado(cuentaActual) }}% pagado</span>
      </div>

      <!-- Resumen NC/ND si existen -->
      <div v-if="cuentaActual.ajusteNc > 0 || cuentaActual.ajusteNd > 0" class="notas-resumen">
        <div v-if="cuentaActual.ajusteNc > 0" class="nota-resumen-item nota-nc">
          <i class="pi pi-arrow-down-right" />
          <span>Notas Crédito: <strong>{{ formatCOP(cuentaActual.ajusteNc) }}</strong></span>
        </div>
        <div v-if="cuentaActual.ajusteNd > 0" class="nota-resumen-item nota-nd">
          <i class="pi pi-arrow-up-right" />
          <span>Notas Débito: <strong>{{ formatCOP(cuentaActual.ajusteNd) }}</strong></span>
        </div>
      </div>

      <!-- Lista de movimientos -->
      <div v-if="cuentaActual.abonos.length === 0" class="sin-abonos">
        <i class="pi pi-info-circle" /> Sin movimientos registrados aún
      </div>
      <div v-else class="abonos-lista">
        <div
          v-for="a in cuentaActual.abonos"
          :key="(a.tipo || 'ABONO') + '-' + a.id"
          class="abono-item"
          :class="{
            'abono-nc': a.tipo === 'NOTA_CREDITO',
            'abono-nd': a.tipo === 'NOTA_DEBITO'
          }"
        >
          <div class="abono-fecha">{{ formatFecha(a.fecha) }}</div>
          <div class="abono-monto" :class="{ 'monto-nc': a.tipo === 'NOTA_CREDITO', 'monto-nd': a.tipo === 'NOTA_DEBITO' }">
            <span v-if="a.tipo === 'NOTA_CREDITO'">-{{ formatCOP(Math.abs(a.monto)) }}</span>
            <span v-else-if="a.tipo === 'NOTA_DEBITO'" style="color:#C62828">+{{ formatCOP(a.monto) }}</span>
            <span v-else>{{ formatCOP(a.monto) }}</span>
          </div>
          <div class="abono-notas" v-if="a.notas">{{ a.notas }}</div>
        </div>
      </div>

      <div class="dialog-footer">
        <Button label="Cerrar" text @click="dialogHistorial = false" />
        <Button
          v-if="cuentaActual.estadoPago !== 'PAGADO'"
          label="Registrar abono"
          icon="pi pi-plus"
          outlined
          severity="info"
          @click="() => { dialogHistorial = false; abrirAbono(cuentaActual) }"
        />
      </div>
    </div>
  </Dialog>

  <!-- ── Dialog Registrar Abono ───────────────────────────────── -->
  <Dialog
    v-model:visible="dialogAbono"
    :header="`Abonar — ${cuentaActual?.numero}`"
    modal
    :style="{ width: '420px', maxWidth: '98vw' }"
    :draggable="false"
  >
    <div v-if="cuentaActual" class="abono-form">

      <!-- Info de la cuenta -->
      <div class="abono-info-strip">
        <span><strong>{{ cuentaActual.clienteNombre }}</strong></span>
        <span class="saldo-pendiente-lbl">Saldo: <strong>{{ formatCOP(cuentaActual.saldoPendiente) }}</strong></span>
      </div>

      <div class="field">
        <label>Monto del abono *</label>
        <InputNumber
          v-model="formAbono.monto"
          mode="currency" currency="COP" locale="es-CO"
          :max="cuentaActual.saldoPendiente"
          :min="1"
          placeholder="Ingrese el valor"
          class="w-full"
        />
      </div>

      <div class="field">
        <label>Fecha del pago</label>
        <DatePicker v-model="formAbono.fecha" dateFormat="dd/mm/yy" :maxDate="hoy" class="w-full" />
      </div>

      <div class="field">
        <label>Observaciones</label>
        <Textarea v-model="formAbono.notas" rows="2" class="w-full" placeholder="Ej: Pago en efectivo, Nequi..." />
      </div>

      <Message v-if="abonoError" severity="error" :closable="false">{{ abonoError }}</Message>

      <div class="dialog-footer">
        <Button label="Cancelar" text @click="dialogAbono = false" />
        <Button
          label="Registrar abono"
          icon="pi pi-save"
          @click="guardarAbono"
          :loading="guardandoAbono"
        />
      </div>
    </div>
  </Dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useToast }   from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import api from '@/api/axios'
import Button      from 'primevue/button'
import InputText   from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select      from 'primevue/select'
import DatePicker  from 'primevue/datepicker'
import Dialog      from 'primevue/dialog'
import Message     from 'primevue/message'
import Textarea    from 'primevue/textarea'

const toast   = useToast()
const confirm = useConfirm()

const hoy     = new Date()
const loading = ref(false)
const creditos = ref([])

// ── Vista activa ──────────────────────────────────────────────
const vistaActiva = ref('cartera')

// ── Resumen global ────────────────────────────────────────────
const loadingResumen = ref(false)
const resumen2       = ref(null)
const resumenDesde   = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const resumenHasta   = ref(new Date())

async function cargarResumen() {
  loadingResumen.value = true
  try {
    const params = {
      inicio: formatLocalDate(resumenDesde.value),
      fin:    formatLocalDate(resumenHasta.value)
    }
    const res = await api.get('/estado-cuenta/resumen-cartera', { params })
    resumen2.value = res.data
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo cargar el resumen', life: 4000 })
  } finally {
    loadingResumen.value = false
  }
}
const busqueda  = ref('')
const filtroEstado = ref(null)
const soloActivos  = ref(true)

const opcionesEstado = [
  { label: 'Pendientes', value: 'PENDIENTE' },
  { label: 'Con abonos', value: 'PARCIAL'   },
  { label: 'Pagados',    value: 'PAGADO'    }
]

// ── KPIs rápidos calculados desde la lista ────────────────────
const resumen = computed(() => {
  const hoyStr = new Date().toISOString().slice(0, 10)
  return {
    totalPendiente: creditos.value
      .filter(c => c.estadoPago !== 'PAGADO')
      .reduce((s, c) => s + Number(c.saldoPendiente || 0), 0),
    vencidas: creditos.value.filter(c =>
      c.estadoPago !== 'PAGADO' &&
      c.fechaVencimiento && c.fechaVencimiento < hoyStr
    ).length,
    parciales: creditos.value.filter(c => c.estadoPago === 'PARCIAL').length,
    pagadas:   creditos.value.filter(c =>
      c.estadoPago === 'PAGADO' &&
      c.abonos?.some(a => a.fecha === hoyStr)
    ).length
  }
})

// ── Filtrado ──────────────────────────────────────────────────
const creditosFiltrados = computed(() => {
  return creditos.value.filter(c => {
    const mEstado = !filtroEstado.value || c.estadoPago === filtroEstado.value
    const q = busqueda.value.trim().toLowerCase()
    const mBusq = !q ||
      c.numero?.toLowerCase().includes(q) ||
      c.clienteNombre?.toLowerCase().includes(q) ||
      c.clienteNit?.includes(q)
    return mEstado && mBusq
  })
})

// ── Carga de datos ────────────────────────────────────────────
async function cargar() {
  loading.value = true
  try {
    const res = await api.get('/estado-cuenta', { params: { soloActivos: soloActivos.value } })
    creditos.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo cargar el estado de cuenta', life: 4000 })
  } finally {
    loading.value = false
  }
}

function toggleSoloActivos() {
  soloActivos.value = !soloActivos.value
  cargar()
}

// ── Clases CSS dinámicas ──────────────────────────────────────
function rowClass(c) {
  if (c.estadoPago === 'PAGADO') return 'row-pagado'
  if (c.fechaVencimiento && c.fechaVencimiento < new Date().toISOString().slice(0, 10)) return 'row-vencido'
  if (c.diasRestantes !== null && c.diasRestantes <= 3) return 'row-urgente'
  return ''
}

function claseVencimiento(c) {
  if (!c.fechaVencimiento || c.estadoPago === 'PAGADO') return ''
  if (c.diasRestantes < 0)  return 'vence-vencido'
  if (c.diasRestantes <= 3) return 'vence-urgente'
  if (c.diasRestantes <= 7) return 'vence-proximo'
  return ''
}

function diasLabel(dias) {
  if (dias === null) return ''
  if (dias < 0)  return `${Math.abs(dias)}d vencida`
  if (dias === 0) return 'Hoy'
  if (dias === 1) return 'Mañana'
  return `${dias}d`
}

function estadoLabel(e) {
  return { PENDIENTE: 'Pendiente', PARCIAL: 'Con abono', PAGADO: 'Pagado' }[e] || e
}

function porcentajePagado(c) {
  if (!c.totalVenta || c.totalVenta === 0) return 0
  return Math.min(100, Math.round((c.montoPagado / c.totalVenta) * 100))
}

// ── Historial ─────────────────────────────────────────────────
const dialogHistorial = ref(false)
const cuentaActual    = ref(null)

function abrirHistorial(c) {
  cuentaActual.value   = c
  dialogHistorial.value = true
}

// ── Abonar ────────────────────────────────────────────────────
const dialogAbono    = ref(false)
const guardandoAbono = ref(false)
const abonoError     = ref('')
const formAbono      = ref({ monto: null, fecha: new Date(), notas: '' })

function abrirAbono(c) {
  cuentaActual.value = c
  formAbono.value    = { monto: null, fecha: new Date(), notas: '' }
  abonoError.value   = ''
  dialogAbono.value  = true
}

async function guardarAbono() {
  abonoError.value = ''
  if (!formAbono.value.monto || formAbono.value.monto <= 0) {
    abonoError.value = 'El monto debe ser mayor a 0'
    return
  }
  guardandoAbono.value = true
  try {
    const fechaStr = formAbono.value.fecha
      ? formatLocalDate(formAbono.value.fecha)
      : null
    await api.post(`/estado-cuenta/${cuentaActual.value.remisionId}/abono`, {
      monto: formAbono.value.monto,
      fecha: fechaStr,
      notas: formAbono.value.notas || ''
    })
    toast.add({ severity: 'success', summary: '¡Abono registrado!',
      detail: `${formatCOP(formAbono.value.monto)} registrado en ${cuentaActual.value.numero}`, life: 3000 })
    dialogAbono.value = false
    await cargar()
  } catch (e) {
    abonoError.value = e.response?.data?.mensaje || 'Error al registrar el abono'
  } finally {
    guardandoAbono.value = false
  }
}

// ── Pagar total ───────────────────────────────────────────────
function confirmarPagarTotal(c) {
  confirm.require({
    message: `¿Marcar la remisión ${c.numero} como completamente pagada?\nSaldo pendiente: ${formatCOP(c.saldoPendiente)}`,
    header: 'Confirmar pago total',
    icon: 'pi pi-check-circle',
    acceptLabel: 'Sí, marcar pagada',
    rejectLabel: 'Cancelar',
    acceptClass: 'p-button-success',
    accept: async () => {
      try {
        await api.put(`/estado-cuenta/${c.remisionId}/pagar-total`, { notas: 'Pago total' })
        toast.add({ severity: 'success', summary: '¡Pagada!',
          detail: `${c.numero} marcada como pagada`, life: 3000 })
        await cargar()
      } catch (e) {
        toast.add({ severity: 'error', summary: 'Error',
          detail: e.response?.data?.mensaje || 'Error al registrar el pago', life: 4000 })
      }
    }
  })
}

// ── Helpers ───────────────────────────────────────────────────
function formatCOP(v) {
  if (v === null || v === undefined) return '—'
  return new Intl.NumberFormat('es-CO', {
    style: 'currency', currency: 'COP', maximumFractionDigits: 0
  }).format(v)
}
function formatFecha(f) {
  if (!f) return '—'
  return new Date(f + 'T00:00:00').toLocaleDateString('es-CO', {
    day: '2-digit', month: '2-digit', year: 'numeric'
  })
}
function formatLocalDate(date) {
  const d = date instanceof Date ? date : new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
}

onMounted(cargar)
</script>

<style scoped>
.estado-cuenta-page { display: flex; flex-direction: column; gap: 1rem; }

/* ── Pestañas ───────────────────────────────────────────────── */
.tabs-bar {
  display: flex; gap: 0.5rem;
  background: white; border-radius: 10px; padding: 0.5rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
}
.tab-btn {
  display: flex; align-items: center; gap: 0.5rem;
  padding: 0.55rem 1.25rem; border-radius: 8px;
  border: none; background: transparent;
  font-size: 0.88rem; font-weight: 600; color: #666;
  cursor: pointer; transition: background 0.15s, color 0.15s;
}
.tab-btn:hover { background: #f5f5f5; color: #333; }
.tab-btn.active { background: rgba(211,47,47,0.1); color: #D32F2F; }

/* ── Resumen global extras ──────────────────────────────────── */
.tabla-titulo {
  display: flex; align-items: center; gap: 0.6rem;
  padding: 0.85rem 1.1rem; font-size: 0.9rem; font-weight: 700;
  color: #333; border-bottom: 1px solid #f0f0f0;
}
.badge-count {
  margin-left: auto; background: #f0f0f0; color: #666;
  padding: 2px 10px; border-radius: 20px; font-size: 0.75rem; font-weight: 600;
}
.tbl-header-cli, .tbl-row-cli {
  grid-template-columns: 2.5fr 1.3fr 1.3fr 1.3fr 1.3fr 0.8fr 1fr;
}
.tbl-header-rec, .tbl-row-rec {
  grid-template-columns: 1fr 2fr 1fr 1.2fr 2fr 1.2fr;
}
.tbl-header-cli, .tbl-header-rec {
  display: grid; gap: 0.4rem; padding: 0.6rem 1rem;
  background: #f5f5f5; font-size: 0.74rem; font-weight: 700;
  color: #666; text-transform: uppercase; letter-spacing: 0.3px;
}
.tbl-row-cli, .tbl-row-rec {
  display: grid; gap: 0.4rem; padding: 0.65rem 1rem;
  align-items: center; font-size: 0.86rem;
  border-top: 1px solid #f0f0f0; transition: background 0.12s;
}
.tbl-row-cli:hover, .tbl-row-rec:hover { background: #fafafa; }
.col-notas { font-size: 0.8rem; color: #777; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.date-pick { width: 150px; }
.field-lbl { font-size: 0.83rem; font-weight: 600; color: #555; white-space: nowrap; }

/* ── KPIs ──────────────────────────────────────────────────── */
.kpi-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.75rem;
}
.kpi-item {
  background: white; border-radius: 12px; padding: 1rem 1.25rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  display: flex; flex-direction: column; gap: 0.3rem;
  border-left: 5px solid transparent;
}
.kpi-total   { border-left-color: #1565C0; }
.kpi-vencido { border-left-color: #C62828; }
.kpi-parcial { border-left-color: #E65100; }
.kpi-ok      { border-left-color: #2E7D32; }

.kpi-val {
  font-size: 1.55rem; font-weight: 800; letter-spacing: -0.5px;
}
.kpi-total   .kpi-val { color: #1565C0; }
.kpi-vencido .kpi-val { color: #C62828; }
.kpi-parcial .kpi-val { color: #E65100; }
.kpi-ok      .kpi-val { color: #2E7D32; }

.kpi-lbl {
  font-size: 0.78rem; color: #888; display: flex; align-items: center; gap: 0.3rem;
}

/* ── Toolbar ────────────────────────────────────────────────── */
.toolbar-card {
  background: white; border-radius: 10px; padding: 0.9rem 1.25rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
  display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap;
}
.toolbar-left  { display: flex; gap: 0.75rem; align-items: center; flex-wrap: wrap; flex: 1; }
.toolbar-right { display: flex; gap: 0.5rem; }
.sel-estado    { width: 160px; }
.search-wrap   { flex: 1; min-width: 220px; position: relative; display: flex; align-items: center; }
.search-wrap > .pi { position: absolute; left: 0.75rem; color: #aaa; z-index: 1; pointer-events: none; }
.search-input  { width: 100%; padding-left: 2.2rem !important; }

/* ── Tabla ──────────────────────────────────────────────────── */
.tabla-card {
  background: white; border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.07); overflow: hidden;
}

.tbl-header {
  display: grid;
  grid-template-columns: 1.1fr 2.2fr 0.9fr 1.3fr 1.2fr 1.1fr 1.1fr 0.9fr 1.1fr;
  gap: 0.4rem; padding: 0.6rem 1rem; align-items: center;
  background: #f5f5f5;
  font-size: 0.74rem; font-weight: 700; color: #666;
  text-transform: uppercase; letter-spacing: 0.3px;
}
.tbl-row {
  display: grid;
  grid-template-columns: 1.1fr 2.2fr 0.9fr 1.3fr 1.2fr 1.1fr 1.1fr 0.9fr 1.1fr;
  gap: 0.4rem; padding: 0.65rem 1rem; align-items: center;
  font-size: 0.86rem; border-top: 1px solid #f0f0f0;
  transition: background 0.12s;
}
.tbl-row:hover { background: #fafafa; }

/* Colores de fila */
.row-vencido { background: #fff5f5 !important; border-left: 3px solid #C62828; }
.row-vencido:hover { background: #ffeeee !important; }
.row-urgente { background: #fff8f0 !important; border-left: 3px solid #E65100; }
.row-urgente:hover { background: #fff0e0 !important; }
.row-pagado  { background: #f0faf0 !important; opacity: 0.7; }
.row-pagado:hover { opacity: 1; }

/* Columnas */
.col-num     { font-weight: 700; color: #1565C0; font-size: 0.82rem; }
.col-cliente { display: flex; flex-direction: column; gap: 1px; }
.cli-nombre  { font-weight: 600; color: #222; font-size: 0.85rem; }
.cli-nit     { font-size: 0.75rem; color: #aaa; }
.col-fecha   { color: #666; font-size: 0.82rem; }
.col-vence   { display: flex; flex-direction: column; gap: 2px; font-size: 0.82rem; }
.col-r       { text-align: right; }
.col-center  { text-align: center; }
.col-total   { color: #333; font-weight: 600; }
.col-pagado  { color: #2E7D32; font-weight: 600; }
.col-saldo   { color: #C62828; font-weight: 700; }
.saldo-cero  { color: #2E7D32 !important; font-weight: 700; }
.col-acciones { display: flex; gap: 0.1rem; justify-content: center; }
.sin-vence   { color: #ccc; }

/* Vencimiento badges */
.dias-badge {
  display: inline-block; padding: 1px 5px; border-radius: 8px;
  font-size: 0.7rem; font-weight: 700;
}
.vence-vencido .dias-badge { background: #FFEBEE; color: #C62828; }
.vence-urgente .dias-badge { background: #FFF3E0; color: #E65100; }
.vence-proximo .dias-badge { background: #FFF9C4; color: #F57F17; }

/* Estado badge */
.estado-badge {
  padding: 2px 8px; border-radius: 10px;
  font-size: 0.72rem; font-weight: 700;
}
.estado-pendiente { background: #FFF3E0; color: #E65100; }
.estado-parcial   { background: #E3F2FD; color: #1565C0; }
.estado-pagado    { background: #E8F5E9; color: #2E7D32; }

/* Empty / loading */
.loading-state, .empty-state {
  display: flex; flex-direction: column; align-items: center; gap: 0.75rem;
  padding: 3rem; color: #aaa; font-size: 0.95rem;
}

/* ── Dialog Historial ───────────────────────────────────────── */
.historial-dialog { display: flex; flex-direction: column; gap: 1rem; }

.hist-resumen {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.75rem;
}
.hist-res-item {
  background: #f9f9f9; border-radius: 8px; padding: 0.75rem;
  display: flex; flex-direction: column; align-items: center; gap: 0.2rem;
}
.hist-res-lbl { font-size: 0.75rem; color: #888; }
.hist-res-val { font-size: 1.05rem; font-weight: 700; color: #333; }
.pagado-val   { color: #2E7D32; }
.saldo-val    { color: #C62828; }

/* Barra de progreso */
.pago-progreso-wrap { display: flex; align-items: center; gap: 0.75rem; }
.pago-progreso-bar {
  flex: 1; height: 10px; background: #e0e0e0; border-radius: 10px; overflow: hidden;
}
.pago-progreso-fill {
  height: 100%; background: linear-gradient(90deg, #2E7D32, #66BB6A);
  border-radius: 10px; transition: width 0.4s ease;
}
.pago-progreso-pct { font-size: 0.8rem; font-weight: 700; color: #2E7D32; white-space: nowrap; }

/* Notas resumen strip */
.notas-resumen { display: flex; gap: 0.75rem; flex-wrap: wrap; }
.nota-resumen-item {
  display: flex; align-items: center; gap: 0.4rem; padding: 0.35rem 0.75rem;
  border-radius: 20px; font-size: 0.82rem;
}
.nota-nc { background: #E8F5E9; color: #2E7D32; }
.nota-nd { background: #FFEBEE; color: #C62828; }

/* Abonos lista */
.sin-abonos {
  text-align: center; padding: 1rem; color: #aaa;
  font-size: 0.88rem; display: flex; align-items: center; justify-content: center; gap: 0.4rem;
}
.abonos-lista { display: flex; flex-direction: column; gap: 0.4rem; max-height: 300px; overflow-y: auto; }
.abono-item {
  display: grid; grid-template-columns: 1fr 1fr 2fr; gap: 0.5rem;
  align-items: center; padding: 0.55rem 0.75rem;
  background: #f9f9f9; border-radius: 8px; border-left: 3px solid #2E7D32;
}
.abono-item.abono-nc { background: #f0faf0; border-left-color: #43A047; }
.abono-item.abono-nd { background: #fff5f5; border-left-color: #E53935; }
.abono-fecha { font-size: 0.8rem; color: #888; }
.abono-monto { font-weight: 700; color: #2E7D32; font-size: 0.92rem; }
.monto-nc    { color: #2E7D32; }
.monto-nd    { color: #C62828; }
.abono-notas { font-size: 0.78rem; color: #777; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ── Dialog Abono ───────────────────────────────────────────── */
.abono-form { display: flex; flex-direction: column; gap: 1rem; }
.abono-info-strip {
  display: flex; justify-content: space-between; align-items: center;
  background: #f5f5f5; border-radius: 8px; padding: 0.6rem 0.9rem;
  font-size: 0.9rem;
}
.saldo-pendiente-lbl strong { color: #C62828; }
.field { display: flex; flex-direction: column; gap: 0.35rem; }
.field label { font-size: 0.85rem; font-weight: 600; color: #444; }
.dialog-footer {
  display: flex; justify-content: flex-end; gap: 0.75rem;
  padding-top: 0.75rem; border-top: 1px solid #f0f0f0; margin-top: 0.5rem;
}

@media (max-width: 900px) {
  .kpi-strip { grid-template-columns: repeat(2, 1fr); }
  .tbl-header, .tbl-row { font-size: 0.78rem; }
}
</style>
