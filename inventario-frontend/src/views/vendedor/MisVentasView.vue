<template>
  <div class="mis-ventas">

    <!-- Resumen del cargue actual -->
    <div class="resumen-dia" v-if="!loading">
      <div class="res-item">
        <span class="res-num">{{ remisiones.length }}</span>
        <span class="res-label">Ventas del cargue</span>
      </div>
      <div class="res-sep"></div>
      <div class="res-item">
        <span class="res-num verde">{{ formatCOP(totalDia) }}</span>
        <span class="res-label">Total facturado</span>
      </div>
    </div>

    <div v-if="loading" class="loading-center">
      <ProgressSpinner />
    </div>

    <div v-else-if="remisiones.length === 0" class="sin-ventas">
      <i class="pi pi-shopping-cart" style="font-size:2.5rem;color:#ccc" />
      <p>Aún no has registrado ventas hoy</p>
      <Button label="Registrar venta" icon="pi pi-plus" text @click="$router.push('/vendedor/nueva-venta')" />
    </div>

    <!-- Lista de remisiones -->
    <div class="remision-card" v-for="rem in remisiones" :key="rem.id">
      <div class="rem-header">
        <div>
          <div class="rem-numero">{{ rem.numero }}</div>
          <div class="rem-cliente">
            {{ rem.cliente?.razonSocial || 'Consumidor Final' }}
          </div>
        </div>
        <div class="rem-derecha">
          <Tag
            :value="estadoLabel(rem.estado)"
            :severity="estadoSeverity(rem.estado)"
            style="font-size:0.75rem"
          />
          <div class="rem-total">{{ formatCOP(rem.total) }}</div>
        </div>
      </div>

      <!-- Líneas de la venta -->
      <div class="rem-detalles" v-if="rem.detalles?.length">
        <div class="rem-det-item" v-for="det in rem.detalles" :key="det.id">
          <span class="det-nombre">{{ det.descripcionProducto }}</span>
          <span class="det-cant">× {{ det.cantidad }} uds{{ cajasLabel(det) }}</span>
          <span class="det-sub">{{ formatCOP(Number(det.precioUnitario) * Number(det.cantidad)) }}</span>
        </div>
      </div>

      <!-- Observaciones -->
      <div class="rem-obs" v-if="rem.observaciones">
        <i class="pi pi-comment" style="font-size:0.75rem" />
        {{ rem.observaciones }}
      </div>

      <!-- Acciones -->
      <div class="rem-acciones">
        <Button
          label="Descargar PDF"
          icon="pi pi-file-pdf"
          size="small"
          outlined
          severity="danger"
          :loading="descargando === rem.id"
          @click="descargarPdf(rem)"
        />
      </div>
    </div>

    <!-- Botón refrescar -->
    <Button
      label="Actualizar"
      icon="pi pi-refresh"
      text
      class="w-full"
      :loading="loading"
      @click="cargarRemisiones"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import api from '@/api/axios'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'

const toast = useToast()
const remisiones = ref([])
const loading = ref(true)
const descargando = ref(null)

const totalDia = computed(() =>
  remisiones.value.reduce((s, r) => s + Number(r.total || 0), 0)
)

function cajasLabel(det) {
  const upc = det.producto?.unidadesPorCaja || 1
  if (upc <= 1) return ''
  const cajas = Math.floor(det.cantidad / upc)
  return cajas > 0 ? ` (${cajas} caj)` : ''
}

function estadoLabel(e) {
  return { PENDIENTE: 'Pendiente', FACTURADA: 'Facturada', ANULADA: 'Anulada' }[e] || e
}
function estadoSeverity(e) {
  return { PENDIENTE: 'warn', FACTURADA: 'success', ANULADA: 'danger' }[e] || 'secondary'
}
function formatCOP(val) {
  if (!val && val !== 0) return '$0'
  return new Intl.NumberFormat('es-CO', {
    style: 'currency', currency: 'COP', maximumFractionDigits: 0
  }).format(val)
}

async function descargarPdf(rem) {
  descargando.value = rem.id
  try {
    const res = await api.get(`/remisiones/${rem.id}/pdf`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `remision_${rem.numero}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo descargar el PDF', life: 3000 })
  } finally {
    descargando.value = null
  }
}

async function cargarRemisiones() {
  loading.value = true
  try {
    const res = await api.get('/remisiones/mis-remisiones')
    remisiones.value = res.data || []
  } catch {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudieron cargar las ventas', life: 3000 })
  } finally {
    loading.value = false
  }
}

onMounted(cargarRemisiones)
</script>

<style scoped>
.mis-ventas { display: flex; flex-direction: column; gap: 1rem; }

.loading-center { display: flex; justify-content: center; padding: 3rem; }

.sin-ventas {
  display: flex; flex-direction: column; align-items: center;
  gap: 0.75rem; padding: 3rem; color: #aaa; text-align: center;
}

/* Resumen del día */
.resumen-dia {
  background: white; border-radius: 12px; padding: 1rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.06);
  display: flex; align-items: center; justify-content: space-around;
}
.res-sep { width: 1px; height: 2.5rem; background: #eee; }
.res-item { display: flex; flex-direction: column; align-items: center; gap: 0.2rem; }
.res-num { font-size: 1.5rem; font-weight: 800; color: #333; }
.res-num.verde { color: #2E7D32; }
.res-label { font-size: 0.75rem; color: #999; text-transform: uppercase; }

/* Tarjeta de remisión */
.remision-card {
  background: white; border-radius: 12px; padding: 1rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.06);
  display: flex; flex-direction: column; gap: 0.75rem;
}

.rem-header { display: flex; justify-content: space-between; align-items: flex-start; }
.rem-numero { font-weight: 700; font-size: 0.95rem; color: #333; }
.rem-cliente { font-size: 0.82rem; color: #888; margin-top: 2px; }
.rem-derecha { display: flex; flex-direction: column; align-items: flex-end; gap: 0.3rem; }
.rem-total { font-weight: 800; font-size: 1rem; color: #D32F2F; }

/* Líneas */
.rem-detalles {
  background: #fafafa; border-radius: 8px;
  padding: 0.6rem 0.75rem;
  display: flex; flex-direction: column; gap: 0.35rem;
}
.rem-det-item { display: flex; align-items: center; gap: 0.5rem; font-size: 0.82rem; }
.det-nombre { flex: 1; color: #444; }
.det-cant { color: #888; }
.det-sub { font-weight: 600; color: #333; margin-left: auto; }

.rem-obs { font-size: 0.78rem; color: #888; display: flex; gap: 0.3rem; align-items: flex-start; }
.rem-acciones { display: flex; justify-content: flex-end; }
</style>
