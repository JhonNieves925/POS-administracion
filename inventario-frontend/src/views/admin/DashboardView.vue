<template>
  <div class="dashboard">
    <!-- KPI Cards -->
    <div class="kpi-grid">
      <div class="kpi-card" v-for="kpi in kpis" :key="kpi.label" :style="{}">
        <div class="kpi-icon" :style="{ background: kpi.color }">
          <i :class="kpi.icon"></i>
        </div>
        <div class="kpi-info">
          <div class="kpi-value">{{ kpi.value }}</div>
          <div class="kpi-label">{{ kpi.label }}</div>
        </div>
      </div>
    </div>

    <!-- Cargues activos del dia -->
    <div class="section-card">
      <div class="section-header">
        <h3><i class="pi pi-truck"></i> Cargues de Hoy</h3>
        <Button label="Nuevo cargue" icon="pi pi-plus" size="small" @click="$router.push('/cargues')" />
      </div>
      <div v-if="loadingCargues" class="loading-state">
        <ProgressSpinner style="width:40px;height:40px" />
      </div>
      <DataTable v-else :value="carguesHoy" size="small" stripedRows :rows="10">
        <Column field="numero" header="Numero" style="width:130px" />
        <Column header="Vendedor">
          <template #body="{ data }">{{ data.vendedor?.nombre }}</template>
        </Column>
        <Column header="Estado">
          <template #body="{ data }">
            <Tag :value="data.estado" :severity="getSeverity(data.estado)" />
          </template>
        </Column>
        <Column header="Total productos">
          <template #body="{ data }">
            {{ data.detalles?.reduce((s, d) => s + d.cantidadCargue, 0) }} uds
          </template>
        </Column>
        <Column header="Acciones">
          <template #body="{ data }">
            <Button icon="pi pi-eye" text rounded size="small" @click="$router.push('/cargues/' + data.id)" />
          </template>
        </Column>
        <template #empty>No hay cargues para hoy</template>
      </DataTable>
    </div>

    <!-- Alertas de stock bajo -->
    <div class="section-card" v-if="stockBajo.length > 0">
      <div class="section-header">
        <h3><i class="pi pi-exclamation-triangle" style="color:#F57F17"></i> Alerta: Stock Bajo</h3>
      </div>
      <DataTable :value="stockBajo" size="small" stripedRows>
        <Column field="nombreCompleto" header="Producto" />
        <Column field="stockActual" header="Unidades disponibles" />
        <Column field="stockMinimo" header="Stock minimo" />
        <template #empty>Sin alertas de stock</template>
      </DataTable>
    </div>

    <!-- ═══ Alerta Cuentas por Cobrar ══════════════════════════ -->
    <div v-if="alertasCredito.hayAlertas" class="alerta-credito-card" @click="$router.push('/estado-cuenta')">
      <div class="alerta-credito-icono">
        <i class="pi pi-wallet" />
      </div>
      <div class="alerta-credito-body">
        <h3 class="alerta-credito-titulo">
          <i class="pi pi-exclamation-circle" />
          Cuentas por Cobrar — Atención requerida
        </h3>
        <div class="alerta-credito-items">
          <div v-if="alertasCredito.vencidas > 0" class="alerta-item alerta-item-rojo">
            <i class="pi pi-times-circle" />
            <span><strong>{{ alertasCredito.vencidas }}</strong> crédito{{ alertasCredito.vencidas !== 1 ? 's' : '' }}
              VENCIDO{{ alertasCredito.vencidas !== 1 ? 'S' : '' }} —
              {{ formatCOP(alertasCredito.totalVencido) }} sin cobrar</span>
          </div>
          <div v-if="alertasCredito.vencenHoy > 0" class="alerta-item alerta-item-naranja">
            <i class="pi pi-clock" />
            <span><strong>{{ alertasCredito.vencenHoy }}</strong> crédito{{ alertasCredito.vencenHoy !== 1 ? 's' : '' }}
              vence{{ alertasCredito.vencenHoy === 1 ? '' : 'n' }} <strong>HOY</strong></span>
          </div>
          <div v-if="alertasCredito.porVencer > 0 && alertasCredito.vencenHoy === 0" class="alerta-item alerta-item-amarillo">
            <i class="pi pi-calendar" />
            <span><strong>{{ alertasCredito.porVencer }}</strong> crédito{{ alertasCredito.porVencer !== 1 ? 's' : '' }}
              vence{{ alertasCredito.porVencer === 1 ? '' : 'n' }} en los próximos 3 días</span>
          </div>
          <div class="alerta-item alerta-item-total">
            <i class="pi pi-dollar" />
            <span>Total pendiente por cobrar: <strong>{{ formatCOP(alertasCredito.totalPendiente) }}</strong></span>
          </div>
        </div>
      </div>
      <div class="alerta-credito-accion">
        <span>Ver estado de cuenta</span>
        <i class="pi pi-arrow-right" />
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import api from '@/api/axios'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import ProgressSpinner from 'primevue/progressspinner'

const carguesHoy = ref([])
const inventario = ref([])
const loadingCargues = ref(true)

// ── Alertas crédito ───────────────────────────────────────────
const alertasCredito = ref({
  hayAlertas: false, vencidas: 0, vencenHoy: 0, porVencer: 0,
  totalPendiente: 0, totalVencido: 0
})

