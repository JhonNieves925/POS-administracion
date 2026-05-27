<template>
  <div class="productos-page">
    <div class="page-toolbar">
      <div class="toolbar-left">
        <div class="search-wrapper">
          <i class="pi pi-search search-icon" />
          <InputText v-model="filtro" placeholder="Buscar producto..." class="search-input" />
        </div>
      </div>
      <div class="toolbar-right">
        <Button
          label="Importar desde Siigo"
          icon="pi pi-list"
          outlined
          severity="secondary"
          @click="abrirCatalogo"
          v-tooltip.top="'Ver catálogo de Siigo e importar productos'"
        />
        <Button
          label="Sincronizar todos"
          icon="pi pi-refresh"
          outlined
          severity="secondary"
          @click="confirmarSync"
          :loading="syncLoading"
          v-tooltip.top="'Importar todo el catálogo desde Siigo'"
        />
        <Button label="Nuevo producto" icon="pi pi-plus" @click="abrirNuevo" />
      </div>
    </div>

    <div class="card-table">
      <DataTable
        :value="productosFiltrados"
        :loading="loading"
        stripedRows
        paginator
        :rows="15"
        paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
        :rowsPerPageOptions="[10, 15, 25, 50]"
        responsiveLayout="scroll"
      >
        <Column field="nombreCompleto" header="Producto" sortable />
        <Column field="codigo" header="Cod. barras" sortable />
        <Column field="marca" header="Marca" sortable />
        <Column header="Tamano">
          <template #body="{ data }">{{ data.tamanoMl }} {{ data.unidadMedida }}</template>
        </Column>
        <Column field="presentacion" header="Presentacion" />
        <Column header="Por caja">
          <template #body="{ data }">{{ data.unidadesPorCaja }} uds</template>
        </Column>
        <Column header="Precio caja">
          <template #body="{ data }">{{ formatCOP(data.precioCaja) }}</template>
        </Column>
        <Column header="Precio unidad">
          <template #body="{ data }">{{ formatCOP(data.precioUnidad) }}</template>
        </Column>
        <Column field="tipoIva" header="IVA" />
        <Column header="Acciones" style="width:100px">
          <template #body="{ data }">
            <Button icon="pi pi-pencil" text rounded size="small" @click="editarProducto(data)" />
            <Button icon="pi pi-trash" text rounded size="small" severity="danger" @click="confirmarEliminar(data)" />
          </template>
        </Column>
        <template #empty>No hay productos registrados</template>
      </DataTable>
    </div>

    <!-- Dialog resultado sincronización Siigo -->
    <Dialog
      v-model:visible="syncDialogVisible"
      header="Sincronización con Siigo"
      modal
      :style="{ width: '520px' }"
      :draggable="false"
    >
      <div v-if="syncResult" class="sync-result">

        <!-- KPIs -->
        <div class="sync-kpi-grid">
          <div class="sync-kpi sync-kpi-act">
            <span class="sync-kpi-val">{{ syncResult.actualizados }}</span>
            <span class="sync-kpi-lbl">Actualizados</span>
          </div>
          <div class="sync-kpi sync-kpi-new">
            <span class="sync-kpi-val">{{ syncResult.creados }}</span>
            <span class="sync-kpi-lbl">Creados nuevos</span>
          </div>
          <div class="sync-kpi sync-kpi-ok">
            <span class="sync-kpi-val">{{ syncResult.sinCambios }}</span>
            <span class="sync-kpi-lbl">Sin cambios</span>
          </div>
          <div class="sync-kpi sync-kpi-err" v-if="syncResult.errores > 0">
            <span class="sync-kpi-val">{{ syncResult.errores }}</span>
            <span class="sync-kpi-lbl">Errores</span>
          </div>
        </div>

        <!-- Productos nuevos creados -->
        <div v-if="syncResult.productosCreados?.length" class="sync-creados">
          <div class="sync-section-title">
            <i class="pi pi-check-circle" style="color:#2E7D32" />
            <span>Productos nuevos creados y activos</span>
          </div>
          <div class="sync-hint">
            Estos productos se crearon como <strong>activos</strong> y ya están disponibles.
            Recuerda editarlos para completar: unidades por caja, precio de compra y presentación.
          </div>
          <ul class="sync-list">
            <li v-for="(nombre, i) in syncResult.productosCreados" :key="i">{{ nombre }}</li>
          </ul>
        </div>

        <!-- Errores -->
        <div v-if="syncResult.mensajesError?.length" class="sync-errores">
          <div class="sync-section-title">
            <i class="pi pi-times-circle" style="color:#C62828" />
            <span>Errores al procesar</span>
          </div>
          <ul class="sync-list sync-list-error">
            <li v-for="(msg, i) in syncResult.mensajesError" :key="i">{{ msg }}</li>
          </ul>
        </div>

        <!-- Modo mock -->
        <div v-if="syncResult.actualizados === 0 && syncResult.creados === 0 && syncResult.sinCambios === 0 && syncResult.errores === 0" class="sync-empty">
          <i class="pi pi-info-circle" />
          Siigo devolvió lista vacía. Si estás en modo desarrollo (mock), esto es normal.
        </div>
      </div>

      <template #footer>
        <Button label="Cerrar" @click="syncDialogVisible = false" />
      </template>
    </Dialog>

    <!-- Dialog Catálogo Siigo — importar uno a uno -->
    <Dialog
      v-model:visible="catalogoDialogVisible"
      header="Catálogo de productos en Siigo"
      modal
      :style="{ width: '780px', maxHeight: '90vh' }"
      :draggable="false"
      @hide="cerrarCatalogo"
    >
      <!-- Cargando -->
      <div v-if="catalogoCargando" class="catalogo-loading">
        <i class="pi pi-spin pi-spinner" style="font-size:2rem; color:#1565C0" />
        <span>Cargando productos desde Siigo...</span>
      </div>

      <!-- Error carga -->
      <div v-else-if="catalogoError" class="catalogo-error">
        <i class="pi pi-exclamation-circle" />
        <span>{{ catalogoError }}</span>
      </div>

      <!-- Tabla catálogo -->
      <div v-else class="catalogo-body">
        <!-- Buscador -->
        <span class="p-input-icon-left catalogo-buscar">
          <i class="pi pi-search" />
          <InputText v-model="catalogoFiltro" placeholder="Buscar por nombre o código..." class="w-full" />
        </span>

        <!-- Contador -->
        <div class="catalogo-contador">
          <span>{{ catalogoFiltrado.length }} de {{ catalogoProductos.length }} productos</span>
          <span class="catalogo-leyenda">
            <span class="badge-ya">✓ Ya en sistema</span>
            <span class="badge-nuevo">● Nuevo</span>
          </span>
        </div>

        <DataTable
          :value="catalogoFiltrado"
          :rows="12"
          paginator
          paginatorTemplate="PrevPageLink PageLinks NextPageLink"
          scrollable
          scrollHeight="380px"
          stripedRows
          size="small"
        >
          <Column header="Código" field="codigo" style="width:160px" />
          <Column header="Nombre" field="nombre" style="min-width:240px" />
          <Column header="IVA" style="width:80px">
            <template #body="{ data }">
              {{ data.porcentajeIva > 0 ? data.porcentajeIva + '%' : 'Excluido' }}
            </template>
          </Column>
          <Column header="Estado" style="width:130px">
            <template #body="{ data }">
              <span v-if="data.yaEnSistema" class="catalogo-badge ya">
                <i class="pi pi-check" /> Ya en sistema
              </span>
              <span v-else class="catalogo-badge nuevo">
                Nuevo
              </span>
            </template>
          </Column>
          <Column header="" style="width:110px">
            <template #body="{ data }">
              <Button
                :label="importandoCodigo === data.codigo ? '' : data.yaEnSistema ? 'Actualizar' : 'Importar'"
                :icon="importandoCodigo === data.codigo ? 'pi pi-spin pi-spinner' : data.yaEnSistema ? 'pi pi-refresh' : 'pi pi-download'"
                :severity="data.yaEnSistema ? 'secondary' : 'success'"
                size="small"
                outlined
                :loading="importandoCodigo === data.codigo"
                :disabled="!!importandoCodigo"
                @click="importarUno(data)"
              />
            </template>
          </Column>
        </DataTable>

        <!-- Toast inline de resultado -->
        <div v-if="importResult" :class="['import-inline', importResult.ok ? 'import-ok' : 'import-err']">
          <i :class="importResult.ok ? 'pi pi-check-circle' : 'pi pi-times-circle'" />
          <span>{{ importResult.mensaje }}</span>
        </div>
      </div>

      <template #footer>
        <Button label="Cerrar" text @click="cerrarCatalogo" />
      </template>
    </Dialog>

    <!-- Dialog Nuevo/Editar -->
    <Dialog v-model:visible="dialogVisible" :header="editando ? 'Editar Producto' : 'Nuevo Producto'" modal :style="{width: '560px'}" :draggable="false">
      <form @submit.prevent="guardarProducto" class="producto-form">
        <div class="form-row">
          <div class="field">
            <label>Marca *</label>
            <InputText v-model="form.marca" required class="w-full" placeholder="Ej: Coca-Cola" />
          </div>
          <div class="field">
            <label>Sabor *</label>
            <InputText v-model="form.sabor" required class="w-full" placeholder="Ej: Original" />
          </div>
        </div>
        <div class="form-row">
          <div class="field">
            <label>Tamano (ml/cc/L) *</label>
            <InputText v-model="form.tamanoMl" class="w-full" placeholder="Ej: 600, 1.5, 1/2, 3.5" />
          </div>
          <div class="field">
            <label>Unidad de medida *</label>
            <Select v-model="form.unidadMedida" :options="['ml', 'cc', 'L', 'oz']" class="w-full" />
          </div>
        </div>
        <div class="form-row">
          <div class="field">
            <label>Presentacion *</label>
            <Select v-model="form.presentacion" :options="presentaciones" class="w-full" required />
          </div>
          <div class="field">
            <label>Unidades por caja *</label>
            <InputNumber v-model="form.unidadesPorCaja" class="w-full" :min="1" required />
          </div>
        </div>
        <div class="form-row">
          <div class="field">
            <label>Precio de venta por caja (COP) *</label>
            <InputNumber v-model="form.precioCaja" class="w-full" :min="0" mode="currency" currency="COP" locale="es-CO" required />
            <small class="hint">Precio sugerido de venta al cliente</small>
          </div>
          <div class="field">
            <label>Precio de compra / proveedor (COP)</label>
            <InputNumber v-model="form.precioCompra" class="w-full" :min="0" mode="currency" currency="COP" locale="es-CO" />
            <small class="hint">Precio mínimo — el vendedor no puede vender por debajo</small>
          </div>
        </div>
        <div class="form-row">
          <div class="field">
            <label>Tipo IVA *</label>
            <Select v-model="form.tipoIva" :options="tiposIva" optionLabel="label" optionValue="value" class="w-full" required />
          </div>
          <div class="field">
            <label>IBUA — valor por unidad (COP)</label>
            <InputNumber v-model="form.valorIbuaUnidad" class="w-full" :min="0" mode="currency" currency="COP" locale="es-CO" />
            <small class="hint">Bebidas azucaradas. 0 si no aplica</small>
          </div>
        </div>
        <div class="field">
          <label>Codigo de barras</label>
          <InputText v-model="form.codigo" class="w-full" placeholder="Opcional" />
        </div>
        <div class="field">
          <label>Stock minimo (cajas)</label>
          <InputNumber v-model="form.stockMinimo" class="w-full" :min="0" />
        </div>

        <Message v-if="formError" severity="error" :closable="false">{{ formError }}</Message>

        <div class="dialog-footer">
          <Button label="Cancelar" text @click="dialogVisible = false" />
          <Button type="submit" :label="editando ? 'Guardar cambios' : 'Crear producto'" icon="pi pi-save" :loading="guardando" />
        </div>
      </form>
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
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'

