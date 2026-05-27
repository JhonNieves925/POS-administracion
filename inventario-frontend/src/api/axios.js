import axios from 'axios'
import router from '@/router'

// En desarrollo: Vite proxy redirige /api → localhost:8080
// En producción: VITE_API_BASE_URL apunta al backend de Render
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

// Inyectar JWT en cada request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Respuesta: desenvolver ApiResponse automaticamente
// El backend responde: { success, mensaje, data }
// Con este interceptor, res.data ya contiene el campo "data" directamente
api.interceptors.response.use(
  response => {
    // Si es blob (PDF, Excel), devolver tal cual
    if (response.config.responseType === 'blob') return response
    // Desenvolver ApiResponse
    if (response.data && typeof response.data === 'object' && 'data' in response.data) {
      response.data = response.data.data
    }
    return response
  },
  error => {
    // Si es 401 y NO viene del endpoint de login → sesion expirada, redirigir
    const esEndpointLogin = error.config?.url?.includes('/auth/login')
    if (error.response?.status === 401 && !esEndpointLogin) {
      localStorage.removeItem('token')
      localStorage.removeItem('usuario')
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('usuario')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default api