const stockBajo = computed(() =>
  inventario.value.filter(p => p.stockActual <= (p.stockMinimo || 0))
)

const kpis = computed(() => [
  {
    label: 'Cargues hoy',
    value: carguesHoy.value.length,
    icon: 'pi pi-truck',
    color: '#D32F2F'
  },
  {
    label: 'En ruta',
    value: carguesHoy.value.filter(c => c.estado === 'EN_RUTA').length,
    icon: 'pi pi-map-marker',
    color: '#1565C0'
  },
  {
    label: 'Liquidados hoy',
    value: carguesHoy.value.filter(c => c.estado === 'LIQUIDADO').length,
    icon: 'pi pi-check-circle',
    color: '#2E7D32'
  },
  {
    label: 'Productos con stock bajo',
    value: stockBajo.value.length,
    icon: 'pi pi-exclamation-triangle',
    color: '#F57F17'
  }
])

function getSeverity(estado) {
  const map = {
    CREADO: 'secondary',
    EN_RUTA: 'info',
    DEVUELTO: 'warn',
    LIQUIDADO: 'success'
  }
  return map[estado] || 'secondary'
}

function formatCOP(v) {
  if (!v && v !== 0) return '—'
  return new Intl.NumberFormat('es-CO', {
    style: 'currency', currency: 'COP', maximumFractionDigits: 0
  }).format(v)
}

async function cargarDatos() {
  try {
    const [resCargues, resInv, resAlertas] = await Promise.allSettled([
      api.get('/cargues/hoy'),
      api.get('/inventario'),
      api.get('/estado-cuenta/alertas', { params: { dias: 3 } })
    ])
    if (resCargues.status === 'fulfilled') carguesHoy.value  = resCargues.value.data
    if (resInv.status     === 'fulfilled') inventario.value  = resInv.value.data
    if (resAlertas.status === 'fulfilled') alertasCredito.value = resAlertas.value.data || {}
  } catch (e) {
    console.error('Error cargando dashboard', e)
  } finally {
    loadingCargues.value = false
  }
}

onMounted(cargarDatos)
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.kpi-card {
  background: white;
  border-radius: 14px;
  padding: 1.35rem 1.5rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06), 0 4px 14px rgba(0,0,0,0.04);
  border: 1px solid #e5e7eb;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  cursor: default;
}

.kpi-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 20px rgba(0,0,0,0.1), 0 2px 8px rgba(0,0,0,0.06);
}

.kpi-icon {
  width: 50px;
  height: 50px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.3rem;
  flex-shrink: 0;
  opacity: 0.92;
}

.kpi-value {
  font-size: 2rem;
  font-weight: 800;
  color: #111827;
  line-height: 1;
  letter-spacing: -0.02em;
}

.kpi-label {
  font-size: 0.8rem;
  color: #6b7280;
  margin-top: 0.3rem;
  font-weight: 500;
}

.section-card {
  background: white;
  border-radius: 14px;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06), 0 4px 14px rgba(0,0,0,0.04);
  border: 1px solid #e5e7eb;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.1rem;
  padding-bottom: 0.85rem;
  border-bottom: 1px solid #f3f4f6;
}

.section-header h3 {
  font-size: 0.95rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #111827;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 2.5rem;
}

/* ── Widget Alertas Cuentas por Cobrar ──────────────────────── */
.alerta-credito-card {
  background: linear-gradient(135deg, #b71c1c 0%, #d32f2f 60%, #e53935 100%);
  border-radius: 14px;
  padding: 1.25rem 1.5rem;
  display: flex;
  align-items: center;
  gap: 1.25rem;
  box-shadow: 0 4px 18px rgba(198, 40, 40, 0.35);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  animation: pulseBorder 2.5s infinite;
}
.alerta-credito-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(198, 40, 40, 0.45);
}
@keyframes pulseBorder {
  0%, 100% { box-shadow: 0 4px 18px rgba(198,40,40,0.35); }
  50%       { box-shadow: 0 4px 28px rgba(198,40,40,0.6); }
}

.alerta-credito-icono {
  width: 56px; height: 56px; flex-shrink: 0;
  background: rgba(255,255,255,0.18); border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  color: white; font-size: 1.8rem;
}
.alerta-credito-body { flex: 1; display: flex; flex-direction: column; gap: 0.5rem; }
.alerta-credito-titulo {
  font-size: 1rem; font-weight: 700; color: white; margin: 0;
  display: flex; align-items: center; gap: 0.5rem;
}
.alerta-credito-items { display: flex; flex-direction: column; gap: 0.3rem; }
.alerta-item {
  display: flex; align-items: center; gap: 0.5rem;
  font-size: 0.87rem; color: rgba(255,255,255,0.92);
}
.alerta-item i { font-size: 0.9rem; }
.alerta-item-rojo    i { color: #FFCDD2; }
.alerta-item-naranja i { color: #FFE082; }
.alerta-item-amarillo i { color: #FFF9C4; }
.alerta-item-total   { margin-top: 0.2rem; font-weight: 600; color: white; }
.alerta-item strong  { color: #FFD54F; }

.alerta-credito-accion {
  display: flex; flex-direction: column; align-items: center; gap: 0.25rem;
  color: rgba(255,255,255,0.8); font-size: 0.78rem; white-space: nowrap;
  flex-shrink: 0;
}
.alerta-credito-accion i { font-size: 1.1rem; color: white; }
</style>