const toast = useToast()
const confirm = useConfirm()

const productos = ref([])
const loading = ref(true)

// ── Sincronización Siigo (todos) ─────────────────────────────
const syncLoading = ref(false)
const syncDialogVisible = ref(false)
const syncResult = ref(null)

// ── Catálogo Siigo (importar uno a uno) ──────────────────────
const catalogoDialogVisible = ref(false)
const catalogoCargando = ref(false)
const catalogoError = ref('')
const catalogoProductos = ref([])
const catalogoFiltro = ref('')
const importandoCodigo = ref('')   // código del producto que se está importando ahora
const importResult = ref(null)     // { ok, mensaje } — resultado inline último import

const catalogoFiltrado = computed(() => {
  if (!catalogoFiltro.value) return catalogoProductos.value
  const f = catalogoFiltro.value.toLowerCase()
  return catalogoProductos.value.filter(p =>
    p.nombre?.toLowerCase().includes(f) ||
    p.codigo?.toLowerCase().includes(f)
  )
})
const filtro = ref('')
const dialogVisible = ref(false)
const editando = ref(false)
const guardando = ref(false)
const formError = ref('')

const presentaciones = ['PET', 'VIR', 'NR', 'LAT', 'TETRA', 'SA', 'BIDON']
const tiposIva = [
  { label: 'Excluido', value: 'EXCLUIDO' },
  { label: 'Exento', value: 'EXENTO' },
  { label: 'IVA 5%', value: 'IVA_5' },
  { label: 'IVA 19%', value: 'IVA_19' }
]

