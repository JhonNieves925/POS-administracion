<template>
  <div class="main-layout">
    <!-- Sidebar -->
    <aside :class="['sidebar', { collapsed: sidebarCollapsed }]">
      <div class="sidebar-header">
        <div class="brand" v-if="!sidebarCollapsed">
          <div class="brand-logo">DA</div>
          <div class="brand-texts">
            <span class="brand-name">DISTRIASOCIADOS</span>
            <span class="brand-sub">SAS</span>
          </div>
        </div>
        <div class="brand-icon-only" v-else>DA</div>
        <Button
          :icon="sidebarCollapsed ? 'pi pi-bars' : 'pi pi-times'"
          text rounded
          class="collapse-btn"
          @click="sidebarCollapsed = !sidebarCollapsed"
        />
      </div>

      <nav class="sidebar-nav">
        <template v-for="item in menuItems" :key="item.to ?? item.id">

          <!-- Item normal (sin submenú) -->
          <RouterLink
            v-if="!item.children"
            :to="item.to"
            class="nav-item"
            active-class="active"
            v-tooltip.right="sidebarCollapsed ? item.label : null"
          >
            <i :class="[item.icon, { 'spin-gear': item.icon === 'pi pi-cog' }]"></i>
            <span v-if="!sidebarCollapsed">{{ item.label }}</span>
          </RouterLink>

          <!-- Item con submenú (ej: Reportes) -->
          <template v-else>
            <div
              v-if="!sidebarCollapsed"
              class="nav-item nav-item-parent"
              :class="{ active: route.path.startsWith(item.basePath) }"
              @click="toggleSubmenu(item.id)"
            >
              <i :class="item.icon"></i>
              <span v-if="!sidebarCollapsed">{{ item.label }}</span>
              <i
                v-if="!sidebarCollapsed"
                class="pi submenu-arrow"
                :class="submenuAbierto === item.id ? 'pi-chevron-down' : 'pi-chevron-right'"
              />
            </div>

            <!-- Sub-items -->
            <transition name="submenu-slide">
              <div
                v-if="!sidebarCollapsed && submenuAbierto === item.id"
                class="submenu"
              >
                <RouterLink
                  v-for="child in item.children"
                  :key="child.to"
                  :to="child.to"
                  class="nav-item nav-subitem"
                  :class="{ 'subitem-active': isChildActive(child) }"
                >
                  <i :class="child.icon"></i>
                  <span>{{ child.label }}</span>
                </RouterLink>
              </div>
            </transition>

            <!-- Cuando colapsado: solo el ícono principal con tooltip -->
            <RouterLink
              v-if="sidebarCollapsed"
              :to="item.children[0].to"
              class="nav-item"
              :class="{ active: route.path.startsWith(item.basePath) }"
              v-tooltip.right="item.label"
            >
              <i :class="item.icon"></i>
            </RouterLink>
          </template>

        </template>
      </nav>

      <!-- Footer: íconos compactos cuando está colapsado -->
      <div class="sidebar-footer" :class="{ 'footer-collapsed': sidebarCollapsed }">
        <template v-if="!sidebarCollapsed">
          <div class="user-info">
            <div class="user-avatar">{{ userInitials }}</div>
            <div>
              <div class="user-name">{{ auth.usuario?.nombre }}</div>
              <div class="user-rol">{{ auth.usuario?.rol }}</div>
            </div>
          </div>
          <Button
            icon="pi pi-sign-out"
            text rounded severity="danger"
            @click="auth.logout()"
            v-tooltip.right="'Cerrar sesión'"
          />
        </template>
        <template v-else>
          <Button
            icon="pi pi-sign-out"
            text rounded severity="danger"
            @click="auth.logout()"
            v-tooltip.right="'Cerrar sesión'"
          />
        </template>
      </div>
    </aside>

    <!-- Main content -->
    <div class="content-area">
      <!-- Topbar -->
      <header class="topbar">
        <div class="topbar-left">
          <Button icon="pi pi-bars" text rounded @click="sidebarCollapsed = !sidebarCollapsed" class="mobile-menu-btn" />
          <h2 class="page-title">{{ pageTitle }}</h2>
        </div>
        <div class="topbar-right">
          <span class="fecha">{{ fechaHoy }}</span>
          <Button
            icon="pi pi-sign-out"
            label="Salir"
            text
            severity="danger"
            @click="auth.logout()"
            class="logout-btn-top"
          />
        </div>
      </header>

      <!-- Router view -->
      <main class="main-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, RouterLink, RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Button from 'primevue/button'

const auth  = useAuthStore()
const route = useRoute()

const userInitials = computed(() => {
  const name = auth.usuario?.nombre || ''
  const parts = name.trim().split(' ').filter(Boolean)
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase()
  return name.substring(0, 2).toUpperCase() || 'U'
})

// Empieza CERRADO en cada inicio de sesión
const sidebarCollapsed = ref(true)

// Submenú activo ('reportes' | null)
const submenuAbierto = ref(null)

