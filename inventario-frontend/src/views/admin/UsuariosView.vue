<template>
  <div class="usuarios-page">
    <div class="page-toolbar">
      <div class="toolbar-left">
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="filtro" placeholder="Buscar usuario, cedula..." />
        </span>
      </div>
      <div class="toolbar-right">
        <Button label="Nuevo usuario" icon="pi pi-plus" @click="abrirNuevo" />
      </div>
    </div>

    <div class="card-table">
      <DataTable :value="usuariosFiltrados" :loading="loading" stripedRows paginator :rows="15">
        <Column field="cedula" header="Cedula" sortable />
        <Column field="nombre" header="Nombre" sortable />
        <Column field="correo" header="Correo" />
        <Column header="Rol">
          <template #body="{ data }">
            <Tag :value="data.rol" :severity="rolSeverity(data.rol)" />
          </template>
        </Column>
        <Column header="Estado">
          <template #body="{ data }">
            <Tag :value="data.activo ? 'Activo' : 'Inactivo'" :severity="data.activo ? 'success' : 'danger'" />
          </template>
        </Column>
        <Column header="Primer login">
          <template #body="{ data }">
            <i v-if="data.primerLogin" class="pi pi-clock" style="color:#F57F17" v-tooltip="'Pendiente cambio de contrasena'"></i>
            <i v-else class="pi pi-check" style="color:#2E7D32"></i>
          </template>
        </Column>
        <Column header="Acciones" style="width:150px">
          <template #body="{ data }">
            <Button icon="pi pi-pencil" text rounded size="small" @click="editarUsuario(data)" v-tooltip.top="'Editar'" />
            <Button
              icon="pi pi-key"
              text rounded size="small" severity="warn"
              @click="resetearPassword(data)"
              v-tooltip.top="'Resetear contrasena'"
            />
            <Button
              :icon="data.activo ? 'pi pi-ban' : 'pi pi-check'"
              text rounded size="small"
              :severity="data.activo ? 'danger' : 'success'"
              @click="toggleActivo(data)"
              v-tooltip.top="data.activo ? 'Desactivar' : 'Activar'"
            />
          </template>
        </Column>
        <template #empty>No hay usuarios registrados</template>
      </DataTable>
    </div>

    <!-- Dialog usuario -->
    <Dialog v-model:visible="dialogVisible" :header="editando ? 'Editar Usuario' : 'Nuevo Usuario'" modal :style="{width:'480px'}" :draggable="false">
      <form @submit.prevent="guardar" class="usuario-form">
        <div class="field">
          <label>Cedula *</label>
          <InputText v-model="form.cedula" required class="w-full" :disabled="editando" />
        </div>
        <div class="field">
          <label>Nombre completo *</label>
          <InputText v-model="form.nombre" required class="w-full" />
        </div>
        <div class="field">
          <label>Correo electronico</label>
          <InputText v-model="form.correo" type="email" class="w-full" />
        </div>
        <div class="field">
          <label>Rol *</label>
          <Select v-model="form.rol" :options="roles" optionLabel="label" optionValue="value" class="w-full" required />
        </div>
        <div class="field" v-if="!editando">
          <label>Contrasena inicial *</label>
          <Password v-model="form.password" :feedback="false" toggleMask class="w-full" inputClass="w-full" required />
          <small class="hint">El usuario debera cambiarla en el primer ingreso.</small>
        </div>

        <Message v-if="formError" severity="error" :closable="false">{{ formError }}</Message>
        <div class="dialog-footer">
          <Button label="Cancelar" text @click="dialogVisible = false" />
          <Button type="submit" :label="editando ? 'Guardar cambios' : 'Crear usuario'" icon="pi pi-save" :loading="guardando" />
        </div>
      </form>
    </Dialog>

    <!-- Dialog reset password -->
    <Dialog v-model:visible="dialogReset" header="Resetear Contrasena" modal :style="{width:'400px'}" :draggable="false">
      <div class="reset-form">
        <p>Resetear contrasena de: <strong>{{ usuarioReset?.nombre }}</strong></p>
        <div class="field">
          <label>Nueva contrasena</label>
          <Password v-model="nuevaPassword" :feedback="false" toggleMask class="w-full" inputClass="w-full" />
        </div>
        <Message v-if="resetError" severity="error" :closable="false">{{ resetError }}</Message>
        <div class="dialog-footer">
          <Button label="Cancelar" text @click="dialogReset = false" />
          <Button label="Resetear" icon="pi pi-key" severity="warn" @click="confirmarReset" :loading="reseteando" />
        </div>
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import api from '@/api/axios'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Select from 'primevue/select'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

const toast = useToast()
const usuarios = ref([])
const loading = ref(true)
const filtro = ref('')
const dialogVisible = ref(false)
const dialogReset = ref(false)
const editando = ref(false)
const guardando = ref(false)
const reseteando = ref(false)
const formError = ref('')
const resetError = ref('')
const usuarioReset = ref(null)
const nuevaPassword = ref('')