const formInicial = () => ({
  id: null, marca: '', sabor: '', tamanoMl: null, unidadMedida: 'ml',
  presentacion: 'PET', unidadesPorCaja: null,
  precioCaja: null, precioCompra: null,
  tipoIva: 'EXCLUIDO', valorIbuaUnidad: 0,
  codigo: '', stockMinimo: 5
})

const form = ref(formInicial())

const productosFiltrados = computed(() => {
  if (!filtro.value) return productos.value
  const f = filtro.value.toLowerCase()
  return productos.value.filter(p =>
    p.nombreCompleto?.toLowerCase().includes(f) ||
    p.marca?.toLowerCase().includes(f) ||
    p.sabor?.toLowerCase().includes(f) ||
    p.codigo?.toLowerCase().includes(f)
  )
})

function formatCOP(val) {
  if (!val && val !== 0) return '-'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(val)
}

function abrirNuevo() {
  form.value = formInicial()
  editando.value = false
  formError.value = ''
  dialogVisible.value = true
}

function editarProducto(p) {
  form.value = { ...p }
  editando.value = true
  formError.value = ''
  dialogVisible.value = true
}

async function guardarProducto() {
  formError.value = ''
  guardando.value = true
  try {
    if (editando.value) {
      await api.put(`/productos/${form.value.id}`, form.value)
      toast.add({ severity: 'success', summary: 'Actualizado', detail: 'Producto actualizado correctamente', life: 3000 })
    } else {
      await api.post('/productos', form.value)
      toast.add({ severity: 'success', summary: 'Creado', detail: 'Producto creado correctamente', life: 3000 })
    }
    dialogVisible.value = false
    cargarProductos()
  } catch (e) {
    formError.value = e.response?.data?.mensaje || 'Error al guardar'
  } finally {
    guardando.value = false
  }
}

