<template>
  <div class="config-page">

    <!-- ── Backups ── -->
    <div class="config-section">
      <div class="section-title">
        <i class="pi pi-database" />
        <h2>Copias de seguridad (Backups)</h2>
      </div>
      <p class="section-desc">
        El sistema genera automáticamente una copia de la base de datos <strong>todos los días a las 2:00 AM</strong>.
        También puedes generar una copia manualmente en cualquier momento.
      </p>

      <!-- Tarjetas de estado -->
      <div class="stats-grid" v-if="config">
        <div class="stat-card">
          <i class="pi pi-copy" style="color:#1565C0" />
          <div class="stat-info">
            <span class="stat-num">{{ config.cantidadBackups }}</span>
            <span class="stat-label">Copias almacenadas</span>
          </div>
        </div>
        <div class="stat-card">
          <i class="pi pi-server" style="color:#2E7D32" />
          <div class="stat-info">
            <span class="stat-num">{{ formatKb(config.espacioUsadoKb) }}</span>
            <span class="stat-label">Espacio utilizado</span>
          </div>
        </div>
        <div class="stat-card">
          <i class="pi pi-calendar" style="color:#E65100" />
          <div class="stat-info">
            <span class="stat-num">{{ config.retenerDias }} días</span>
            <span class="stat-label">Retención de backups</span>
          </div>
        </div>
        <div class="stat-card">
          <i :class="config.habilitado ? 'pi pi-check-circle' : 'pi pi-times-circle'"
             :style="{ color: config.habilitado ? '#2E7D32' : '#C62828' }" />
          <div class="stat-info">
            <span class="stat-num">{{ config.habilitado ? 'Activo' : 'Inactivo' }}</span>
            <span class="stat-label">Backup automático</span>
          </div>
        </div>
      </div>

      <!-- Directorio -->
      <div class="dir-info" v-if="config">
        <i class="pi pi-folder" />
        <span>Carpeta de backups: <code>{{ config.directorio }}</code></span>
      </div>

      <!-- Acciones -->
      <div class="backup-acciones">
        <Button
          label="Hacer backup ahora"
          icon="pi pi-download"
          :loading="ejecutando"
          @click="ejecutarBackup"
        />
        <Button
          label="Limpiar backups antiguos"
          icon="pi pi-trash"
          severity="warn"
          outlined
          :loading="limpiando"
          @click="limpiarBackups"
        />
        <Button
          label="Actualizar lista"
          icon="pi pi-refresh"
          severity="secondary"
          outlined
          @click="cargarDatos"
          :loading="cargando"
        />
      </div>

      <!-- Resultado del último backup manual -->
      <div v-if="ultimoResultado" class="resultado-backup" :class="ultimoResultado.exito ? 'res-ok' : 'res-error'">
        <i :class="ultimoResultado.exito ? 'pi pi-check-circle' : 'pi pi-times-circle'" />
        <div>
          <div class="res-titulo">{{ ultimoResultado.exito ? '¡Backup completado!' : 'Error en el backup' }}</div>
          <div class="res-detalle" v-if="ultimoResultado.exito">
            Archivo: <strong>{{ ultimoResultado.archivo }}</strong> · {{ ultimoResultado.tamanoKb }} KB
          </div>
          <div class="res-detalle" v-else>{{ ultimoResultado.error }}</div>
        </div>
      </div>

      <!-- Lista de backups -->
      <div class="backups-lista" v-if="backups.length > 0">
        <div class="lista-header">
          <span>Archivo</span>
          <span>Tipo</span>
          <span>Tamaño</span>
          <span>Fecha</span>
          <span></span>
        </div>
        <div
          class="lista-row"
          v-for="b in backups"
          :key="b.archivo"
        >
          <span class="archivo-nombre">
            <i class="pi pi-file" style="color:#1565C0;margin-right:0.4rem" />
            {{ b.archivo }}
          </span>
          <span>
            <Tag
              :value="b.tipo === 'manual' ? 'Manual' : 'Automático'"
              :severity="b.tipo === 'manual' ? 'info' : 'success'"
              style="font-size:0.72rem"
            />
          </span>
          <span class="archivo-size">{{ formatKb(b.tamanoKb) }}</span>
          <span class="archivo-fecha">{{ formatFecha(b.fechaModificacion) }}</span>
          <span>
            <Button
              icon="pi pi-download"
              size="small"
              text
              rounded
              severity="info"
              v-tooltip.left="'Descargar backup'"
              :loading="descargando[b.archivo]"
              @click="descargarBackup(b.archivo)"
            />
          </span>
        </div>
      </div>

      <div v-else-if="!cargando" class="lista-vacia">
        <i class="pi pi-inbox" style="font-size:2rem;color:#ccc" />
        <p>No hay backups todavía. Haz clic en "Hacer backup ahora".</p>
      </div>
    </div>

    <!-- ── Cuentas PUC ── -->
    <div class="config-section">
      <div class="section-title">
        <i class="pi pi-book" />
        <h2>Cuentas Contables (PUC)</h2>
      </div>
      <p class="section-desc">
        Configure las cuentas del <strong>Plan Único de Cuentas</strong> que usa el contador en Siigo.
        Estas cuentas se usan para generar la <strong>Relación de Ventas</strong> en Reportes.
      </p>

      <div v-if="loadingPuc" class="puc-loading">
        <i class="pi pi-spin pi-spinner" /> Cargando cuentas...
      </div>

      <div v-else class="puc-tabla">
        <div class="puc-head">
          <span>Concepto</span>
          <span>Cuenta PUC</span>
          <span>Nombre de la cuenta</span>
          <span>Grupo</span>
        </div>
        <div class="puc-row" v-for="cfg in cuentasPuc" :key="cfg.clave">
          <span class="puc-descripcion">{{ cfg.descripcion }}</span>
          <input
            v-model="cfg.cuentaPuc"
            class="puc-input"
            placeholder="ej: 4135"
            maxlength="15"
          />
          <input
            v-model="cfg.nombreCuenta"
            class="puc-input puc-nombre"
            placeholder="Nombre de la cuenta"
            maxlength="100"
          />
          <span>
            <Tag :value="cfg.grupo" :severity="grupoPucSeverity(cfg.grupo)" style="font-size:0.7rem" />
          </span>
        </div>
      </div>

      <div class="backup-acciones" style="margin-top:1rem">
        <Button
          label="Guardar cuentas PUC"
          icon="pi pi-save"
          :loading="guardandoPuc"
          @click="guardarPuc"
        />
        <Button
          label="Restaurar valores por defecto"
          icon="pi pi-refresh"
          severity="secondary"
          outlined
          @click="restaurarPucDefaults"
        />
      </div>
    </div>

    <!-- ── Para desarrolladores ── -->
    <div class="config-section dev-section">
      <div class="section-title">
        <i class="pi pi-code" />
        <h2>Para desarrolladores</h2>
        <span class="dev-badge">DEV</span>
      </div>
      <p class="section-desc">
        Herramientas técnicas para configurar la integración con Siigo.
        Estas opciones no afectan el funcionamiento diario del sistema.
      </p>

      <div class="dev-card">
        <div class="dev-card-info">
          <i class="pi pi-sliders-h" style="color:#6A1B9A; font-size:1.4rem" />
          <div>
            <div class="dev-card-titulo">Consultar IDs de configuración — Siigo API</div>
            <div class="dev-card-desc">
              Conecta con tu cuenta de Siigo y obtiene los IDs exactos de tipos de documento,
              impuestos y métodos de pago. Úsalo para configurar las variables de entorno
              en Render antes de activar la facturación electrónica real.
            </div>
          </div>
        </div>
        <Button
          label="Consultar configuración Siigo"
          icon="pi pi-search"
          severity="secondary"
          outlined
          :loading="configCargando"
          @click="abrirConfigSiigo"
        />
      </div>

      <!-- Resultado inline -->
      <div v-if="configData" class="dev-resultado">

        <!-- Modo mock -->
        <div v-if="!configData.modoReal" class="dev-mock-aviso">
          <i class="pi pi-exclamation-triangle" />
          <div>
            <strong>Servidor en modo MOCK</strong><br>
            <span>{{ configData.mensaje }}</span>
          </div>
        </div>

        <!-- Modo real -->
        <template v-else>
          <div class="dev-exito">
            <i class="pi pi-check-circle" />
            Conexión con Siigo exitosa — copia los valores en Render.
          </div>

          <!-- Tipos de documento -->
          <div class="dev-subsection">
            <div class="dev-sub-titulo"><i class="pi pi-file-edit" /> Tipos de documento</div>
            <table class="dev-table">
              <thead><tr><th>ID</th><th>Tipo</th><th>Nombre en Siigo</th><th>Variable en Render</th></tr></thead>
              <tbody>
                <tr v-for="d in configData.tiposDocumento" :key="d.id">
                  <td><code class="dev-id">{{ d.id }}</code></td>
                  <td><span class="dev-badge-tipo">{{ d.tipo }}</span></td>
                  <td>{{ d.nombre }}</td>
                  <td><code v-if="d.variableEntorno" class="dev-var">{{ d.variableEntorno }}={{ d.id }}</code></td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Impuestos -->
          <div class="dev-subsection">
            <div class="dev-sub-titulo"><i class="pi pi-percentage" /> Impuestos</div>
            <table class="dev-table">
              <thead><tr><th>ID</th><th>Nombre</th><th>%</th><th>Variable en Render</th></tr></thead>
              <tbody>
                <tr v-for="t in configData.impuestos" :key="t.id">
                  <td><code class="dev-id">{{ t.id }}</code></td>
                  <td>{{ t.nombre }}</td>
                  <td>{{ t.porcentaje }}%</td>
                  <td>
                    <code v-if="t.variableEntorno" class="dev-var">{{ t.variableEntorno }}={{ t.id }}</code>
                    <span v-else class="dev-no-usa">— no aplica</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Métodos de pago -->
          <div class="dev-subsection">
            <div class="dev-sub-titulo"><i class="pi pi-wallet" /> Métodos de pago</div>
            <table class="dev-table">
              <thead><tr><th>ID</th><th>Nombre</th><th>Variable en Render</th></tr></thead>
              <tbody>
                <tr v-for="p in configData.metodosPago" :key="p.id">
                  <td><code class="dev-id">{{ p.id }}</code></td>
                  <td>{{ p.nombre }}</td>
                  <td>
                    <code v-if="p.variableEntorno" class="dev-var">{{ p.variableEntorno }}={{ p.id }}</code>
                    <span v-else class="dev-no-usa">— no aplica</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Bloque copy-paste para Render -->
          <div class="dev-subsection">
            <div class="dev-sub-titulo"><i class="pi pi-copy" /> Pega esto en Render → Environment Variables</div>
            <pre class="dev-env-block">{{ generarEnvBlock(configData) }}</pre>
          </div>
        </template>
      </div>
    </div>

    <!-- ── Info del sistema ── -->
    <div class="config-section">
      <div class="section-title">
        <i class="pi pi-info-circle" />
        <h2>Información del sistema</h2>
      </div>
      <div class="sys-info-grid">
        <div class="sys-item">
          <span class="sys-label">Empresa</span>
          <span class="sys-val">DISTRIASOCIADOS SAS</span>
        </div>
        <div class="sys-item">
          <span class="sys-label">NIT</span>
          <span class="sys-val">901.897.762-1</span>
        </div>
        <div class="sys-item">
          <span class="sys-label">Resolución DIAN</span>
          <span class="sys-val">18764089740671</span>
        </div>
        <div class="sys-item">
          <span class="sys-label">Prefijo facturación</span>
          <span class="sys-val">FEV</span>
        </div>
        <div class="sys-item">
          <span class="sys-label">Versión backend</span>
          <span class="sys-val">Spring Boot 3.5 · Java 21</span>
        </div>
        <div class="sys-item">
          <span class="sys-label">Base de datos</span>
          <span class="sys-val">PostgreSQL</span>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import api from '@/api/axios'