const roles = [
  { label: 'Administrador', value: 'ADMIN' },
  { label: 'Auxiliar', value: 'AUXILIAR' },
  { label: 'Vendedor', value: 'VENDEDOR' }
]

const formInicial = () => ({
  id: null, cedula: '', nombre: '', correo: '', rol: 'VENDEDOR', password: ''
})
const form = ref(formInicial())

const usuariosFiltrados = computed(() => {
  if (!filtro.value) return usuarios.value
  const f = filtro.value.toLowerCase()
  return usuarios.value.filter(u =>
    u.nombre?.toLowerCase().includes(f) ||
    u.cedula?.toLowerCase().includes(f) ||
    u.rol?.toLowerCase().includes(f)
  )
})

function rolSeverity(rol) {
  return { ADMIN: 'danger', AUXILIAR: 'warn', VENDEDOR: 'info' }[rol] || 'secondary'
}

function abrirNuevo() {
  form.value = formInicial()
  editando.value = false
  formError.value = ''
  dialogVisible.value = true
}

function editarUsuario(u) {
  form.value = { ...u, password: '' }
  editando.value = true
  formError.value = ''
  dialogVisible.value = true
}

async function guardar() {
  formError.value = ''
  guardando.value = true
  try {
    if (editando.value) {
      const res = await api.put(`/usuarios/${form.value.id}`, form.value)
      const idx = usuarios.value.findIndex(u => u.id === form.value.id)
      if (idx !== -1) usuarios.value.splice(idx, 1, res.data)
      toast.add({ severity: 'success', summary: 'Actualizado', detail: 'Usuario actualizado', life: 3000 })
    } else {
      const res = await api.post('/usuarios', form.value)
      usuarios.value.unshift(res.data)
      toast.add({ severity: 'success', summary: 'Creado', detail: 'Usuario creado. Primer login activo.', life: 3000 })
    }
    dialogVisible.value = false
    cargarUsuarios() // recarga en segundo plano para consistencia
  } catch (e) {
    formError.value = e.response?.data?.mensaje || 'Error al guardar'
  } finally {
    guardando.value = false
  }
}

function resetearPassword(u) {
  usuarioReset.value = u
  nuevaPassword.value = ''
  resetError.value = ''
  dialogReset.value = true
}

async function confirmarReset() {
  if (!nuevaPassword.value || nuevaPassword.value.length < 6) {
    resetError.value = 'La contrasena debe tener al menos 6 caracteres'
    return
  }
  reseteando.value = true
  try {
    await api.post(`/usuarios/${usuarioReset.value.id}/reset-password`, { nuevaPassword: nuevaPassword.value })
    toast.add({ severity: 'success', summary: 'Contrasena reseteada', detail: `Contrasena de ${usuarioReset.value.nombre} actualizada`, life: 3000 })
    dialogReset.value = false
  } catch (e) {
    resetError.value = e.response?.data?.mensaje || 'Error al resetear'
  } finally {
    reseteando.value = false
  }
}

async function toggleActivo(u) {
  try {
    const res = await api.put(`/usuarios/${u.id}/toggle-activo`)
    // Actualización inmediata: reflejar el nuevo estado sin esperar el GET
    const idx = usuarios.value.findIndex(x => x.id === u.id)
    if (idx !== -1) usuarios.value.splice(idx, 1, res.data)
    toast.add({ severity: 'info', summary: 'Actualizado', detail: `Usuario ${u.activo ? 'desactivado' : 'activado'}`, life: 3000 })
    cargarUsuarios() // recarga en segundo plano
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'Error', life: 4000 })
  }
}

async function cargarUsuarios() {
  loading.value = true
  try {
    const res = await api.get('/usuarios')
    usuarios.value = res.data
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Error cargando usuarios', life: 4000 })
  } finally {
    loading.value = false
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') cargarUsuarios()
}

onMounted(() => {
  cargarUsuarios()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped>
.usuarios-page { display: flex; flex-direction: column; gap: 1rem; }
.page-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  background: white; padding: 1rem 1.25rem; border-radius: 10px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
}
.card-table { background: white; border-radius: 10px; padding: 1rem; box-shadow: 0 2px 6px rgba(0,0,0,0.05); }
.usuario-form, .reset-form { display: flex; flex-direction: column; gap: 1rem; }
.field { display: flex; flex-direction: column; gap: 0.35rem; }
.field label { font-size: 0.85rem; font-weight: 600; color: #444; }
.hint { color: #888; font-size: 0.78rem; }
.dialog-footer {
  display: flex; justify-content: flex-end; gap: 0.75rem;
  padding-top: 0.5rem; border-top: 1px solid #f0f0f0; margin-top: 0.5rem;
}
:deep(.p-password) { width: 100%; }
:deep(.p-password-input) { width: 100%; }
</style>