function confirmarEliminar(p) {
  confirm.require({
    message: `Eliminar "${p.nombreCompleto}"? Esta accion no se puede deshacer.`,
    header: 'Confirmar eliminacion',
    icon: 'pi pi-exclamation-triangle',
    acceptSeverity: 'danger',
    acceptLabel: 'Eliminar',
    rejectLabel: 'Cancelar',
    accept: async () => {
      try {
        await api.delete(`/productos/${p.id}`)
        toast.add({ severity: 'success', summary: 'Eliminado', detail: 'Producto eliminado', life: 3000 })
        cargarProductos()
      } catch (e) {
        toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'No se pudo eliminar', life: 4000 })
      }
    }
  })
}

async function abrirCatalogo() {
  catalogoDialogVisible.value = true
  catalogoCargando.value = true
  catalogoError.value = ''
  catalogoProductos.value = []
  catalogoFiltro.value = ''
  importResult.value = null
  try {
    const res = await api.get('/siigo/productos')
    // La respuesta viene envuelta en ApiResponse { data: [...] }
    catalogoProductos.value = res.data.data ?? res.data
  } catch (e) {
    catalogoError.value = e.response?.data?.mensaje || 'No se pudo cargar el catálogo de Siigo'
  } finally {
    catalogoCargando.value = false
  }
}

