<template>
  <div class="devoluciones-view">
    <div v-if="loading" class="loading-center">
      <ProgressSpinner />
    </div>

    <div v-else-if="!cargue" class="sin-cargue">
      <i class="pi pi-inbox" style="font-size:2.5rem;color:#ccc;"></i>
      <p>No tienes cargue activo hoy</p>
    </div>

    <!-- Estado: ya terminó la ruta -->
    <template v-if="cargue && (cargue.estado === 'DEVUELTO' || cargue.estado === 'LIQUIDADO')">
      <div class="ruta-terminada">
        <i class="pi pi-check-circle" style="font-size:3rem;color:#2E7D32" />
        <h3>Ruta finalizada</h3>
        <p>Tus devoluciones fueron registradas. La auxiliar procesará el cierre.</p>
        <Tag :value="cargue.estado === 'LIQUIDADO' ? 'Liquidado' : 'Devuelto — pendiente cierre'" :severity="cargue.estado === 'LIQUIDADO' ? 'success' : 'warn'" />
      </div>
    </template>

    <template v-else-if="cargue">
      <div class="info-card">
        <div class="info-card-header">
          <div>
            <h3>Fin de ruta</h3>
            <p>Ingresa las unidades que traes de vuelta al almacén.</p>
          </div>
          <Tag :value="cargue.numero" severity="info" style="font-size:0.75rem" />
        </div>
        <div class="aviso-fin-ruta">
          <i class="pi pi-info-circle" />
          Lo que <strong>no devuelvas</strong> queda registrado como vendido.
        </div>
      </div>

      <div class="devolucion-lista">
        <div class="devolucion-item" v-for="det in items" :key="det.id">
          <div class="dev-prod-nombre">{{ det.producto?.nombreCompleto }}</div>
          <div class="dev-stats">
            <div class="dev-stat">
              <span>Cargado</span>
              <strong>{{ det.cantidadCargue }}</strong>
            </div>
            <div class="dev-input">
              <span>Devuelvo</span>
              <InputNumber
                v-model="det.devolucion"
                :min="0"
                :max="det.cantidadCargue"
                showButtons
                buttonLayout="horizontal"
                style="max-width:150px"
                inputStyle="text-align:center;font-size:1.1rem;width:50px"
              />
            </div>
            <div class="dev-stat">
              <span>Vendo</span>
              <strong class="vendo">{{ det.cantidadCargue - (det.devolucion || 0) }}</strong>
            </div>
          </div>
        </div>
      </div>

      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      <Message v-if="exito" severity="success" :closable="false">{{ exito }}</Message>

      <Button
        label="Confirmar y terminar ruta"
        icon="pi pi-flag-fill"
        class="w-full btn-fin-ruta"
        :loading="guardando"
        @click="confirmarDevoluciones"
      />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import api from '@/api/axios'
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'

const toast = useToast()
const cargue = ref(null)
const items = ref([])
const loading = ref(true)
const guardando = ref(false)
const error = ref('')
const exito = ref('')

async function cargarCargue() {
  loading.value = true
  try {
    const res = await api.get('/cargues/mi-cargue-hoy')
    cargue.value = res.data
    items.value = res.data.detalles.map(d => ({
      ...d,
      devolucion: d.cantidadDevolucion || 0
    }))
  } catch (e) {
    if (e.response?.status !== 404) {
      toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo cargar el cargue', life: 4000 })
    }
  } finally {
    loading.value = false
  }
}

async function confirmarDevoluciones() {
  error.value = ''
  exito.value = ''

  const payload = items.value.map(d => ({
    detalleId: d.id,
    cantidadDevolucion: d.devolucion || 0
  }))

  guardando.value = true
  try {
    await api.put(`/cargues/${cargue.value.id}/devoluciones`, payload)
    exito.value = 'Devoluciones registradas exitosamente'
    cargarCargue()
  } catch (e) {
    error.value = e.response?.data?.mensaje || 'Error al registrar devoluciones'
  } finally {
    guardando.value = false
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') cargarCargue()
}

onMounted(() => {
  cargarCargue()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped>
.devoluciones-view { display: flex; flex-direction: column; gap: 1rem; }
.loading-center { display: flex; justify-content: center; align-items: center; padding: 3rem; }
.sin-cargue { display: flex; flex-direction: column; align-items: center; gap: 0.5rem; padding: 3rem; color: #888; }

.info-card {
  background: white; border-radius: 12px; padding: 1rem;
  box-shadow: 0 2px 6px rgba(0,0,0,0.06);
}
.info-card h3 { margin: 0 0 0.25rem; color: #333; }
.info-card p { font-size: 0.85rem; color: #666; margin-bottom: 0.5rem; }

.devolucion-lista { display: flex; flex-direction: column; gap: 0.75rem; }

.devolucion-item {
  background: white; border-radius: 10px; padding: 1rem;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.dev-prod-nombre { font-weight: 600; color: #333; margin-bottom: 0.75rem; }
.dev-stats { display: flex; justify-content: space-around; align-items: center; }
.dev-stat { display: flex; flex-direction: column; align-items: center; gap: 0.2rem; font-size: 0.82rem; color: #888; }
.dev-stat strong { font-size: 1.2rem; color: #333; }
.dev-input { display: flex; flex-direction: column; align-items: center; gap: 0.2rem; font-size: 0.82rem; color: #888; }
.vendo { color: #2E7D32 !important; }

.ruta-terminada {
  display: flex; flex-direction: column; align-items: center;
  gap: 0.75rem; padding: 3rem 1rem; text-align: center;
  background: white; border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.ruta-terminada h3 { color: #2E7D32; margin: 0; }
.ruta-terminada p { color: #666; font-size: 0.9rem; margin: 0; }

.info-card-header { display: flex; justify-content: space-between; align-items: flex-start; }
.aviso-fin-ruta {
  display: flex; align-items: center; gap: 0.5rem;
  background: #FFF8E1; border-radius: 8px; padding: 0.6rem 0.75rem;
  font-size: 0.82rem; color: #6D4C00; margin-top: 0.25rem;
}
.btn-fin-ruta { background: #1B5E20 !important; border-color: #1B5E20 !important; }
</style>
