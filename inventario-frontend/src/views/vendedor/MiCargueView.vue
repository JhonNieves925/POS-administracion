<template>
  <div class="mi-cargue">
    <div v-if="loading" class="loading-center">
      <ProgressSpinner />
      <p>Cargando tu cargue...</p>
    </div>

    <div v-else-if="!cargue" class="sin-cargue">
      <i class="pi pi-inbox" style="font-size:3rem; color:#ccc;"></i>
      <h3>Sin cargue activo</h3>
      <p>No tienes un cargue asignado para hoy. Contacta al administrador.</p>
      <Button label="Actualizar" icon="pi pi-refresh" text @click="cargarDatos" />
    </div>

    <template v-else>
      <!-- Info del cargue -->
      <div class="cargue-card">
        <div class="cargue-header">
          <div>
            <div class="cargue-numero">{{ cargue.numero }}</div>
            <div class="cargue-fecha">{{ formatFecha(cargue.fecha) }}</div>
          </div>
          <div style="display:flex;align-items:center;gap:0.5rem">
            <Tag :value="estadoLabel(cargue.estado)" :severity="estadoSeverity(cargue.estado)" style="font-size:0.85rem" />
            <Button icon="pi pi-refresh" text rounded size="small" @click="cargarDatos" :loading="refrescando" />
          </div>
        </div>

        <!-- Indicador de cargue grupal -->
        <div v-if="cargue.grupoId" class="grupo-badge">
          <i class="pi pi-users"></i>
          Cargue compartido — disponible actualizado con ventas de todo el grupo
        </div>

        <!-- Progreso en tiempo real basado en remisiones -->
        <div class="progreso-section">
          <div class="progreso-info">
            <span>Vendido: <strong>{{ totalVendidoReal }}</strong> / {{ totalCargado }} uds</span>
            <span class="progreso-pct">{{ porcentajeReal }}%</span>
          </div>
          <ProgressBar :value="porcentajeReal" style="height:10px" />
          <div class="progreso-meta">
            <span class="disp-badge">Disponible: {{ totalCargado - totalVendidoReal }} uds</span>
            <span class="remisiones-badge">{{ remisiones.length }} venta{{ remisiones.length !== 1 ? 's' : '' }} hoy</span>
          </div>
        </div>
      </div>

      <!-- Productos del cargue — con disponible en tiempo real -->
      <div class="productos-lista">
        <div class="lista-header">
          <h3>Productos en cargue</h3>
          <span class="total-valor">{{ formatCOP(valorTotalRemisiones) }}</span>
        </div>

        <div
          class="producto-item"
          v-for="det in cargue.detalles"
          :key="det.id"
          :class="{ 'agotado': disponibleProducto(det) === 0 }"
        >
          <div class="prod-nombre">{{ det.producto?.nombreCompleto }}</div>
          <div class="prod-stats">
            <div class="stat">
              <span class="stat-label">Cargado</span>
              <span class="stat-val">{{ cajasLabel(det.cantidadCargue, det) }}</span>
              <span class="stat-sub">{{ cajasStr(det.cantidadCargue, det) }}</span>
            </div>
            <div class="stat">
              <span class="stat-label">Vendido</span>
              <span class="stat-val sold">{{ cajasLabel(vendidoProducto(det), det) }}</span>
              <span class="stat-sub">{{ cajasStr(vendidoProducto(det), det) }}</span>
            </div>
            <div class="stat">
              <span class="stat-label">Disponible</span>
              <span class="stat-val" :class="disponibleProducto(det) === 0 ? 'agotado-val' : 'disp-val'">
                {{ cajasLabel(disponibleProducto(det), det) }}
              </span>
              <span class="stat-sub">{{ cajasStr(disponibleProducto(det), det) }}</span>
            </div>
            <!-- Al finalizar ruta: también mostrar devoluciones -->
            <div class="stat" v-if="cargue.estado === 'DEVUELTO' || cargue.estado === 'LIQUIDADO'">
              <span class="stat-label">Devuelto</span>
              <span class="stat-val dev">{{ cajasLabel(det.cantidadDevolucion || 0, det) }}</span>
              <span class="stat-sub">{{ cajasStr(det.cantidadDevolucion || 0, det) }}</span>
            </div>
          </div>
          <!-- Mini barra por producto -->
          <div class="prod-barra" v-if="det.cantidadCargue > 0">
            <div
              class="prod-barra-fill"
              :style="{ width: Math.min(100, (vendidoProducto(det) / det.cantidadCargue) * 100) + '%' }"
            />
          </div>
          <div class="prod-subtotal">{{ formatCOP(subtotalProducto(det)) }}</div>
        </div>
      </div>

      <!-- Acciones -->
      <div class="acciones">
        <Button
          label="Registrar nueva venta"
          icon="pi pi-plus-circle"
          class="w-full"
          @click="$router.push('/vendedor/nueva-venta')"
        />
        <Button
          label="Ir a Fin de Ruta"
          icon="pi pi-flag"
          class="w-full"
          severity="warn"
          outlined
          @click="$router.push('/vendedor/devoluciones')"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import api from '@/api/axios'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import ProgressBar from 'primevue/progressbar'