function cerrarCatalogo() {
  catalogoDialogVisible.value = false
  catalogoFiltro.value = ''
  importResult.value = null
}

async function importarUno(producto) {
  importandoCodigo.value = producto.codigo
  importResult.value = null
  try {
    const res = await api.post(`/siigo/sincronizar-producto?codigo=${encodeURIComponent(producto.codigo)}`)
    const r = res.data.data ?? res.data
    const ok = r.errores === 0
    let mensaje
    if (!ok) {
      mensaje = r.mensajesError?.[0] ?? 'Error al importar'
    } else if (r.creados > 0) {
      mensaje = `"${producto.nombre}" importado y activo.`
    } else if (r.actualizados > 0) {
      mensaje = `"${producto.nombre}" actualizado correctamente.`
    } else {
      mensaje = `"${producto.nombre}" ya estaba sincronizado.`
    }
    importResult.value = { ok, mensaje }

    // Actualizar flag yaEnSistema en la lista local para reflejar el cambio
    if (ok && (r.creados > 0 || r.actualizados > 0)) {
      const idx = catalogoProductos.value.findIndex(p => p.codigo === producto.codigo)
      if (idx !== -1) catalogoProductos.value[idx].yaEnSistema = true
      await cargarProductos()
    }
  } catch (e) {
    importResult.value = {
      ok: false,
      mensaje: e.response?.data?.mensaje || 'Error al conectar con el servidor'
    }
  } finally {
    importandoCodigo.value = ''
  }
}

function confirmarSync() {
  confirm.require({
    message: 'Se conectará con Siigo y actualizará el catálogo completo de productos.\n' +
             'Productos existentes: se actualiza siigoCodigo e IVA si cambiaron.\n' +
             'Productos nuevos: se crean activos (completa precio y detalles luego).\n\n' +
             '¿Continuar?',
    header: 'Sincronizar todos desde Siigo',
    icon: 'pi pi-refresh',
    acceptLabel: 'Sincronizar',
    rejectLabel: 'Cancelar',
    accept: ejecutarSync
  })
}

async function ejecutarSync() {
  syncLoading.value = true
  syncResult.value = null
  try {
    const res = await api.post('/siigo/sincronizar-productos')
    syncResult.value = res.data
    syncDialogVisible.value = true
    // Recargar lista para ver los nuevos productos
    await cargarProductos()
  } catch (e) {
    toast.add({
      severity: 'error',
      summary: 'Error de sincronización',
      detail: e.response?.data?.mensaje || 'No se pudo sincronizar con Siigo',
      life: 6000
    })
  } finally {
    syncLoading.value = false
  }
}

async function cargarProductos() {
  loading.value = true
  try {
    const res = await api.get('/productos')
    productos.value = res.data
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudieron cargar los productos', life: 4000 })
  } finally {
    loading.value = false
  }
}

onMounted(cargarProductos)
</script>

<style scoped>
.productos-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.page-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  padding: 0.85rem 1.25rem;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  gap: 1rem;
  flex-wrap: wrap;
}

.toolbar-left {
  flex: 0 0 auto;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  flex-wrap: wrap;
}

/* ── Barra de búsqueda ── */
.search-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 0.9rem;
  color: #9ca3af;
  font-size: 0.88rem;
  pointer-events: none;
  z-index: 1;
}

:deep(.search-input) {
  padding-left: 2.4rem !important;
  padding-right: 1rem !important;
  height: 40px;
  width: 280px;
  border-radius: 99px !important;
  border-color: #e5e7eb !important;
  background: #f9fafb !important;
  font-size: 0.875rem !important;
  transition: width 0.25s ease, border-color 0.2s, box-shadow 0.2s, background 0.2s !important;
}