function toggleSubmenu(id) {
  submenuAbierto.value = submenuAbierto.value === id ? null : id
}

function isChildActive(child) {
  const [childPath, childQuery] = child.to.split('?')
  if (route.path !== childPath) return false
  if (!childQuery) return true
  const params = Object.fromEntries(new URLSearchParams(childQuery))
  return Object.entries(params).every(([k, v]) => route.query[k] === v)
}

// Abrir automáticamente el submenú de reportes si la ruta empieza con /reportes
watch(() => route.path, (path) => {
  if (path.startsWith('/reportes')) submenuAbierto.value = 'reportes'
}, { immediate: true })

const menuItems = computed(() => {
  const items = [
    { to: '/dashboard',     icon: 'pi pi-chart-bar',  label: 'Dashboard' },
    { to: '/productos',     icon: 'pi pi-box',         label: 'Productos' },
    { to: '/inventario',    icon: 'pi pi-warehouse',   label: 'Inventario' },
    ...(auth.esAdmin ? [{ to: '/compras', icon: 'pi pi-shopping-cart', label: 'Compras' }] : []),
    { to: '/cargues',       icon: 'pi pi-truck',       label: 'Cargues' },
    { to: '/clientes',      icon: 'pi pi-users',       label: 'Clientes' },
    { to: '/facturacion',   icon: 'pi pi-cloud-upload', label: 'Facturacion' },
    { to: '/estado-cuenta', icon: 'pi pi-wallet',      label: 'Estado de Cuenta' },
    {
      id: 'reportes',
      basePath: '/reportes',
      icon: 'pi pi-chart-line',
      label: 'Reportes',
      children: [
        // ── Solo ADMIN: cifras financieras de la empresa ──────────
        ...(auth.esAdmin ? [
          { to: '/reportes?tab=ventas',          icon: 'pi pi-chart-line', label: 'Ventas' },
          { to: '/reportes?tab=inventario',      icon: 'pi pi-box',        label: 'Inventario' },
          { to: '/reportes?tab=devoluciones',    icon: 'pi pi-replay',     label: 'Devoluciones' },
          { to: '/reportes?tab=ganancias',       icon: 'pi pi-dollar',     label: 'Ganancias' },
          { to: '/reportes?tab=kardex',          icon: 'pi pi-arrows-v',   label: 'Kardex' },
        ] : []),
        // ── ADMIN y AUXILIAR: reportes operativos ─────────────────
        { to: '/reportes?tab=estado-cuenta',   icon: 'pi pi-wallet', label: 'Estado de Cuenta' },
        { to: '/reportes?tab=relacion-ventas', icon: 'pi pi-book',   label: 'Relación de Ventas' },
        { to: '/reportes?tab=nulas',           icon: 'pi pi-ban',    label: 'Nulas' },
      ]
    },
  ]
  if (auth.esAdmin) {
    items.push({ to: '/usuarios',      icon: 'pi pi-user-edit', label: 'Usuarios' })
    items.push({ to: '/configuracion', icon: 'pi pi-cog',       label: 'Configuración' })
  }
  return items
})

const pageTitle = computed(() => {
  const titles = {
    '/dashboard':     'Dashboard',
    '/productos':     'Productos',
    '/inventario':    'Inventario',
    '/compras':       'Compras',
    '/cargues':       'Cargues',
    '/clientes':      'Clientes',
    '/facturacion':   'Facturacion',
    '/estado-cuenta': 'Estado de Cuenta',
    '/reportes':      'Reportes',
    '/usuarios':      'Usuarios',
    '/configuracion': 'Configuración',
  }
  return titles[route.path] || 'DISTRIASOCIADOS'
})

const fechaHoy = computed(() => {
  return new Date().toLocaleDateString('es-CO', {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
  })
})
</script>

<style scoped>
.main-layout {
  display: flex;
  min-height: 100vh;
  background: var(--color-bg);
}

/* ====== SIDEBAR ====== */
.sidebar {
  width: 248px;
  min-width: 248px;
  background: linear-gradient(180deg, #1c1c32 0%, var(--color-sidebar-bg) 100%);
  color: var(--color-sidebar-text);
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease, min-width 0.25s ease;
  z-index: 100;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
  box-shadow: 2px 0 16px rgba(0,0,0,0.18);
}

.sidebar.collapsed {
  width: 68px;
  min-width: 68px;
}

/* ── Header ── */
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 0.85rem;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  min-height: 72px;
  flex-shrink: 0;
}

.sidebar.collapsed .sidebar-header {
  justify-content: center;
  padding: 1rem 0;
  flex-direction: column;
  gap: 0.4rem;
}

.brand {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 0.65rem;
  overflow: hidden;
  white-space: nowrap;
}