import ProgressSpinner from 'primevue/progressspinner'

const toast = useToast()
const cargue = ref(null)
const remisiones = ref([])
const grupoVendidoMap = ref({})   // mapa grupo: productoId → vendido total
const loading = ref(true)
const refrescando = ref(false)

// Si hay grupoId usa el mapa del grupo, si no calcula desde mis remisiones
const vendidoMap = computed(() => {
  if (cargue.value?.grupoId) return grupoVendidoMap.value
  const map = {}
  for (const rem of remisiones.value) {
    for (const det of (rem.detalles || [])) {
      const pid = det.producto?.id
      if (pid) map[pid] = (map[pid] || 0) + Number(det.cantidad || 0)
    }
  }
  return map
})

// Valor principal en cajas (para stat-val)
function cajasLabel(units, det) {
  const upc = det.producto?.unidadesPorCaja || 1
  if (upc <= 1) return String(units || 0)
  const cajas = Math.floor((units || 0) / upc)
  const sueltas = (units || 0) % upc
  return sueltas > 0 ? `${cajas} caj` : `${cajas} caj`
}
// Detalle en unidades para el subtexto (para stat-sub)
function cajasStr(units, det) {
  const upc = det.producto?.unidadesPorCaja || 1
  if (upc <= 1 || !units) return ''
  return `(${units} und)`
}

function vendidoProducto(det) {
  return vendidoMap.value[det.producto?.id] || 0
}
function disponibleProducto(det) {
  return Math.max(0, det.cantidadCargue - vendidoProducto(det))
}
function subtotalProducto(det) {
  const upk = det.producto?.unidadesPorCaja || 1
  const precioU = Number(det.precioCajaSnapshot || 0) / upk
  return vendidoProducto(det) * precioU
}

const totalCargado = computed(() =>
  cargue.value?.detalles?.reduce((s, d) => s + d.cantidadCargue, 0) || 0
)
const totalVendidoReal = computed(() =>
  Object.values(vendidoMap.value).reduce((s, v) => s + v, 0)
)
const porcentajeReal = computed(() =>
  totalCargado.value ? Math.round((totalVendidoReal.value / totalCargado.value) * 100) : 0
)
const valorTotalRemisiones = computed(() =>
  remisiones.value.reduce((s, r) => s + Number(r.total || 0), 0)
)

function estadoLabel(e) {
  return { CREADO: 'Creado', EN_RUTA: 'En ruta', DEVUELTO: 'Devuelto', LIQUIDADO: 'Liquidado' }[e] || e
}
function estadoSeverity(e) {
  return { CREADO: 'secondary', EN_RUTA: 'info', DEVUELTO: 'warn', LIQUIDADO: 'success' }[e] || 'secondary'
}
function formatFecha(f) { return f ? new Date(f).toLocaleDateString('es-CO') : '-' }
function formatCOP(v) {
  if (!v && v !== 0) return '-'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(v)
}