:deep(.search-input:focus) {
  border-color: var(--color-primary) !important;
  background: #ffffff !important;
  box-shadow: 0 0 0 3px rgba(211,47,47,0.1) !important;
  width: 320px !important;
  outline: none !important;
}

:deep(.search-input::placeholder) {
  color: #b0b8c4 !important;
  font-size: 0.85rem !important;
}

.card-table {
  background: white;
  border-radius: 14px;
  padding: 1rem;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.producto-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field label {
  font-size: 0.85rem;
  font-weight: 600;
  color: #444;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding-top: 0.5rem;
  border-top: 1px solid #f0f0f0;
  margin-top: 0.5rem;
}

/* ── Sincronización Siigo ───────────────────────────────────── */
.sync-result { display: flex; flex-direction: column; gap: 1.25rem; }

.sync-kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 0.75rem;
}
.sync-kpi {
  background: #f8f9fa;
  border-radius: 10px;
  padding: 0.9rem 0.75rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  border-top: 3px solid #ddd;
}
.sync-kpi-val { font-size: 1.8rem; font-weight: 800; line-height: 1; }
.sync-kpi-lbl { font-size: 0.72rem; color: #888; font-weight: 600; text-transform: uppercase; text-align: center; }

.sync-kpi-act { border-top-color: #1565C0; }
.sync-kpi-act .sync-kpi-val { color: #1565C0; }
.sync-kpi-new { border-top-color: #E65100; }
.sync-kpi-new .sync-kpi-val { color: #E65100; }
.sync-kpi-ok  { border-top-color: #2E7D32; }
.sync-kpi-ok  .sync-kpi-val { color: #2E7D32; }
.sync-kpi-err { border-top-color: #C62828; }
.sync-kpi-err .sync-kpi-val { color: #C62828; }

.sync-section-title {
  display: flex; align-items: center; gap: 0.5rem;
  font-weight: 700; font-size: 0.88rem; color: #444;
  margin-bottom: 0.4rem;
}

.sync-hint {
  background: #FFF8E1;
  border-left: 3px solid #F9A825;
  border-radius: 0 6px 6px 0;
  padding: 0.6rem 0.8rem;
  font-size: 0.82rem;
  color: #5D4037;
  margin-bottom: 0.5rem;
}

.sync-list {
  margin: 0; padding-left: 1.25rem;
  font-size: 0.83rem; color: #555;
  max-height: 180px; overflow-y: auto;
  line-height: 1.7;
}
.sync-list-error { color: #C62828; }

.sync-empty {
  display: flex; align-items: center; gap: 0.5rem;
  background: #E3F2FD; border-radius: 8px;
  padding: 0.9rem 1rem; font-size: 0.85rem; color: #1565C0;
}

/* ── Catálogo Siigo ──────────────────────────────────────────── */
.catalogo-loading,
.catalogo-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 3rem 1rem;
  font-size: 1rem;
  color: #555;
}
.catalogo-error { color: #C62828; }

.catalogo-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.catalogo-buscar {
  display: block;
}

.catalogo-contador {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.82rem;
  color: #777;
  padding: 0 0.1rem;
}

.catalogo-leyenda {
  display: flex;
  gap: 1rem;
}

.badge-ya {
  color: #1565C0;
  font-weight: 600;
}
.badge-nuevo {
  color: #E65100;
  font-weight: 600;
}

.catalogo-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.2rem 0.55rem;
  border-radius: 20px;
}
.catalogo-badge.ya {
  background: #E3F2FD;
  color: #1565C0;
}
.catalogo-badge.nuevo {
  background: #FFF3E0;
  color: #E65100;
}

.import-inline {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.7rem 1rem;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 500;
  margin-top: 0.25rem;
}
.import-ok {
  background: #E8F5E9;
  color: #1B5E20;
}
.import-ok i { color: #2E7D32; }
.import-err {
  background: #FFEBEE;
  color: #B71C1C;
}
.import-err i { color: #C62828; }
</style>