.brand-logo {
  width: 34px; height: 34px;
  background: linear-gradient(135deg, #D32F2F, #B71C1C);
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.5px;
  flex-shrink: 0;
  box-shadow: 0 3px 10px rgba(211, 47, 47, 0.45);
}

.brand-icon-only {
  width: 34px; height: 34px;
  background: linear-gradient(135deg, #D32F2F, #B71C1C);
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.5px;
  box-shadow: 0 3px 10px rgba(211, 47, 47, 0.4);
}

.brand-texts {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.brand-name {
  font-size: 0.8rem;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.brand-sub {
  font-size: 0.65rem;
  color: rgba(255,255,255,0.4);
  letter-spacing: 0.5px;
}

.collapse-btn {
  color: rgba(255,255,255,0.4) !important;
  flex-shrink: 0;
  transition: color 0.15s !important;
}
.collapse-btn:hover { color: rgba(255,255,255,0.8) !important; }

/* ── Nav items ── */
.sidebar-nav {
  flex: 1 1 0;
  height: 0;            /* ← fuerza al flex item a respetar su límite */
  padding: 0.75rem 0.6rem;
  overflow-y: auto;
  overflow-x: hidden;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.68rem 0.85rem;
  color: var(--color-sidebar-text);
  text-decoration: none;
  font-size: 0.875rem;
  font-weight: 500;
  transition: background 0.15s, color 0.15s;
  border-radius: 9px;
  position: relative;
  white-space: nowrap;
  margin-bottom: 2px;
}

.sidebar.collapsed .nav-item {
  justify-content: center;
  padding: 0.7rem 0;
  gap: 0;
}

.nav-item:hover {
  background: rgba(255,255,255,0.08);
  color: white;
}

.nav-item.active {
  background: rgba(211, 47, 47, 0.18);
  color: #fff;
  font-weight: 600;
  
}

.nav-item.active i { color: #EF9A9A; }

.nav-item i {
  width: 18px;
  text-align: center;
  font-size: 0.95rem;
  flex-shrink: 0;
}

/* ── Engranaje girando ── */
@keyframes spinGear {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
.spin-gear { animation: spinGear 5s linear infinite; display: inline-block; }

/* ── Footer ── */
.sidebar-footer {
  padding: 0.85rem;
  border-top: 1px solid rgba(255,255,255,0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  flex-shrink: 0;
}

.sidebar-footer.footer-collapsed {
  justify-content: center;
  padding: 0.75rem 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  overflow: hidden;
  min-width: 0;
}

.user-avatar {
  width: 34px; height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #D32F2F, #9B1C1C);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 0.72rem;
  font-weight: 700;
  flex-shrink: 0;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  box-shadow: 0 2px 8px rgba(211, 47, 47, 0.4);
}

.user-name {
  font-size: 0.8rem;
  font-weight: 600;
  color: white;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-rol {
  font-size: 0.68rem;
  color: rgba(255,255,255,0.45);
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

/* ====== CONTENT ====== */
.content-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.topbar {
  background: #fff;
  padding: 0 1.5rem;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e5e7eb;
  box-shadow: 0 1px 10px rgba(0,0,0,0.05);
  position: sticky;
  top: 0;
  z-index: 50;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 0.85rem;
}

.page-title {
  font-size: 1.1rem;
  color: #111827;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 0.85rem;
}

.fecha {
  font-size: 0.78rem;
  color: #6b7280;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  padding: 0.28rem 0.75rem;
  border-radius: 99px;
  font-weight: 500;
  text-transform: capitalize;
  white-space: nowrap;
}

.mobile-menu-btn { display: none; }

.main-content {
  flex: 1;
  padding: 1.5rem;
  overflow-y: auto;
}

/* ====== SUBMENÚ ====== */
.nav-item-parent {
  cursor: pointer;
  justify-content: space-between;
}
.nav-item-parent .pi:first-child { flex-shrink: 0; }
.submenu-arrow {
  margin-left: auto;
  font-size: 0.72rem;
  color: rgba(255,255,255,0.35);
  transition: transform 0.2s;
}
.submenu { overflow: hidden; }
.nav-subitem {
  padding-left: 2.4rem;
  font-size: 0.83rem;
  border-radius: 8px;
  color: rgba(196,196,216,0.8);
}
.nav-subitem:hover { color: white; background: rgba(255,255,255,0.06); }
.subitem-active {
  color: white !important;
  background: rgba(211, 47, 47, 0.18) !important;
  
}

.submenu-slide-enter-active,
.submenu-slide-leave-active {
  transition: max-height 0.25s ease, opacity 0.2s ease;
  max-height: 400px;
  opacity: 1;
}
.submenu-slide-enter-from,
.submenu-slide-leave-to {
  max-height: 0;
  opacity: 0;
}

/* ====== MÓVIL ====== */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    height: 100vh;
    transform: translateX(-100%);
    transition: transform 0.25s ease, width 0.25s ease;
  }
  .sidebar.open { transform: translateX(0); }
  .sidebar.collapsed {
    transform: translateX(-100%);
    width: 248px;
    min-width: 248px;
  }
  .mobile-menu-btn { display: flex; }
  .fecha { display: none; }
}
</style>
