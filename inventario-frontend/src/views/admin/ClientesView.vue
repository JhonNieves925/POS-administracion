<template>
  <div class="clientes-page">
    <div class="page-toolbar">
      <div class="toolbar-left">
        <div class="search-wrapper">
          <i class="pi pi-search search-icon" />
          <InputText v-model="filtro" placeholder="Buscar cliente, NIT..." class="search-input" />
        </div>
      </div>
      <div class="toolbar-right">
        <Button label="Nuevo cliente" icon="pi pi-plus" @click="abrirNuevo" />
      </div>
    </div>

    <div class="card-table">
      <DataTable :value="clientesFiltrados" :loading="loading" stripedRows paginator :rows="15" responsiveLayout="scroll">
        <Column field="razonSocial" header="Nombre / Razon social" sortable />
        <Column field="nit" header="NIT / CC" />
        <Column field="telefono" header="Telefono" />
        <Column field="municipio" header="Municipio" />
        <Column header="Facturacion">
          <template #body="{ data }">
            <span v-if="data.soloFacturaFisica" class="badge-fisico">Fisica</span>
            <span v-else>
              <i v-if="data.envioCorreo" class="pi pi-envelope" v-tooltip="'Email'" style="margin-right:4px"></i>
              <i v-if="data.envioWhatsapp" class="pi pi-whatsapp" v-tooltip="'WhatsApp'"></i>
            </span>
          </template>
        </Column>
        <Column header="Regimen">
          <template #body="{ data }">{{ data.regimen || '-' }}</template>
        </Column>
        <Column header="Acciones" style="width:100px">
          <template #body="{ data }">
            <Button icon="pi pi-pencil" text rounded size="small" @click="editarCliente(data)" />
            <Button icon="pi pi-trash" text rounded size="small" severity="danger" @click="confirmarEliminar(data)" />
          </template>
        </Column>
        <template #empty>No hay clientes registrados</template>
      </DataTable>
    </div>

    <Dialog v-model:visible="dialogVisible" :header="editando ? 'Editar Cliente' : 'Nuevo Cliente'" modal :style="{width:'580px'}" :draggable="false">
      <form @submit.prevent="guardar" class="cliente-form">
        <div class="form-row">
          <div class="field">
            <label>Nombre / Razon social *</label>
            <InputText v-model="form.razonSocial" required class="w-full" />
          </div>
          <div class="field">
            <label>NIT / CC *</label>
            <InputText v-model="form.nit" required class="w-full" />
          </div>
        </div>
        <div class="form-row">
          <div class="field">
            <label>Telefono</label>
            <InputText v-model="form.telefono" class="w-full" />
          </div>
          <div class="field">
            <label>Correo electronico</label>
            <InputText v-model="form.correo" type="email" class="w-full" />
          </div>
        </div>
        <div class="form-row">
          <div class="field">
            <label>Direccion</label>
            <InputText v-model="form.direccion" class="w-full" />
          </div>
          <div class="field">
            <label>Municipio</label>
            <InputText v-model="form.municipio" class="w-full" placeholder="La Union, Valle..." />
          </div>
        </div>
        <div class="form-row">
          <div class="field">
            <label>Regimen tributario</label>
            <Select v-model="form.regimen" :options="regimenes" optionLabel="label" optionValue="value" class="w-full" />
          </div>
          <div class="field">
            <label>Tipo de persona</label>
            <Select v-model="form.tipoPersona" :options="tiposPersona" optionLabel="label" optionValue="value" class="w-full" />
          </div>
        </div>

        <div class="opciones-factura">
          <h4>Opciones de facturacion</h4>
          <div class="check-group">
            <div class="check-item">
              <Checkbox v-model="form.envioCorreo" :binary="true" inputId="envioCorreo" />
              <label for="envioCorreo">Enviar factura por email</label>
            </div>
            <div class="check-item">
              <Checkbox v-model="form.envioWhatsapp" :binary="true" inputId="envioWA" />
              <label for="envioWA">Enviar por WhatsApp (cuando este activo)</label>
            </div>
            <div class="check-item">
              <Checkbox v-model="form.soloFacturaFisica" :binary="true" inputId="soloFisica" />
              <label for="soloFisica">Solo factura fisica (no electronica)</label>
            </div>
          </div>
        </div>

        <Message v-if="formError" severity="error" :closable="false">{{ formError }}</Message>
        <div class="dialog-footer">
          <Button label="Cancelar" text @click="dialogVisible = false" />
          <Button type="submit" :label="editando ? 'Guardar cambios' : 'Crear cliente'" icon="pi pi-save" :loading="guardando" />
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
import Select from 'primevue/select'
import Checkbox from 'primevue/checkbox'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'

