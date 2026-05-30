<template>
  <div class="compras-page">

    <!-- Toolbar principal -->
    <div class="page-toolbar">
      <div class="toolbar-left">
        <div class="field-inline">
          <label>Desde</label>
          <Calendar v-model="filtros.desde" dateFormat="dd/mm/yy" showIcon />
        </div>
        <div class="field-inline">
          <label>Hasta</label>
          <Calendar v-model="filtros.hasta" dateFormat="dd/mm/yy" showIcon />
        </div>
        <Button label="Buscar" icon="pi pi-search" outlined @click="cargarCompras" :loading="cargando" />
      </div>
      <div class="toolbar-right">
        <Button
          label="Importar XML factura"
          icon="pi pi-file-import"
          outlined
          severity="secondary"
          @click="abrirImportDian"
          v-tooltip.bottom="'Sube el archivo XML de la factura electrónica del proveedor'"
        />
        <Button label="Registrar compra" icon="pi pi-plus" @click="abrirNuevaCompra" />
      </div>
    </div>

    <!-- KPIs del período -->
    <div class="kpi-row" v-if="compras.length">
      <div class="kpi-card">
        <span class="kpi-val">{{ compras.length }}</span>
        <span class="kpi-lbl">Compras en el período</span>
      </div>
      <div class="kpi-card kpi-card-blue">
        <span class="kpi-val">{{ formatCOP(totalPeriodo) }}</span>
        <span class="kpi-lbl">Total invertido</span>
      </div>
      <div class="kpi-card kpi-card-green">
        <span class="kpi-val">{{ totalProductosIngresados }}</span>
        <span class="kpi-lbl">Líneas de producto ingresadas</span>
      </div>
    </div>

    <!-- Tabla de compras -->
    <div class="card-table">
      <DataTable
        :value="compras"
        :loading="cargando"
        v-model:expandedRows="expandedRows"
        dataKey="id"
        stripedRows
        paginator
        :rows="15"
        paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
        :rowsPerPageOptions="[10, 15, 25]"
        responsiveLayout="scroll"
      >
        <!-- Expansor -->
        <Column expander style="width:3rem" />

        <Column header="Fecha" sortable field="fecha">
          <template #body="{ data }">{{ formatFecha(data.fecha) }}</template>
        </Column>
        <Column header="Proveedor" field="proveedor" sortable style="min-width:180px" />
        <Column header="N° Factura proveedor" field="numeroFacturaProveedor">
          <template #body="{ data }">
            <span v-if="data.numeroFacturaProveedor" class="tag-factura">{{ data.numeroFacturaProveedor }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </Column>
        <Column header="Productos">
          <template #body="{ data }">
            <span class="badge-lineas">{{ data.detalles?.length ?? 0 }} líneas</span>
          </template>
        </Column>
        <Column header="Total" sortable field="total">
          <template #body="{ data }">
            <strong class="total-cell">{{ formatCOP(data.total) }}</strong>
          </template>
        </Column>
        <Column header="Registrado por">
          <template #body="{ data }">{{ data.registradoPor?.nombre ?? '—' }}</template>
        </Column>

        <!-- Detalle expandible: líneas de la compra -->
        <template #expansion="{ data }">
          <div class="expansion-body">
            <div class="expansion-header">
              <i class="pi pi-list" />
              <span>Detalle de la compra — {{ data.proveedor }} · {{ formatFecha(data.fecha) }}</span>
              <span v-if="data.observaciones" class="obs-chip">
                <i class="pi pi-comment" /> {{ data.observaciones }}
              </span>
            </div>
            <DataTable :value="data.detalles" size="small" stripedRows>
              <Column header="Producto">
                <template #body="{ data: d }">{{ d.producto?.nombreCompleto ?? '—' }}</template>
              </Column>
              <Column header="Cajas ingresadas" field="cantidad" />
              <Column header="Precio / caja">
                <template #body="{ data: d }">{{ formatCOP(d.precioCaja) }}</template>
              </Column>
              <Column header="Subtotal">
                <template #body="{ data: d }">
                  <strong>{{ formatCOP(d.precioCaja * d.cantidad) }}</strong>
                </template>
              </Column>
            </DataTable>
          </div>
        </template>

        <template #empty>
          <div class="empty-state">
            <i class="pi pi-inbox" style="font-size:2rem;color:#ccc" />
            <p>No hay compras registradas en este período</p>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- ════════════════════════════════════════════════════════
         Dialog — Importar desde DIAN (CUFE o XML)
         ════════════════════════════════════════════════════════ -->
    <Dialog
      v-model:visible="dianDialog.visible"
      header="Importar factura XML del proveedor"
      modal
      :style="{ width: '760px' }"
      :draggable="false"
      :closable="!dianDialog.cargando"
    >
      <div class="dian-import">

        <!-- ── PASO 1: subir XML ── -->
        <div v-if="dianDialog.paso === 1">

          <p class="dian-intro">
            <i class="pi pi-info-circle" style="color:#1565C0" />
            Sube el archivo <strong>.xml</strong> que viene junto con la factura electrónica
            del proveedor. El sistema extrae automáticamente: proveedor, fecha, número de
            factura y todos los productos con cantidades y precios.
          </p>

          <div class="field" style="margin-top:1.25rem">
            <label>Archivo XML de la factura *</label>
            <div
              class="dian-dropzone"
              @dragover.prevent
              @drop.prevent="onXmlDrop"
              @click="$refs.xmlInput.click()"
            >
              <i class="pi pi-upload" style="font-size:1.6rem;color:#aaa" />
              <span v-if="!dianDialog.xmlFile">
                Arrastra aquí el archivo .xml o haz clic para seleccionarlo
              </span>
              <span v-else class="dian-xml-nombre">
                <i class="pi pi-file-edit" style="color:#2E7D32" />
                {{ dianDialog.xmlFile.name }}
              </span>
            </div>
            <input
              ref="xmlInput"
              type="file"
              accept=".xml,application/xml,text/xml"
              style="display:none"
              @change="onXmlSelect"
            />
            <small class="hint" style="margin-top:0.5rem;display:block">
              El archivo .xml llega junto con la factura electrónica del proveedor,
              generalmente en un .zip que también contiene el PDF de la factura.
            </small>
          </div>

          <Message v-if="dianDialog.error" severity="error" :closable="false">
            {{ dianDialog.error }}
          </Message>
        </div>

        <!-- ── PASO 2: Revisión y formulario ── -->
        <div v-if="dianDialog.paso === 2">

          <!-- Banner duplicado -->
          <Message v-if="dianDialog.resultado?.duplicado" severity="warn" :closable="false" style="margin-bottom:1rem">
            <strong>⚠️ Factura posiblemente duplicada.</strong>
            Esta factura (CUFE) ya fue registrada anteriormente en el sistema.
            Revisa el historial antes de continuar.
          </Message>

          <!-- Banner XML completo -->
          <Message v-if="dianDialog.resultado?.fuente === 'XML'" severity="success" :closable="false" style="margin-bottom:1rem">
            <strong>✅ Datos importados desde el XML.</strong>
            Se encontraron {{ dianDialog.resultado.lineas?.length }} línea(s) de producto.
            Verifica que el producto asignado en cada línea sea correcto antes de guardar.
          </Message>

          <!-- Encabezado de la factura -->
          <div class="dian-header-form">
            <div class="field">
              <label>Proveedor *</label>
              <InputText v-model="form.proveedor" class="w-full" placeholder="Nombre del proveedor" @blur="cargarSugerencias" />
            </div>
            <div class="field">
              <label>Fecha *</label>
              <Calendar v-model="form.fecha" dateFormat="dd/mm/yy" showIcon class="w-full" />
            </div>
            <div class="field">
              <label>N° Factura proveedor</label>
              <InputText v-model="form.numeroFacturaProveedor" class="w-full" placeholder="Ej: FE-0001-245" />
            </div>
          </div>
          <div class="field" style="margin-bottom:1rem">
            <label>Observaciones</label>
            <Textarea v-model="form.observaciones" class="w-full" rows="1" autoResize
              :placeholder="dianDialog.resultado?.cufe ? 'CUFE: ' + dianDialog.resultado.cufe.substring(0, 16) + '...' : ''" />
          </div>

          <!-- ── Líneas desde XML ── -->
          <div v-if="dianDialog.resultado?.lineas?.length" class="dian-lineas-section">
            <div class="productos-header">
              <span><i class="pi pi-box" /> Productos detectados en el XML</span>
              <Button label="Agregar línea" icon="pi pi-plus" size="small" outlined @click="agregarLinea" />
            </div>

            <div class="lineas-cols-header dian-cols">
              <span>Descripción en la factura</span>
              <span>Producto en el sistema</span>
              <span>Cajas</span>
              <span>Precio / caja</span>
              <span>Subtotal</span>
              <span></span>
            </div>

            <div v-for="(linea, i) in form.detalles" :key="i" class="linea-row dian-linea-row">
              <!-- Descripción DIAN -->
              <div class="dian-desc-cell">
                <span class="dian-desc-text">{{ dianDialog.resultado.lineas[i]?.descripcionDian || '—' }}</span>
              </div>
              <!-- Producto del sistema -->
              <div class="linea-producto">
                <Select
                  v-model="linea.productoId"
                  :options="productos"
                  optionValue="id"
                  optionLabel="nombreCompleto"
                  placeholder="Seleccionar..."
                  filter
                  filterPlaceholder="Buscar..."
                  class="w-full"
                  :class="{ 'p-invalid': linea.error }"
                  @change="linea.error = ''"
                />
                <small class="p-error" v-if="linea.error">{{ linea.error }}</small>
              </div>
              <!-- Cantidad -->
              <InputNumber v-model="linea.cantidad" :min="1" class="w-full" inputClass="w-full" />
              <!-- Precio -->
              <InputNumber v-model="linea.precioCaja" mode="currency" currency="COP" locale="es-CO" :min="0" class="w-full" inputClass="w-full" />
              <!-- Subtotal -->
              <span class="subtotal-val">{{ formatCOP((linea.cantidad || 0) * (linea.precioCaja || 0)) }}</span>
              <!-- Quitar -->
              <Button icon="pi pi-trash" text rounded severity="danger" size="small" @click="quitarLinea(i)" />
            </div>
          </div>

          <!-- ── Sugerencias del historial (modo CUFE) ── -->
          <div v-else class="dian-sugerencias-section">
            <div class="productos-header">
              <span><i class="pi pi-box" /> Productos a registrar</span>
              <Button label="Agregar producto" icon="pi pi-plus" size="small" outlined @click="agregarLinea" />
            </div>

            <!-- Chips de sugerencias del historial -->
            <div v-if="dianDialog.sugerencias.length" class="sugerencias-chips">
              <span class="sugerencias-titulo">
                <i class="pi pi-history" /> Últimas compras a este proveedor — clic para agregar:
              </span>
              <div class="chips-row">
                <button
                  v-for="s in dianDialog.sugerencias"
                  :key="s.productoId"
                  class="chip-sugerencia"
                  :class="{ 'chip-ya-agregado': form.detalles.some(d => d.productoId === s.productoId) }"
                  @click="agregarSugerencia(s)"
                  :disabled="form.detalles.some(d => d.productoId === s.productoId)"
                >
                  <span class="chip-nombre">{{ s.nombreProducto }}</span>
                  <span class="chip-precio">{{ formatCOP(s.ultimoPrecioCaja) }}/caja</span>
                </button>
              </div>
            </div>

            <small class="p-error" v-if="errores.detalles">{{ errores.detalles }}</small>

            <div v-if="form.detalles.length === 0" class="empty-lineas">
              <i class="pi pi-arrow-up" />
              <span>Haz clic en una sugerencia o en "Agregar producto" para ingresar la mercancía</span>
            </div>

            <div v-if="form.detalles.length > 0" class="lineas-cols-header">
              <span></span><span>Producto</span><span>Cajas</span><span>Precio / caja</span><span>Subtotal</span><span></span>
            </div>

            <div v-for="(linea, i) in form.detalles" :key="i" class="linea-row">
              <div class="linea-num">{{ i + 1 }}</div>
              <div class="linea-producto">
                <Select v-model="linea.productoId" :options="productos" optionValue="id" optionLabel="nombreCompleto"
                  placeholder="Seleccionar producto..." filter filterPlaceholder="Buscar producto..." class="w-full"
                  :class="{ 'p-invalid': linea.error }" @change="linea.error = ''" />
                <small class="p-error" v-if="linea.error">{{ linea.error }}</small>
              </div>
              <InputNumber v-model="linea.cantidad" :min="1" class="w-full" inputClass="w-full" />
              <InputNumber v-model="linea.precioCaja" mode="currency" currency="COP" locale="es-CO" :min="0" class="w-full" inputClass="w-full" />
              <span class="subtotal-val">{{ formatCOP((linea.cantidad || 0) * (linea.precioCaja || 0)) }}</span>
              <Button icon="pi pi-trash" text rounded severity="danger" size="small" @click="quitarLinea(i)" />
            </div>
          </div>

          <!-- Total -->
          <div v-if="form.detalles.length > 0" class="total-row">
            <span class="total-label">Total de la compra</span>
            <span class="total-val">{{ formatCOP(totalForm) }}</span>
          </div>

          <Message v-if="errorGeneral" severity="error" :closable="false" class="mt-2">{{ errorGeneral }}</Message>
        </div>

      </div>

      <template #footer>
        <Button label="Cancelar" text :disabled="dianDialog.cargando || guardando" @click="cerrarDianDialog" />

        <!-- Paso 1: botón Procesar XML -->
        <Button
          v-if="dianDialog.paso === 1"
          label="Procesar XML"
          icon="pi pi-arrow-right"
          iconPos="right"
          :loading="dianDialog.cargando"
          :disabled="!puedeProcessarDian"
          @click="procesarDian"
        />

        <!-- Paso 2: botón Guardar -->
        <Button
          v-if="dianDialog.paso === 2"
          label="Registrar compra"
          icon="pi pi-save"
          :loading="guardando"
          :disabled="form.detalles.length === 0"
          @click="guardarCompraDian"
        />
      </template>
    </Dialog>

    <!-- ── Dialog Nueva Compra ── -->
    <Dialog
      v-model:visible="dialogVisible"
      header="Registrar compra a proveedor"
      modal
      :style="{ width: '820px' }"
      :draggable="false"
      :closable="!guardando"
    >
      <div class="compra-form">

        <!-- Fila 1: Proveedor + Fecha + N° Factura -->
        <div class="form-row-3">
          <div class="field">
            <label>Proveedor *</label>
            <InputText
              v-model="form.proveedor"
              class="w-full"
              placeholder="Ej: Coca-Cola FEMSA"
              :class="{ 'p-invalid': errores.proveedor }"
            />
            <small class="p-error" v-if="errores.proveedor">{{ errores.proveedor }}</small>
          </div>
          <div class="field">
            <label>Fecha *</label>
            <Calendar v-model="form.fecha" dateFormat="dd/mm/yy" showIcon class="w-full" />
          </div>
          <div class="field">
            <label>N° Factura proveedor</label>
            <InputText v-model="form.numeroFacturaProveedor" class="w-full" placeholder="Opcional" />
          </div>
        </div>

        <!-- Fila 2: Observaciones -->
        <div class="field">
          <label>Observaciones</label>
          <Textarea v-model="form.observaciones" class="w-full" rows="2" placeholder="Notas adicionales sobre esta compra..." autoResize />
        </div>

        <!-- Sección productos -->
        <div class="productos-section">
          <div class="productos-header">
            <span><i class="pi pi-box" /> Productos recibidos</span>
            <Button
              label="Agregar producto"
              icon="pi pi-plus"
              size="small"
              outlined
              @click="agregarLinea"
            />
          </div>
          <small class="p-error" v-if="errores.detalles">{{ errores.detalles }}</small>

          <!-- Sin líneas -->
          <div v-if="form.detalles.length === 0" class="empty-lineas">
            <i class="pi pi-arrow-up" />
            <span>Haz clic en "Agregar producto" para comenzar a ingresar la mercancía</span>
          </div>

          <!-- Cabecera de columnas (aparece cuando hay al menos 1 línea) -->
          <div v-if="form.detalles.length > 0" class="lineas-cols-header">
            <span></span>
            <span>Producto</span>
            <span>Cajas</span>
            <span>Precio / caja</span>
            <span>Subtotal</span>
            <span></span>
          </div>

          <!-- Líneas de producto -->
          <div v-for="(linea, i) in form.detalles" :key="i" class="linea-row">
            <div class="linea-num">{{ i + 1 }}</div>

            <!-- Selector de producto -->
            <div class="linea-producto">
              <Select
                v-model="linea.productoId"
                :options="productos"
                optionValue="id"
                optionLabel="nombreCompleto"
                placeholder="Seleccionar producto..."
                filter
                filterPlaceholder="Buscar producto..."
                class="w-full"
                :class="{ 'p-invalid': linea.error }"
                @change="linea.error = ''"
              />
              <small class="p-error" v-if="linea.error">{{ linea.error }}</small>
            </div>

            <!-- Cajas -->
            <InputNumber
              v-model="linea.cantidad"
              :min="1"
              class="w-full"
              inputClass="w-full"
              placeholder="0"
              @input="linea.error = ''"
            />

            <!-- Precio / caja -->
            <InputNumber
              v-model="linea.precioCaja"
              mode="currency"
              currency="COP"
              locale="es-CO"
              :min="0"
              class="w-full"
              inputClass="w-full"
              placeholder="$ 0"
            />

            <!-- Subtotal -->
            <span class="subtotal-val">{{ formatCOP((linea.cantidad || 0) * (linea.precioCaja || 0)) }}</span>

            <!-- Eliminar línea -->
            <Button
              icon="pi pi-trash"
              text
              rounded
              severity="danger"
              size="small"
              @click="quitarLinea(i)"
              v-tooltip.top="'Quitar línea'"
            />
          </div>

          <!-- Total general -->
          <div v-if="form.detalles.length > 0" class="total-row">
            <span class="total-label">Total de la compra</span>
            <span class="total-val">{{ formatCOP(totalForm) }}</span>
          </div>
        </div>

        <Message v-if="errorGeneral" severity="error" :closable="false" class="mt-2">
          {{ errorGeneral }}
        </Message>
      </div>

      <template #footer>
        <Button label="Cancelar" text :disabled="guardando" @click="cerrarDialog" />
        <Button
          label="Registrar compra"
          icon="pi pi-save"
          :loading="guardando"
          :disabled="form.detalles.length === 0"
          @click="guardarCompra"
        />
      </template>
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
import Calendar from 'primevue/calendar'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'

const toast = useToast()

// ── Estado principal ──────────────────────────────────────────
const compras     = ref([])
const productos   = ref([])
const cargando    = ref(false)
const expandedRows = ref([])

const ahora = new Date()
const filtros = ref({
  desde: new Date(ahora.getFullYear(), ahora.getMonth(), 1),
  hasta: new Date()
})

// ── KPIs ─────────────────────────────────────────────────────
const totalPeriodo = computed(() =>
  compras.value.reduce((s, c) => s + Number(c.total || 0), 0)
)
const totalProductosIngresados = computed(() =>
  compras.value.reduce((s, c) => s + (c.detalles?.length ?? 0), 0)
)

// ── Dialog importar desde DIAN ────────────────────────────────
const dianDialog = ref({
  visible:    false,
  paso:       1,         // 1 = subir XML, 2 = revisar/editar
  xmlFile:    null,
  cargando:   false,
  error:      '',
  resultado:  null,      // FacturaDianResponse del backend
  sugerencias: []
})

const puedeProcessarDian = computed(() => !!dianDialog.value.xmlFile)

function abrirImportDian() {
  dianDialog.value = {
    visible: true, paso: 1,
    xmlFile: null, cargando: false, error: '', resultado: null, sugerencias: []
  }
  form.value = formInicial()
  errores.value = { proveedor: '', detalles: '' }
  errorGeneral.value = ''
}

function cerrarDianDialog() {
  dianDialog.value.visible = false
}

function onXmlSelect(event) {
  const file = event.target.files[0]
  if (file) dianDialog.value.xmlFile = file
}
function onXmlDrop(event) {
  const file = event.dataTransfer.files[0]
  if (file && (file.name.endsWith('.xml') || file.type.includes('xml'))) {
    dianDialog.value.xmlFile = file
  }
}

async function procesarDian() {
  dianDialog.value.error = ''
  dianDialog.value.cargando = true
  try {
    // Parsear XML de la factura electrónica
    const fd = new FormData()
    fd.append('file', dianDialog.value.xmlFile)
    const resultado = await api.post('/inventario/compras/importar-xml', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    dianDialog.value.resultado = resultado.data

    // Pre-llenar el formulario con los datos obtenidos
    if (resultado.data?.proveedor)           form.value.proveedor              = resultado.data.proveedor
    if (resultado.data?.numeroFactura)        form.value.numeroFacturaProveedor = resultado.data.numeroFactura
    if (resultado.data?.fecha)               form.value.fecha                  = new Date(resultado.data.fecha + 'T00:00:00')
    if (resultado.data?.cufe)                form.value.observaciones           = '' // CUFE se guarda en backend

    // Si el XML trajo líneas, pre-llenar los productos
    if (resultado.data?.lineas?.length) {
      form.value.detalles = resultado.data.lineas.map(l => ({
        productoId: l.productoIdSugerido || null,
        cantidad:   l.cantidad ? Math.round(Number(l.cantidad)) : null,
        precioCaja: l.precioUnitario ? Number(l.precioUnitario) : null,
        error:      ''
      }))
    }

    // Cargar sugerencias de historial
    if (resultado.data?.proveedor) {
      await cargarSugerencias()
    }

    dianDialog.value.paso = 2
  } catch (e) {
    dianDialog.value.error = e.response?.data?.mensaje || 'No se pudo procesar. Verifica el CUFE o el archivo XML.'
  } finally {
    dianDialog.value.cargando = false
  }
}

async function cargarSugerencias() {
  const nombre = form.value.proveedor?.trim()
  if (!nombre || nombre.length < 3) return
  try {
    const res = await api.get('/inventario/compras/sugerencias-proveedor', { params: { nombre } })
    dianDialog.value.sugerencias = res.data || []
  } catch { /* silencioso */ }
}

function agregarSugerencia(s) {
  const yaExiste = form.value.detalles.some(d => d.productoId === s.productoId)
  if (yaExiste) return
  form.value.detalles.push({
    productoId: s.productoId,
    cantidad:   s.ultimaCantidad || null,
    precioCaja: s.ultimoPrecioCaja ? Number(s.ultimoPrecioCaja) : null,
    error:      ''
  })
}

async function guardarCompraDian() {
  if (!validar()) return
  guardando.value = true
  errorGeneral.value = ''
  const payload = {
    proveedor:               form.value.proveedor.trim(),
    fecha:                   formatLocalDate(form.value.fecha),
    numeroFacturaProveedor:  form.value.numeroFacturaProveedor?.trim() || null,
    observaciones:           form.value.observaciones?.trim() || null,
    cufe:                    dianDialog.value.resultado?.cufe || null,
    detalles: form.value.detalles.map(l => ({
      producto:   { id: l.productoId },
      cantidad:   l.cantidad,
      precioCaja: l.precioCaja
    }))
  }
  try {
    await api.post('/inventario/compras', payload)
    toast.add({
      severity: 'success',
      summary:  'Compra registrada',
      detail:   `Compra a ${payload.proveedor} registrada desde la DIAN. Inventario y precios actualizados.`,
      life:     5000
    })
    dianDialog.value.visible = false
    await cargarCompras()
  } catch (e) {
    errorGeneral.value = e.response?.data?.mensaje || 'No se pudo registrar la compra.'
  } finally {
    guardando.value = false
  }
}

// ── Dialog nueva compra ───────────────────────────────────────
const dialogVisible = ref(false)
const guardando     = ref(false)
const errorGeneral  = ref('')
const errores       = ref({ proveedor: '', detalles: '' })

const formInicial = () => ({
  proveedor: '',
  fecha: new Date(),
  numeroFacturaProveedor: '',
  observaciones: '',
  detalles: []
})
const form = ref(formInicial())

const totalForm = computed(() =>
  form.value.detalles.reduce((s, l) => s + (l.cantidad || 0) * (l.precioCaja || 0), 0)
)

function abrirNuevaCompra() {
  form.value = formInicial()
  errores.value = { proveedor: '', detalles: '' }
  errorGeneral.value = ''
  dialogVisible.value = true
}

function cerrarDialog() {
  dialogVisible.value = false
}

function agregarLinea() {
  errores.value.detalles = ''
  form.value.detalles.push({ productoId: null, cantidad: null, precioCaja: null, error: '' })
}

function quitarLinea(i) {
  form.value.detalles.splice(i, 1)
}

function validar() {
  let ok = true
  errores.value = { proveedor: '', detalles: '' }

  if (!form.value.proveedor?.trim()) {
    errores.value.proveedor = 'El nombre del proveedor es obligatorio'
    ok = false
  }
  if (form.value.detalles.length === 0) {
    errores.value.detalles = 'Debes agregar al menos un producto'
    ok = false
  }

  form.value.detalles.forEach((l, i) => {
    if (!l.productoId) {
      l.error = 'Selecciona un producto'
      ok = false
    } else if (!l.cantidad || l.cantidad < 1) {
      l.error = 'Ingresa la cantidad de cajas'
      ok = false
    } else if (!l.precioCaja || l.precioCaja <= 0) {
      l.error = 'Ingresa el precio por caja'
      ok = false
    } else {
      l.error = ''
    }
  })

  return ok
}

async function guardarCompra() {
  if (!validar()) return

  guardando.value = true
  errorGeneral.value = ''

  // Construir el payload que espera el backend
  const payload = {
    proveedor: form.value.proveedor.trim(),
    fecha: formatLocalDate(form.value.fecha),
    numeroFacturaProveedor: form.value.numeroFacturaProveedor?.trim() || null,
    observaciones: form.value.observaciones?.trim() || null,
    detalles: form.value.detalles.map(l => ({
      producto: { id: l.productoId },
      cantidad: l.cantidad,
      precioCaja: l.precioCaja
    }))
  }

  try {
    await api.post('/inventario/compras', payload)
    toast.add({
      severity: 'success',
      summary: 'Compra registrada',
      detail: `Compra a ${payload.proveedor} registrada. Inventario y precios de compra actualizados.`,
      life: 5000
    })
    dialogVisible.value = false
    await cargarCompras()
  } catch (e) {
    errorGeneral.value = e.response?.data?.mensaje || 'No se pudo registrar la compra. Intenta de nuevo.'
  } finally {
    guardando.value = false
  }
}

// ── Carga de datos ────────────────────────────────────────────
async function cargarCompras() {
  cargando.value = true
  try {
    const res = await api.get('/inventario/compras', {
      params: {
        inicio: formatLocalDate(filtros.value.desde),
        fin:    formatLocalDate(filtros.value.hasta)
      }
    })
    compras.value = res.data ?? []
  } catch {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudieron cargar las compras', life: 4000 })
  } finally {
    cargando.value = false
  }
}

async function cargarProductos() {
  try {
    const res = await api.get('/productos')
    productos.value = res.data ?? []
  } catch {
    toast.add({ severity: 'warn', summary: 'Advertencia', detail: 'No se pudo cargar el catálogo de productos', life: 4000 })
  }
}

// ── Utilidades ────────────────────────────────────────────────
function formatLocalDate(date) {
  if (!date) return null
  const d = date instanceof Date ? date : new Date(date)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

function formatFecha(f) {
  return f ? new Date(f + 'T00:00:00').toLocaleDateString('es-CO', {
    day: '2-digit', month: 'short', year: 'numeric'
  }) : '—'
}

function formatCOP(v) {
  if (!v && v !== 0) return '—'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(v)
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') Promise.all([cargarCompras(), cargarProductos()])
}

onMounted(async () => {
  await Promise.all([cargarCompras(), cargarProductos()])
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped>
.compras-page { display: flex; flex-direction: column; gap: 1rem; }

/* ── Toolbar ── */
.page-toolbar {
  display: flex; justify-content: space-between; align-items: flex-end;
  flex-wrap: wrap; gap: 0.75rem;
  background: white; padding: 1rem 1.25rem;
  border-radius: 10px; box-shadow: 0 2px 6px rgba(0,0,0,0.05);
}
.toolbar-left  { display: flex; gap: 0.75rem; align-items: flex-end; flex-wrap: wrap; }
.toolbar-right { display: flex; gap: 0.5rem; }
.field-inline  { display: flex; flex-direction: column; gap: 0.3rem; }
.field-inline label { font-size: 0.8rem; font-weight: 600; color: #555; }

/* ── KPIs ── */
.kpi-row { display: flex; gap: 1rem; flex-wrap: wrap; }
.kpi-card {
  background: white; border-radius: 12px;
  padding: 1rem 1.5rem; box-shadow: 0 2px 6px rgba(0,0,0,0.05);
  display: flex; flex-direction: column; gap: 0.25rem;
  border-left: 4px solid #E65100; min-width: 180px;
}
.kpi-card-blue  { border-left-color: #1565C0; }
.kpi-card-green { border-left-color: #2E7D32; }
.kpi-val { font-size: 1.4rem; font-weight: 800; color: #333; }
.kpi-lbl { font-size: 0.78rem; color: #888; }

/* ── Tabla ── */
.card-table {
  background: white; border-radius: 10px;
  padding: 1rem; box-shadow: 0 2px 6px rgba(0,0,0,0.05);
}
.tag-factura {
  background: #E3F2FD; color: #1565C0; font-weight: 700;
  padding: 2px 8px; border-radius: 4px; font-size: 0.8rem; font-family: monospace;
}
.badge-lineas {
  background: #FFF3E0; color: #E65100; font-weight: 600;
  padding: 2px 8px; border-radius: 10px; font-size: 0.78rem;
}
.total-cell { color: #1565C0; font-size: 1rem; }
.text-muted  { color: #ccc; font-size: 0.82rem; }

/* ── Expansión de detalle ── */
.expansion-body {
  padding: 1rem 1.5rem;
  background: #F8F9FA;
  border-radius: 0 0 8px 8px;
}
.expansion-header {
  display: flex; align-items: center; gap: 0.6rem;
  font-weight: 600; color: #444; font-size: 0.9rem;
  margin-bottom: 0.75rem;
}
.obs-chip {
  margin-left: auto; font-size: 0.8rem; font-weight: 400; color: #777;
  background: #eee; padding: 2px 8px; border-radius: 10px;
}

/* ── Dialog form ── */
.compra-form { display: flex; flex-direction: column; gap: 1rem; }
.form-row-3 { display: grid; grid-template-columns: 2fr 1fr 1.5fr; gap: 1rem; }
.field { display: flex; flex-direction: column; gap: 0.3rem; }
.field label { font-size: 0.82rem; font-weight: 600; color: #444; }

/* Productos */
.productos-section {
  border: 1px solid #e0e0e0; border-radius: 10px;
  overflow: hidden;
}
.productos-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0.75rem 1rem;
  background: #F5F5F5; border-bottom: 1px solid #e0e0e0;
  font-weight: 600; font-size: 0.9rem; color: #444;
}

.empty-lineas {
  display: flex; align-items: center; gap: 0.5rem;
  padding: 1.5rem; color: #aaa; font-size: 0.85rem;
  justify-content: center;
}

/* Cabecera de columnas */
.lineas-cols-header {
  display: grid;
  grid-template-columns: 28px 1fr 110px 160px 120px 36px;
  gap: 0.6rem;
  padding: 0.35rem 0.75rem;
  font-size: 0.72rem; font-weight: 700;
  text-transform: uppercase; letter-spacing: 0.3px;
  color: #999; background: #f8f9fa;
  border-bottom: 2px solid #e0e0e0;
}

/* Fila de línea */
.linea-row {
  display: grid;
  grid-template-columns: 28px 1fr 110px 160px 120px 36px;
  align-items: center; gap: 0.6rem;
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid #f0f0f0;
}
.linea-row:last-of-type { border-bottom: none; }
.linea-row > * { min-width: 0; }
.linea-num {
  width: 28px; height: 28px; border-radius: 50%;
  background: #E3F2FD; color: #1565C0;
  font-size: 0.78rem; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.linea-producto { min-width: 0; }
.linea-row :deep(.p-inputnumber) { width: 100%; min-width: 0; }
.linea-row :deep(.p-inputnumber-input) { width: 100% !important; min-width: 0; }
.subtotal-val {
  font-weight: 700; color: #1565C0; font-size: 0.95rem;
  white-space: nowrap;
}

/* Total fila */
.total-row {
  display: flex; justify-content: flex-end; align-items: center;
  gap: 1rem; padding: 0.75rem 1rem;
  background: #E8F5E9; border-top: 2px solid #C8E6C9;
}
.total-label { font-size: 0.85rem; font-weight: 600; color: #555; }
.total-val   { font-size: 1.3rem; font-weight: 800; color: #2E7D32; }

/* Empty state */
.empty-state {
  display: flex; flex-direction: column; align-items: center;
  gap: 0.5rem; padding: 3rem; color: #aaa; font-size: 0.9rem;
}

.mt-2 { margin-top: 0.5rem; }

/* ── Diálogo importar DIAN ─────────────────────────────────── */
.dian-import { display: flex; flex-direction: column; gap: 1.25rem; }

.dian-intro {
  background: #e3f2fd; border-left: 4px solid #1565C0; border-radius: 0 8px 8px 0;
  padding: 0.75rem 1rem; font-size: 0.88rem; color: #1565C0;
  display: flex; align-items: flex-start; gap: 0.5rem; margin: 0;
}

.dian-metodos { display: flex; flex-direction: column; gap: 0.75rem; }

.dian-metodo-card {
  display: flex; align-items: flex-start; gap: 1rem;
  border: 2px solid #e0e0e0; border-radius: 12px; padding: 1rem 1.25rem;
  cursor: pointer; transition: border-color 0.2s, background 0.2s;
}
.dian-metodo-card:hover  { border-color: #90caf9; background: #f8fbff; }
.dian-metodo-card.active { border-color: #1565C0; background: #e3f2fd; }

.dian-metodo-icon { font-size: 1.6rem; color: #1565C0; margin-top: 0.2rem; flex-shrink: 0; }
.dian-metodo-titulo { font-weight: 700; font-size: 0.95rem; color: #333; margin-bottom: 0.3rem; }
.dian-metodo-desc { font-size: 0.85rem; color: #666; line-height: 1.5; }

.dian-badge {
  display: inline-block; margin-top: 0.4rem;
  background: #e3f2fd; color: #1565C0;
  font-size: 0.75rem; padding: 2px 8px; border-radius: 99px; font-weight: 600;
}
.dian-badge-green { background: #e8f5e9; color: #2E7D32; }

.dian-entrada { margin-top: 0.5rem; }

.dian-cufe-input { font-family: monospace; font-size: 0.78rem; letter-spacing: 0.02em; }
.hint { font-size: 0.8rem; color: #888; margin-top: 0.3rem; display: block; }

.dian-dropzone {
  border: 2px dashed #ccc; border-radius: 10px; padding: 2rem;
  text-align: center; cursor: pointer; color: #888; font-size: 0.9rem;
  transition: border-color 0.2s, background 0.2s;
  display: flex; flex-direction: column; align-items: center; gap: 0.5rem;
}
.dian-dropzone:hover { border-color: #1565C0; background: #f8fbff; color: #1565C0; }
.dian-xml-nombre { font-weight: 600; color: #2E7D32; display: flex; align-items: center; gap: 0.4rem; }

.dian-header-form {
  display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 1rem; margin-bottom: 1rem;
}

/* Sugerencias del historial */
.dian-sugerencias-section { display: flex; flex-direction: column; gap: 0.75rem; }

.sugerencias-chips { display: flex; flex-direction: column; gap: 0.5rem; }
.sugerencias-titulo {
  font-size: 0.82rem; color: #555; font-weight: 600;
  display: flex; align-items: center; gap: 0.4rem;
}

.chips-row { display: flex; flex-wrap: wrap; gap: 0.5rem; }

.chip-sugerencia {
  display: flex; flex-direction: column; align-items: flex-start;
  background: #f0f7ff; border: 1.5px solid #90caf9; border-radius: 8px;
  padding: 0.4rem 0.75rem; cursor: pointer;
  transition: background 0.15s, border-color 0.15s; text-align: left;
}
.chip-sugerencia:hover:not(:disabled) { background: #1565C0; border-color: #1565C0; }
.chip-sugerencia:hover:not(:disabled) .chip-nombre,
.chip-sugerencia:hover:not(:disabled) .chip-precio { color: white; }
.chip-sugerencia:disabled { opacity: 0.45; cursor: default; }
.chip-ya-agregado { background: #e8f5e9; border-color: #a5d6a7; }

.chip-nombre { font-size: 0.82rem; font-weight: 600; color: #1565C0; }
.chip-precio { font-size: 0.72rem; color: #666; margin-top: 0.1rem; }

/* Líneas del XML */
.dian-lineas-section { display: flex; flex-direction: column; gap: 0.5rem; }

.dian-cols { grid-template-columns: 1.8fr 1.8fr 0.8fr 1.2fr 1fr 36px !important; }

.dian-linea-row { display: grid; align-items: start; gap: 0.5rem;
  grid-template-columns: 1.8fr 1.8fr 0.8fr 1.2fr 1fr 36px; }

.dian-desc-cell {
  background: #f5f5f5; border-radius: 6px; padding: 0.4rem 0.6rem;
  min-height: 38px; display: flex; align-items: center;
}
.dian-desc-text {
  font-size: 0.78rem; color: #555; line-height: 1.3;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
</style>