import Button from 'primevue/button'
import Tag from 'primevue/tag'

// ── Configuración Siigo (sección Para desarrolladores) ────────
const configCargando = ref(false)
const configData     = ref(null)

async function abrirConfigSiigo() {
  configCargando.value = true
  configData.value     = null
  try {
    const res = await api.get('/siigo/configuracion')
    configData.value = res.data.data ?? res.data
  } catch (e) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: e.response?.data?.mensaje || 'No se pudo consultar la configuración de Siigo',
      life: 5000
    })
  } finally {
    configCargando.value = false
  }
}

function generarEnvBlock(cfg) {
  if (!cfg?.modoReal) return ''
  const lines = []
  cfg.tiposDocumento?.forEach(d => {
    if (d.variableEntorno) lines.push(`${d.variableEntorno}=${d.id}   # ${d.nombre} (${d.tipo})`)
  })
  cfg.impuestos?.forEach(t => {
    if (t.variableEntorno) lines.push(`${t.variableEntorno}=${t.id}   # ${t.nombre} (${t.porcentaje}%)`)
  })
  cfg.metodosPago?.forEach(p => {
    if (p.variableEntorno) lines.push(`${p.variableEntorno}=${p.id}   # ${p.nombre}`)
  })
  lines.push('')
  lines.push('# Activar facturación real a la DIAN:')
  lines.push('FACTURACION_PROVEEDOR=siigo')
  return lines.join('\n')
}

