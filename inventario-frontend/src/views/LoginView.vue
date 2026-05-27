<template>
  <div class="login-wrapper">
    <!-- Formas decorativas de fondo -->
    <div class="bg-shapes">
      <div class="shape s1"></div>
      <div class="shape s2"></div>
      <div class="shape s3"></div>
    </div>

    <div class="login-card">
      <!-- Logo / Header -->
      <div class="login-header">
        <div class="logo-mark">DA</div>
        <h1>DISTRIASOCIADOS</h1>
        <span class="subtitle-badge">Sistema de Inventario</span>
      </div>

      <!-- Formulario -->
      <form @submit.prevent="handleLogin" class="login-form">
        <div class="field">
          <label for="cedula">Cedula</label>
          <InputText
            id="cedula"
            v-model="cedula"
            placeholder="Numero de cedula"
            type="text"
            inputmode="numeric"
            name="username"
            autocomplete="username"
            :disabled="loading"
            autofocus
            class="w-full"
          />
        </div>

        <div class="field">
          <label for="password">Contrasena</label>
          <Password
            id="password"
            v-model="password"
            placeholder="Contrasena"
            :feedback="false"
            toggleMask
            :disabled="loading"
            name="password"
            autocomplete="current-password"
            class="w-full"
            inputClass="w-full"
            :inputProps="{ name: 'password', autocomplete: 'current-password' }"
          />
        </div>

        <div class="field-check">
          <Checkbox v-model="recordarme" inputId="recordarme" :binary="true" />
          <label for="recordarme">Recordarme por 30 dias</label>
        </div>

        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

        <Button
          type="submit"
          label="Ingresar"
          icon="pi pi-sign-in"
          :loading="loading"
          class="login-btn w-full"
        />
      </form>

      <!-- Botón instalar PWA (Android/Chrome) -->
      <div v-if="puedeInstalar" class="install-banner" @click="instalarApp">
        <i class="pi pi-download"></i>
        <div>
          <strong>Instalar app en tu celular</strong>
          <span>Accede sin abrir el navegador</span>
        </div>
        <i class="pi pi-chevron-right" style="margin-left:auto;color:#aaa"></i>
      </div>

      <!-- Instrucción manual para iOS -->
      <div v-else-if="esIOS" class="install-banner ios">
        <i class="pi pi-apple"></i>
        <div>
          <strong>Instalar en iPhone / iPad</strong>
          <span>Toca <b>Compartir</b> <i class="pi pi-share-alt"></i> y luego "Agregar a inicio"</span>
        </div>
      </div>

      <p class="login-footer">
        <i class="pi pi-lock" style="font-size:0.8rem;"></i>
        Solo el administrador puede crear o restablecer cuentas
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'
import Message from 'primevue/message'

const auth = useAuthStore()

const cedula = ref('')
const password = ref('')
const recordarme = ref(false)
const loading = ref(false)
const error = ref('')

// PWA install
const esStandalone = window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone
const esIOS = /iphone|ipad|ipod/i.test(navigator.userAgent) && !esStandalone
const puedeInstalar = ref(!esStandalone && !!window.__pwaInstallEvent)

function onPwaReady() {
  puedeInstalar.value = true
}

async function instalarApp() {
  const ev = window.__pwaInstallEvent
  if (!ev) return
  ev.prompt()
  const { outcome } = await ev.userChoice
  if (outcome === 'accepted') {
    puedeInstalar.value = false
    window.__pwaInstallEvent = null
  }
}

onMounted(() => window.addEventListener('pwaInstallReady', onPwaReady))
onUnmounted(() => window.removeEventListener('pwaInstallReady', onPwaReady))

