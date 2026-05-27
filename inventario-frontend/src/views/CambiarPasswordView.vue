<template>
  <div class="cambiar-wrapper">
    <div class="cambiar-card">
      <div class="cambiar-header">
        <i class="pi pi-lock" style="font-size: 2.5rem; color: #D32F2F;"></i>
        <h2>Cambiar Contrasena</h2>
        <p v-if="esPrimerLogin" class="aviso">
          Es su primer ingreso. Debe cambiar la contrasena antes de continuar.
        </p>
        <p v-else class="aviso">Ingrese su contrasena actual y la nueva contrasena.</p>
      </div>

      <form @submit.prevent="handleCambiar" class="cambiar-form">
        <div class="field">
          <label>Contrasena actual</label>
          <Password v-model="passwordActual" :feedback="false" toggleMask class="w-full" inputClass="w-full" />
        </div>
        <div class="field">
          <label>Nueva contrasena</label>
          <Password v-model="passwordNuevo" toggleMask class="w-full" inputClass="w-full" />
          <small class="hint">Minimo 8 caracteres, una mayuscula y un numero</small>
        </div>
        <div class="field">
          <label>Confirmar nueva contrasena</label>
          <Password v-model="confirmar" :feedback="false" toggleMask class="w-full" inputClass="w-full" />
        </div>

        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
        <Message v-if="exito" severity="success" :closable="false">{{ exito }}</Message>

        <Button
          type="submit"
          label="Guardar contrasena"
          icon="pi pi-save"
          :loading="loading"
          class="cambiar-btn w-full"
        />
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'

const auth = useAuthStore()
const router = useRouter()

const passwordActual = ref('')
const passwordNuevo = ref('')
const confirmar = ref('')
const loading = ref(false)
const error = ref('')
const exito = ref('')

const esPrimerLogin = computed(() => auth.primerLogin)

async function handleCambiar() {
  error.value = ''
  exito.value = ''

  if (!passwordActual.value || !passwordNuevo.value || !confirmar.value) {
    error.value = 'Complete todos los campos'
    return
  }
  if (passwordNuevo.value !== confirmar.value) {
    error.value = 'Las contrasenas nuevas no coinciden'
    return
  }
  if (passwordNuevo.value.length < 8) {
    error.value = 'La contrasena debe tener al menos 8 caracteres'
    return
  }
  if (!/[A-Z]/.test(passwordNuevo.value) || !/[0-9]/.test(passwordNuevo.value)) {
    error.value = 'La contrasena debe tener al menos una mayuscula y un numero'
    return
  }

  loading.value = true
  try {
    await auth.cambiarPassword(passwordActual.value, passwordNuevo.value)
    exito.value = 'Contrasena actualizada correctamente. Redirigiendo...'
    setTimeout(() => {
      if (auth.esVendedor) router.push('/vendedor/mi-cargue')
      else router.push('/dashboard')
    }, 1500)
  } catch (e) {
    error.value = e.response?.data?.mensaje || 'Error al cambiar la contrasena'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.cambiar-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #B71C1C 0%, #1a1a2e 100%);
  padding: 1rem;
}

.cambiar-card {
  background: white;
  border-radius: 16px;
  padding: 2.5rem 2rem;
  width: 100%;
  max-width: 420px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}

.cambiar-header {
  text-align: center;
  margin-bottom: 2rem;
}

.cambiar-header h2 {
  font-size: 1.4rem;
  color: #333;
  margin: 0.75rem 0 0.5rem;
}

.aviso {
  color: #666;
  font-size: 0.9rem;
  background: #fff3e0;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  border-left: 3px solid #F57F17;
}

.cambiar-form {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.field label {
  font-weight: 600;
  font-size: 0.9rem;
  color: #444;
}

.hint {
  color: #888;
  font-size: 0.78rem;
}

.cambiar-btn {
  background: #D32F2F !important;
  border-color: #D32F2F !important;
  margin-top: 0.5rem;
}

:deep(.p-password) { width: 100%; }
:deep(.p-password-input) { width: 100%; }
</style>