const toast = useToast()
const config    = ref(null)
const backups   = ref([])
const cargando  = ref(false)
const ejecutando = ref(false)
const limpiando  = ref(false)
const ultimoResultado = ref(null)
const descargando = ref({})

// ── PUC ──
const cuentasPuc   = ref([])
const loadingPuc   = ref(false)
const guardandoPuc = ref(false)

async function cargarDatos() {
  cargando.value = true
  try {
    const [resConfig, resLista] = await Promise.allSettled([
      api.get('/backup/config'),
      api.get('/backup/listar')
    ])
    if (resConfig.status === 'fulfilled') config.value = resConfig.value.data
    if (resLista.status === 'fulfilled')  backups.value = resLista.value.data || []
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo cargar la configuración', life: 4000 })
  } finally {
    cargando.value = false
  }
}

async function ejecutarBackup() {
  ejecutando.value = true
  ultimoResultado.value = null
  try {
    const res = await api.post('/backup/ejecutar')
    // res.data ya está desenvuelto por el interceptor → es el Map del resultado
    const datos = res.data || {}
    ultimoResultado.value = { exito: true, ...datos }
    toast.add({ severity: 'success', summary: '¡Backup completado!', detail: `Archivo: ${datos.archivo}`, life: 5000 })
    await cargarDatos()
  } catch (e) {
    // El backend devuelve HTTP 500 cuando pg_dump falla
    // e.response.data es el ApiResponse sin desenvolver (ya que es un error HTTP)
    const apiResp = e.response?.data
    let msg = 'No se pudo completar el backup'
    if (apiResp) {
      // ApiResponse tiene estructura: { success, mensaje, data }
      msg = apiResp.mensaje || apiResp.message || apiResp.error || msg
      // Quitar prefijo redundante si viene
      msg = msg.replace(/^Error al realizar backup:\s*/i, '')
    }
    ultimoResultado.value = { exito: false, error: msg }
    toast.add({ severity: 'error', summary: 'Error en backup', detail: msg, life: 8000 })
  } finally {
    ejecutando.value = false
  }
}

