<template>
  <div class="vendedor-layout">
    <!-- Header -->
    <header class="v-header">
      <div class="v-header-left">
        <div class="v-brand-logo">DA</div>
        <span class="v-brand">DISTRIASOCIADOS</span>
      </div>
      <div class="v-header-right">
        <span class="v-user">{{ auth.usuario?.nombre?.split(' ')[0] }}</span>
        <Button icon="pi pi-sign-out" text rounded severity="danger" @click="auth.logout()" />
      </div>
    </header>

    <!-- Indicador offline -->
    <div v-if="!isOnline" class="offline-banner">
      <i class="pi pi-wifi"></i>
      Modo offline - Los datos se sincronizaran cuando haya conexion
    </div>

    <!-- Contenido -->
    <main class="v-content">
      <RouterView />
    </main>

    <!-- Bottom Navigation -->
    <nav class="bottom-nav">
      <RouterLink to="/vendedor/mi-cargue" class="bnav-item" active-class="active">
        <i class="pi pi-truck"></i>
        <span>Mi Cargue</span>
      </RouterLink>
      <RouterLink to="/vendedor/nueva-venta" class="bnav-item" active-class="active">
        <i class="pi pi-plus-circle"></i>
        <span>Vender</span>
      </RouterLink>
      <RouterLink to="/vendedor/mis-ventas" class="bnav-item" active-class="active">
        <i class="pi pi-receipt"></i>
        <span>Mis Ventas</span>
      </RouterLink>
      <RouterLink to="/vendedor/devoluciones" class="bnav-item" active-class="active">
        <i class="pi pi-flag"></i>
        <span>Fin Ruta</span>
      </RouterLink>
    </nav>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Button from 'primevue/button'

const auth = useAuthStore()
const isOnline = ref(navigator.onLine)

function updateOnlineStatus() {
  isOnline.value = navigator.onLine
}

onMounted(() => {
  window.addEventListener('online', updateOnlineStatus)
  window.addEventListener('offline', updateOnlineStatus)
})

onUnmounted(() => {
  window.removeEventListener('online', updateOnlineStatus)
  window.removeEventListener('offline', updateOnlineStatus)
})
</script>

<style scoped>
.vendedor-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--color-bg);
  max-width: 480px;
  margin: 0 auto;
}

.v-header {
  background: linear-gradient(135deg, #B71C1C 0%, #D32F2F 100%);
  color: white;
  padding: 0.8rem 1rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 2px 16px rgba(183, 28, 28, 0.35);
  position: sticky;
  top: 0;
  z-index: 100;
}

.v-header-left {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.v-brand-logo {
  width: 30px; height: 30px;
  background: rgba(255,255,255,0.18);
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.68rem;
  font-weight: 800;
  color: white;
  letter-spacing: 0.5px;
  border: 1px solid rgba(255,255,255,0.25);
}

.v-brand {
  font-weight: 700;
  font-size: 0.85rem;
  letter-spacing: 0.8px;
}

.v-header-right {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.v-user {
  font-size: 0.84rem;
  font-weight: 500;
  opacity: 0.9;
}

.offline-banner {
  background: #F57F17;
  color: white;
  text-align: center;
  padding: 0.5rem 1rem;
  font-size: 0.82rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  font-weight: 500;
}

.v-content {
  flex: 1;
  padding: 1rem;
  padding-bottom: 5.5rem;
  overflow-y: auto;
}

.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 480px;
  background: white;
  display: flex;
  box-shadow: 0 -1px 0 #e5e7eb, 0 -4px 16px rgba(0,0,0,0.08);
  z-index: 100;
  border-top-left-radius: 16px;
  border-top-right-radius: 16px;
}

.bnav-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.22rem;
  padding: 0.65rem 0 0.55rem;
  color: #9ca3af;
  text-decoration: none;
  font-size: 0.68rem;
  font-weight: 500;
  transition: color 0.18s;
  position: relative;
}

.bnav-item i {
  font-size: 1.25rem;
  transition: transform 0.18s;
}

.bnav-item.active { color: #D32F2F; }
.bnav-item.active i { color: #D32F2F; transform: scale(1.12); }

.bnav-item.active::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 28px;
  height: 3px;
  background: #D32F2F;
  border-radius: 0 0 4px 4px;
}
</style>