async function handleLogin() {
  if (!cedula.value || !password.value) {
    error.value = 'Ingrese cedula y contrasena'
    return
  }
  error.value = ''
  loading.value = true
  try {
    await auth.login(cedula.value.trim(), password.value, recordarme.value)
  } catch (e) {
    error.value = e.response?.data?.mensaje || e.response?.data?.message || 'Error al iniciar sesion'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ── Wrapper & Fondo ── */
.login-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(150deg, #7f0000 0%, #C62828 30%, #D32F2F 55%, #16162a 100%);
  padding: 1rem;
  position: relative;
  overflow: hidden;
}

.bg-shapes { position: absolute; inset: 0; pointer-events: none; }
.shape { position: absolute; border-radius: 50%; }
.s1 {
  width: 540px; height: 540px;
  top: -200px; right: -180px;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.07);
}
.s2 {
  width: 300px; height: 300px;
  bottom: -90px; left: -80px;
  background: rgba(255,255,255,0.05);
}
.s3 {
  width: 170px; height: 170px;
  bottom: 28%; right: 9%;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.09);
}

/* ── Tarjeta ── */
.login-card {
  background: #fff;
  border-radius: 20px;
  padding: 2.5rem 2rem;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 25px 70px rgba(0,0,0,0.38), 0 8px 24px rgba(0,0,0,0.15);
  position: relative;
  z-index: 1;
  border-top: 4px solid var(--color-primary);
}

/* ── Header ── */
.login-header {
  text-align: center;
  margin-bottom: 2rem;
}

.logo-mark {
  width: 68px; height: 68px;
  background: linear-gradient(135deg, #D32F2F, #B71C1C);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 1.1rem;
  color: white;
  font-size: 1.45rem;
  font-weight: 800;
  letter-spacing: 1px;
  box-shadow: 0 8px 28px rgba(211, 47, 47, 0.45);
}

.login-header h1 {
  font-size: 1.3rem;
  font-weight: 700;
  color: #111827;
  letter-spacing: 0.5px;
}

.subtitle-badge {
  display: inline-flex;
  align-items: center;
  background: rgba(211, 47, 47, 0.08);
  color: var(--color-primary);
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0.22rem 0.8rem;
  border-radius: 99px;
  margin-top: 0.6rem;
  letter-spacing: 0.6px;
  text-transform: uppercase;
  border: 1px solid rgba(211, 47, 47, 0.15);
}

/* ── Formulario ── */
.login-form {
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
  font-size: 0.84rem;
  color: #374151;
}

.field-check {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.field-check label {
  font-size: 0.875rem;
  color: #6b7280;
  cursor: pointer;
}

.login-btn {
  background: #D32F2F !important;
  border-color: #D32F2F !important;
  border-radius: 10px !important;
  font-weight: 600 !important;
  height: 44px;
  margin-top: 0.5rem;
  transition: background 0.15s, box-shadow 0.15s !important;
}

.login-btn:hover {
  background: #B71C1C !important;
  border-color: #B71C1C !important;
  box-shadow: 0 4px 16px rgba(211,47,47,0.35) !important;
}

/* ── Banner PWA ── */
.install-banner {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 1.25rem;
  padding: 0.85rem 1rem;
  border-radius: 12px;
  background: #f0f7ff;
  border: 1.5px solid #BFDBFE;
  cursor: pointer;
  transition: background 0.15s;
}
.install-banner:hover { background: #dbeafe; }
.install-banner.ios { background: #f9fafb; border-color: #e5e7eb; cursor: default; }
.install-banner > .pi:first-child { font-size: 1.4rem; color: #1565C0; flex-shrink: 0; }
.install-banner.ios > .pi:first-child { color: #6b7280; }
.install-banner div { display: flex; flex-direction: column; gap: 0.15rem; }
.install-banner strong { font-size: 0.88rem; color: #1565C0; font-weight: 600; }
.install-banner.ios strong { color: #374151; }
.install-banner span { font-size: 0.75rem; color: #6b7280; }

/* ── Footer ── */
.login-footer {
  text-align: center;
  margin-top: 1.5rem;
  font-size: 0.75rem;
  color: #9ca3af;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
}

:deep(.p-password) { width: 100%; }
:deep(.p-password-input) { width: 100%; }
</style>