async function limpiarBackups() {
  limpiando.value = true
  try {
    const res = await api.delete('/backup/limpiar')
    const elim = res.data?.backupsEliminados ?? 0
    toast.add({
      severity: elim > 0 ? 'warn' : 'info',
      summary: 'Limpieza completada',
      detail: elim > 0 ? `${elim} backup(s) antiguo(s) eliminado(s)` : 'No había backups antiguos para eliminar',
      life: 4000
    })
    await cargarDatos()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo limpiar', life: 4000 })
  } finally {
    limpiando.value = false
  }
}

async function descargarBackup(nombreArchivo) {
  descargando.value[nombreArchivo] = true
  try {
    const res = await api.get(`/backup/descargar/${encodeURIComponent(nombreArchivo)}`, {
      responseType: 'blob'
    })
    // Crear enlace temporal y disparar descarga
    const url = URL.createObjectURL(new Blob([res.data], { type: 'application/octet-stream' }))
    const link = document.createElement('a')
    link.href = url
    link.download = nombreArchivo
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    setTimeout(() => URL.revokeObjectURL(url), 10_000)
    toast.add({ severity: 'success', summary: 'Descarga iniciada', detail: nombreArchivo, life: 3000 })
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo descargar el backup', life: 4000 })
  } finally {
    descargando.value[nombreArchivo] = false
  }
}

function formatKb(kb) {
  if (!kb && kb !== 0) return '—'
  if (kb >= 1024) return (kb / 1024).toFixed(1) + ' MB'
  return kb + ' KB'
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}

async function cargarPuc() {
  loadingPuc.value = true
  try {
    const res = await api.get('/contabilidad/cuentas')
    // Clonar para poder restaurar
    cuentasPuc.value = (res.data || []).map(c => ({ ...c }))
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudieron cargar las cuentas PUC', life: 4000 })
  } finally {
    loadingPuc.value = false
  }
}

async function guardarPuc() {
  guardandoPuc.value = true
  try {
    await api.put('/contabilidad/cuentas', cuentasPuc.value)
    toast.add({ severity: 'success', summary: 'Guardado', detail: 'Cuentas PUC actualizadas correctamente', life: 3000 })
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudieron guardar las cuentas PUC', life: 4000 })
  } finally {
    guardandoPuc.value = false
  }
}