const toast = useToast()
const confirm = useConfirm()
const clientes = ref([])
const loading = ref(true)
const filtro = ref('')
const dialogVisible = ref(false)
const editando = ref(false)
const guardando = ref(false)
const formError = ref('')

const regimenes = [
  { label: 'Simplificado', value: 'SIMPLIFICADO' },
  { label: 'Comun', value: 'COMUN' },
  { label: 'Gran contribuyente', value: 'GRAN_CONTRIBUYENTE' },
  { label: 'No responsable', value: 'NO_RESPONSABLE' }
]
const tiposPersona = [
  { label: 'Persona natural', value: 'NATURAL' },
  { label: 'Persona juridica', value: 'JURIDICA' }
]

const formInicial = () => ({
  id: null, razonSocial: '', nit: '', telefono: '', correo: '',
  direccion: '', municipio: 'La Union', regimen: 'SIMPLIFICADO',
  tipoPersona: 'NATURAL',
  envioCorreo: false, envioWhatsapp: false, soloFacturaFisica: true
})
const form = ref(formInicial())

const clientesFiltrados = computed(() => {
  if (!filtro.value) return clientes.value
  const f = filtro.value.toLowerCase()
  return clientes.value.filter(c =>
    c.razonSocial?.toLowerCase().includes(f) ||
    c.nit?.toLowerCase().includes(f) ||
    c.municipio?.toLowerCase().includes(f)
  )
})

function abrirNuevo() {
  form.value = formInicial()
  editando.value = false
  formError.value = ''
  dialogVisible.value = true
}

function editarCliente(c) {
  form.value = { ...c }
  editando.value = true
  formError.value = ''
  dialogVisible.value = true
}

async function guardar() {
  formError.value = ''
  guardando.value = true
  try {
    if (editando.value) {
      await api.put(`/clientes/${form.value.id}`, form.value)
      toast.add({ severity: 'success', summary: 'Actualizado', detail: 'Cliente actualizado', life: 3000 })
    } else {
      await api.post('/clientes', form.value)
      toast.add({ severity: 'success', summary: 'Creado', detail: 'Cliente creado', life: 3000 })
    }
    dialogVisible.value = false
    cargarClientes()
  } catch (e) {
    formError.value = e.response?.data?.mensaje || 'Error al guardar'
  } finally {
    guardando.value = false
  }
}

function confirmarEliminar(c) {
  confirm.require({
    message: `Eliminar cliente "${c.razonSocial}"?`,
    header: 'Confirmar',
    icon: 'pi pi-exclamation-triangle',
    acceptSeverity: 'danger',
    acceptLabel: 'Eliminar',
    rejectLabel: 'Cancelar',
    accept: async () => {
      try {
        await api.delete(`/clientes/${c.id}`)
        toast.add({ severity: 'success', summary: 'Eliminado', detail: 'Cliente eliminado', life: 3000 })
        cargarClientes()
      } catch (e) {
        toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'No se pudo eliminar', life: 4000 })
      }
    }
  })
}

async function cargarClientes() {
  loading.value = true
  try {
    const res = await api.get('/clientes')
    clientes.value = res.data
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Error cargando clientes', life: 4000 })
  } finally {
    loading.value = false
  }
}

onMounted(cargarClientes)
</script>

<style scoped>
.clientes-page { display: flex; flex-direction: column; gap: 1rem; }
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
.card-table { background: white; border-radius: 14px; padding: 1rem; border: 1px solid var(--color-border); box-shadow: var(--shadow-sm); }
.cliente-form { display: flex; flex-direction: column; gap: 1rem; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.field { display: flex; flex-direction: column; gap: 0.35rem; }
.field label { font-size: 0.85rem; font-weight: 600; color: #444; }
.opciones-factura { background: #f9f9f9; padding: 1rem; border-radius: 8px; }
.opciones-factura h4 { margin: 0 0 0.75rem; color: #333; font-size: 0.9rem; }
.check-group { display: flex; flex-direction: column; gap: 0.6rem; }
.check-item { display: flex; align-items: center; gap: 0.5rem; font-size: 0.9rem; }
.badge-fisico { background: #e0e0e0; color: #555; border-radius: 4px; padding: 2px 8px; font-size: 0.78rem; }
.dialog-footer {
  display: flex; justify-content: flex-end; gap: 0.75rem;
  padding-top: 0.5rem; border-top: 1px solid #f0f0f0; margin-top: 0.5rem;
}
</style>
