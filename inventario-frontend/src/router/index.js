import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/cambiar-password',
    name: 'CambiarPassword',
    component: () => import('@/views/CambiarPasswordView.vue'),
    meta: { requiresAuth: true }
  },
  // Layout Admin/Auxiliar
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN', 'AUXILIAR'] },
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/admin/DashboardView.vue') },
      { path: 'productos', name: 'Productos', component: () => import('@/views/admin/ProductosView.vue') },
      { path: 'inventario', name: 'Inventario', component: () => import('@/views/admin/InventarioView.vue') },
      {
        path: 'compras',
        name: 'Compras',
        component: () => import('@/views/admin/ComprasView.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN'] }
      },
      { path: 'cargues', name: 'Cargues', component: () => import('@/views/admin/CarguesView.vue') },
      { path: 'cargues/:id', name: 'DetalleCargue', component: () => import('@/views/admin/DetalleCargueView.vue') },
      { path: 'clientes', name: 'Clientes', component: () => import('@/views/admin/ClientesView.vue') },
      { path: 'facturacion', name: 'Facturacion', component: () => import('@/views/admin/FacturacionView.vue') },
      { path: 'estado-cuenta', name: 'EstadoCuenta', component: () => import('@/views/admin/EstadoCuentaView.vue') },
      { path: 'reportes', name: 'Reportes', component: () => import('@/views/admin/ReportesView.vue') },
      {
        path: 'usuarios',
        name: 'Usuarios',
        component: () => import('@/views/admin/UsuariosView.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN'] }
      },
      {
        path: 'configuracion',
        name: 'Configuracion',
        component: () => import('@/views/admin/ConfiguracionView.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN'] }
      }
    ]
  },
  // Layout Vendedor (PWA movil)
  {
    path: '/vendedor',
    component: () => import('@/layouts/VendedorLayout.vue'),
    meta: { requiresAuth: true, roles: ['VENDEDOR'] },
    children: [
      { path: '', redirect: '/vendedor/mi-cargue' },
      { path: 'mi-cargue', name: 'MiCargue', component: () => import('@/views/vendedor/MiCargueView.vue') },
      { path: 'nueva-venta', name: 'NuevaVenta', component: () => import('@/views/vendedor/NuevaVentaView.vue') },
      { path: 'mis-ventas', name: 'MisVentas', component: () => import('@/views/vendedor/MisVentasView.vue') },
      { path: 'devoluciones', name: 'Devoluciones', component: () => import('@/views/vendedor/DevolucionesView.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const auth = useAuthStore()

  if (to.meta.public) return next()

  if (!auth.isAuthenticated) return next('/login')

  if (auth.primerLogin && to.name !== 'CambiarPassword') {
    return next('/cambiar-password')
  }

  if (to.meta.roles && !to.meta.roles.includes(auth.usuario?.rol)) {
    if (auth.esVendedor) return next('/vendedor/mi-cargue')
    return next('/dashboard')
  }

  next()
})

export default router
