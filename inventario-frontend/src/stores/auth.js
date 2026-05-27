import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api/axios'
import router from '@/router'

// Lee de localStorage primero (recordarme activo), luego sessionStorage (sesión actual)
function leerStorage(key) {
  return localStorage.getItem(key) || sessionStorage.getItem(key) || null
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(leerStorage('token'))
  const usuario = ref(JSON.parse(leerStorage('usuario') || 'null'))

  const isAuthenticated = computed(() => !!token.value)
  const esAdmin = computed(() => usuario.value?.rol === 'ADMIN')
  const esAuxiliar = computed(() => usuario.value?.rol === 'AUXILIAR')
  const esVendedor = computed(() => usuario.value?.rol === 'VENDEDOR')
  const esAdminOAux = computed(() => ['ADMIN', 'AUXILIAR'].includes(usuario.value?.rol))
  const primerLogin = computed(() => usuario.value?.primerLogin === true)

  async function login(cedula, password, recordarme = false) {
    // El backend devuelve ApiResponse<LoginResponse>
    // El interceptor axios desenvuelve a LoginResponse = { token, cedula, nombre, rol, primerLogin }
    const response = await api.post('/auth/login', { cedula, password, recordarme })
    const data = response.data

    token.value = data.token
    usuario.value = {
      cedula: data.cedula,
      nombre: data.nombre,
      rol: data.rol,
      primerLogin: data.primerLogin
    }

    // recordarme → localStorage (persiste al cerrar el navegador/app)
    // sin recordarme → sessionStorage (se borra al cerrar la pestaña/app)
    const storage = recordarme ? localStorage : sessionStorage
    const otra = recordarme ? sessionStorage : localStorage
    storage.setItem('token', data.token)
    storage.setItem('usuario', JSON.stringify(usuario.value))
    // Limpiar del otro storage para evitar conflictos
    otra.removeItem('token')
    otra.removeItem('usuario')

    if (data.primerLogin) {
      router.push('/cambiar-password')
    } else if (['ADMIN', 'AUXILIAR'].includes(data.rol)) {
      router.push('/dashboard')
    } else {
      router.push('/vendedor/mi-cargue')
    }
  }

  async function cambiarPassword(passwordActual, passwordNuevo) {
    await api.post('/auth/cambiar-password', {
      passwordActual,
      passwordNueva: passwordNuevo,
      confirmarPassword: passwordNuevo
    })
    if (usuario.value) {
      usuario.value.primerLogin = false
      localStorage.setItem('usuario', JSON.stringify(usuario.value))
    }
  }

  async function logout() {
    // Invalidar el token en el servidor antes de borrarlo localmente.
    // Si la petición falla (red, token ya expirado), el finally limpia igual.
    try {
      await api.post('/auth/logout')
    } catch {
      // Silencioso — el servidor puede estar caído o el token ya expiró
    } finally {
      token.value = null
      usuario.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('usuario')
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('usuario')
      router.push('/login')
    }
  }

  return {
    token, usuario, isAuthenticated,
    esAdmin, esAuxiliar, esVendedor, esAdminOAux, primerLogin,
    login, cambiarPassword, logout
  }
})