async function cargarDatos() {
  if (cargue.value) refrescando.value = true
  else loading.value = true
  try {
    const [resCargue, resRem] = await Promise.allSettled([
      api.get('/cargues/mi-cargue-hoy'),
      api.get('/remisiones/mis-remisiones')
    ])
    if (resCargue.status === 'fulfilled') cargue.value = resCargue.value.data
    else if (resCargue.reason?.response?.status === 404) cargue.value = null
    if (resRem.status === 'fulfilled') remisiones.value = resRem.value.data || []

    // Si el cargue es grupal, cargar el mapa de vendido del grupo
    if (cargue.value?.grupoId) {
      const resGrupo = await api.get(`/cargues/${cargue.value.id}/grupo-vendido`)
      grupoVendidoMap.value = resGrupo.data || {}
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo cargar el cargue', life: 4000 })
  } finally {
    loading.value = false
    refrescando.value = false
  }
}

function onVisibilityChange() {
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
.mi-cargue { display: flex; flex-direction: column; gap: 1rem; }

.loading-center {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; padding: 3rem; gap: 1rem; color: #888;
}
.sin-cargue {
  display: flex; flex-direction: column; align-items: center;
  text-align: center; gap: 0.75rem; padding: 3rem 1rem;
}
.sin-cargue h3 { color: #555; }
.sin-cargue p { color: #888; font-size: 0.9rem; }

.cargue-card {
  background: white; border-radius: 12px; padding: 1.25rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}
.cargue-header {
  display: flex; justify-content: space-between;
  align-items: flex-start; margin-bottom: 1rem;
}
.cargue-numero { font-size: 1.1rem; font-weight: 700; color: #333; }
.cargue-fecha { font-size: 0.82rem; color: #888; margin-top: 0.2rem; }

/* Progreso */
.progreso-section { display: flex; flex-direction: column; gap: 0.4rem; }
.progreso-info { display: flex; justify-content: space-between; font-size: 0.85rem; color: #666; }
.progreso-pct { font-weight: 700; color: #D32F2F; }
.progreso-meta {
  display: flex; justify-content: space-between;
  font-size: 0.75rem; margin-top: 0.1rem;
}
.disp-badge { color: #1565C0; font-weight: 600; }
.remisiones-badge { color: #888; }

/* Lista productos */
.productos-lista { display: flex; flex-direction: column; gap: 0.5rem; }
.lista-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0 0.25rem;
}
.lista-header h3 { font-size: 1rem; color: #333; margin: 0; }
.total-valor { font-size: 1rem; font-weight: 700; color: #D32F2F; }

.producto-item {
  background: white; border-radius: 10px; padding: 1rem;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.producto-item.agotado { border-left: 3px solid #E53935; }

.prod-nombre { font-size: 0.9rem; font-weight: 600; color: #333; margin-bottom: 0.6rem; }
.prod-stats { display: flex; gap: 1.25rem; flex-wrap: wrap; }
.stat { display: flex; flex-direction: column; gap: 0.15rem; }
.stat-label { font-size: 0.68rem; color: #999; text-transform: uppercase; letter-spacing: 0.3px; }
.stat-val { font-size: 1.1rem; font-weight: 700; color: #333; }
.stat-val.sold { color: #2E7D32; }
.stat-val.dev { color: #F57F17; }
.stat-val.disp-val { color: #1565C0; }
.stat-val.agotado-val { color: #E53935; }
.stat-sub { font-size: 0.7rem; color: #aaa; font-weight: 400; line-height: 1; }

.grupo-badge {
  display: flex; align-items: center; gap: 0.5rem;
  background: #e3f2fd; color: #1565C0;
  font-size: 0.78rem; padding: 0.5rem 0.75rem;
  border-radius: 8px; margin-bottom: 0.5rem;
}

/* Mini barra por producto */
.prod-barra {
  height: 4px; background: #f0f0f0; border-radius: 2px;
  margin: 0.6rem 0 0.2rem;
}
.prod-barra-fill {
  height: 100%; background: #2E7D32; border-radius: 2px;
  transition: width 0.3s;
}

.prod-subtotal { font-size: 0.85rem; color: #666; text-align: right; }

.acciones { display: flex; flex-direction: column; gap: 0.75rem; }
</style>