async function restaurarPucDefaults() {
  await cargarPuc()
  toast.add({ severity: 'info', summary: 'Valores recargados', detail: 'Se recargarón los valores actuales desde el servidor', life: 3000 })
}

function grupoPucSeverity(grupo) {
  const map = {
    'INGRESOS': 'success',
    'ACTIVO':   'info',
    'PASIVO':   'warn',
    'COSTO':    'danger',
  }
  return map[grupo] ?? 'secondary'
}

onMounted(() => {
  cargarDatos()
  cargarPuc()
})
</script>

<style scoped>
.config-page {
  display: flex; flex-direction: column; gap: 1.5rem;
}

.config-section {
  background: white; border-radius: 14px; padding: 1.5rem;
  box-shadow: 0 2px 10px rgba(0,0,0,0.06);
  display: flex; flex-direction: column; gap: 1rem;
}

.section-title {
  display: flex; align-items: center; gap: 0.75rem;
}
.section-title i { font-size: 1.3rem; color: #1565C0; }
.section-title h2 { margin: 0; font-size: 1.1rem; color: #222; }

.section-desc { font-size: 0.88rem; color: #666; margin: 0; }

/* Stats */
.stats-grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 0.75rem;
}
.stat-card {
  background: #f8f9fa; border-radius: 10px; padding: 1rem;
  display: flex; align-items: center; gap: 0.75rem;
}
.stat-card i { font-size: 1.6rem; }
.stat-info { display: flex; flex-direction: column; }
.stat-num { font-size: 1.1rem; font-weight: 800; color: #222; }
.stat-label { font-size: 0.72rem; color: #888; text-transform: uppercase; letter-spacing: 0.4px; }

/* Directorio */
.dir-info {
  display: flex; align-items: center; gap: 0.5rem;
  background: #EEF2FF; border-radius: 8px; padding: 0.6rem 0.9rem;
  font-size: 0.85rem; color: #333;
}
.dir-info code { background: #dce5ff; padding: 2px 6px; border-radius: 4px; font-size: 0.82rem; }

/* Acciones */
.backup-acciones {
  display: flex; gap: 0.75rem; flex-wrap: wrap;
}

/* Resultado */
.resultado-backup {
  display: flex; align-items: flex-start; gap: 0.75rem;
  border-radius: 10px; padding: 0.9rem 1rem;
}
.res-ok    { background: #E8F5E9; border-left: 4px solid #2E7D32; }
.res-error { background: #FFEBEE; border-left: 4px solid #C62828; }
.res-ok    i { color: #2E7D32; font-size: 1.3rem; margin-top: 2px; }
.res-error i { color: #C62828; font-size: 1.3rem; margin-top: 2px; }
.res-titulo { font-weight: 700; font-size: 0.95rem; color: #222; }
.res-detalle { font-size: 0.83rem; color: #555; margin-top: 0.2rem; }

/* Lista de backups */
.backups-lista {
  border: 1px solid #eee; border-radius: 10px; overflow: hidden;
}
.lista-header {
  display: grid; grid-template-columns: 2fr 1fr 0.8fr 1.4fr 2.5rem;
  background: #f5f5f5; padding: 0.5rem 1rem;
  font-size: 0.75rem; font-weight: 700; color: #888; text-transform: uppercase;
}
.lista-row {
  display: grid; grid-template-columns: 2fr 1fr 0.8fr 1.4fr 2.5rem;
  padding: 0.6rem 1rem; border-top: 1px solid #f0f0f0;
  font-size: 0.85rem; align-items: center;
}
.lista-row:hover { background: #fafafa; }
.archivo-nombre { color: #333; display: flex; align-items: center; word-break: break-all; }
.archivo-size { color: #555; }
.archivo-fecha { color: #888; font-size: 0.8rem; }

.lista-vacia {
  text-align: center; padding: 2.5rem; color: #aaa;
  display: flex; flex-direction: column; align-items: center; gap: 0.5rem;
}

/* PUC */
.puc-loading {
  color: #888; font-size: 0.88rem; display: flex; align-items: center; gap: 0.5rem;
}
.puc-tabla {
  border: 1px solid #e8e8e8; border-radius: 10px; overflow: hidden;
}
.puc-head {
  display: grid;
  grid-template-columns: 2.2fr 1fr 2fr 1fr;
  background: #f0f4ff; padding: 0.55rem 1rem;
  font-size: 0.73rem; font-weight: 700; color: #555; text-transform: uppercase; letter-spacing: 0.4px;
}
.puc-row {
  display: grid;
  grid-template-columns: 2.2fr 1fr 2fr 1fr;
  padding: 0.5rem 1rem; border-top: 1px solid #f2f2f2;
  align-items: center; gap: 0.5rem;
}
.puc-row:hover { background: #fafbff; }
.puc-descripcion { font-size: 0.86rem; color: #333; }
.puc-input {
  border: 1px solid #dce1f0; border-radius: 6px; padding: 0.35rem 0.6rem;
  font-size: 0.85rem; width: 100%; outline: none; color: #222;
  transition: border-color 0.15s;
}
.puc-input:focus { border-color: #1565C0; box-shadow: 0 0 0 2px rgba(21,101,192,0.12); }
.puc-nombre { }

/* Para desarrolladores */
.dev-section { border-left: 4px solid #6A1B9A; }

.dev-badge {
  background: #6A1B9A;
  color: white;
  font-size: 0.65rem;
  font-weight: 800;
  padding: 0.1rem 0.45rem;
  border-radius: 4px;
  letter-spacing: 1px;
  margin-left: 0.25rem;
}

.dev-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  background: #F9F5FF;
  border: 1px solid #E1BEE7;
  border-radius: 10px;
  padding: 1rem 1.25rem;
  flex-wrap: wrap;
}
.dev-card-info {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  flex: 1;
  min-width: 240px;
}
.dev-card-titulo {
  font-weight: 700;
  font-size: 0.9rem;
  color: #333;
  margin-bottom: 0.25rem;
}
.dev-card-desc {
  font-size: 0.82rem;
  color: #666;
  line-height: 1.5;
}

.dev-resultado {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  border: 1px solid #E1BEE7;
  border-radius: 10px;
  padding: 1.25rem;
  background: #fdfbff;
}

.dev-mock-aviso {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  background: #FFF8E1;
  border-left: 4px solid #F9A825;
  border-radius: 0 8px 8px 0;
  padding: 0.9rem 1rem;
  font-size: 0.87rem;
  color: #5D4037;
}
.dev-mock-aviso i { color: #F9A825; font-size: 1.3rem; margin-top: 2px; }

.dev-exito {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: #E8F5E9;
  border-radius: 8px;
  padding: 0.65rem 1rem;
  font-size: 0.87rem;
  color: #1B5E20;
  font-weight: 500;
}
.dev-exito i { color: #2E7D32; }

.dev-subsection {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.dev-sub-titulo {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-weight: 700;
  font-size: 0.83rem;
  color: #555;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.dev-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.83rem;
  border: 1px solid #eee;
  border-radius: 8px;
  overflow: hidden;
}
.dev-table th {
  text-align: left;
  font-size: 0.72rem;
  font-weight: 700;
  color: #888;
  text-transform: uppercase;
  padding: 0.4rem 0.75rem;
  background: #f5f5f5;
}
.dev-table td {
  padding: 0.45rem 0.75rem;
  border-top: 1px solid #f5f5f5;
  vertical-align: middle;
}

.dev-id {
  background: #6A1B9A;
  color: white;
  padding: 0.15rem 0.55rem;
  border-radius: 4px;
  font-size: 0.85rem;
  font-weight: 700;
  font-family: monospace;
}
.dev-var {
  background: #F3E5F5;
  color: #6A1B9A;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  font-size: 0.78rem;
  font-family: monospace;
  word-break: break-all;
}
.dev-badge-tipo {
  background: #E3F2FD;
  color: #1565C0;
  padding: 0.1rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 700;
}
.dev-no-usa { font-size: 0.78rem; color: #bbb; font-style: italic; }

.dev-env-block {
  background: #1E1E1E;
  color: #A8FF78;
  border-radius: 8px;
  padding: 1rem 1.25rem;
  font-family: 'Courier New', monospace;
  font-size: 0.8rem;
  line-height: 1.9;
  overflow-x: auto;
  white-space: pre;
  margin: 0;
}

/* Info sistema */
.sys-info-grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0.75rem;
}
.sys-item {
  background: #f8f9fa; border-radius: 8px; padding: 0.75rem 1rem;
  display: flex; flex-direction: column; gap: 0.2rem;
}
.sys-label { font-size: 0.72rem; color: #999; text-transform: uppercase; letter-spacing: 0.4px; }
.sys-val { font-size: 0.9rem; font-weight: 600; color: #333; }
</style>
