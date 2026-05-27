<template>
  <div class="facturacion-page">

    <!-- ╔══ TABS PRINCIPALES ══╗ -->
    <Tabs v-model:value="tabActivo">
      <TabList>
        <Tab value="dian">
          <i class="pi pi-cloud-upload" style="margin-right:0.4rem" />
          Facturación Electrónica DIAN
        </Tab>
        <Tab value="manual">
          <i class="pi pi-file" style="margin-right:0.4rem" />
          Facturas Manuales
        </Tab>
        <Tab value="notas">
          <i class="pi pi-list-check" style="margin-right:0.4rem" />
          Notas NC / ND
        </Tab>
      </TabList>

      <!-- ══════════════════════════════════════════════════════
           TAB 1: FACTURACIÓN ELECTRÓNICA (Remisiones → Siigo)
           ══════════════════════════════════════════════════════ -->
      <TabPanel value="dian">
        <div class="dian-panel">

          <!-- Selector de fecha + botón cargar resumen -->
          <div class="dian-toolbar">
            <div class="campo-fecha">
              <label>Fecha a facturar</label>
              <DatePicker v-model="fechaDian" dateFormat="dd/mm/yy" :maxDate="hoy" />
            </div>
            <Button
              label="Ver resumen"
              icon="pi pi-search"
              outlined
              @click="cargarResumen"
              :loading="cargandoResumen"
            />
          </div>

          <!-- ─── RESUMEN ─────────────────────────────────── -->
          <div v-if="resumen" class="resumen-card">

            <!-- Indicadores rápidos -->
            <div class="indicadores">
              <div class="ind-item ind-pendiente">
                <span class="ind-num">{{ resumen.totalRemisionesPendientes }}</span>
                <span class="ind-label">Pendientes</span>
              </div>
              <div class="ind-item ind-ok">
                <span class="ind-num">{{ resumen.totalRemisionesYaFacturadas }}</span>
                <span class="ind-label">Ya facturadas</span>
              </div>
              <div class="ind-item ind-error">
                <span class="ind-num">{{ resumen.totalRemisionesConError }}</span>
                <span class="ind-label">Con error</span>
              </div>
              <div class="ind-item ind-facturas">
                <span class="ind-num">{{ resumen.facturasAGenerar }}</span>
                <span class="ind-label">Facturas a crear</span>
              </div>
            </div>

            <!-- Sin pendientes -->
            <div v-if="resumen.totalRemisionesPendientes === 0" class="sin-pendientes">
              <i class="pi pi-check-circle" style="font-size:2.5rem;color:#2E7D32" />
              <p>No hay remisiones pendientes para esta fecha.</p>
            </div>

            <template v-else>
              <!-- Agrupación explicada -->
              <div class="agrupacion-info">
                <div class="agrup-item">
                  <i class="pi pi-users"></i>
                  <span><strong>{{ resumen.remisionesConsumidorFinal }}</strong> remisiones de consumidor final
                    → se consolidan en <strong>1 sola factura</strong></span>
                </div>
                <div class="agrup-item">
                  <i class="pi pi-building"></i>
                  <span><strong>{{ resumen.remisionesClienteIdentificado }}</strong> remisiones de clientes identificados
                    → <strong>1 factura por remisión</strong></span>
                </div>
              </div>

              <!-- Barra de búsqueda de remisiones -->
              <div class="rem-busqueda-bar">
                <span class="p-input-icon-left rem-search-wrap">
                  <i class="pi pi-search" />
                  <InputText
                    v-model="busquedaRem"
                    placeholder="Buscar por No. remisión o cliente..."
                    class="rem-search-input"
                  />
                </span>
                <span class="rem-count-info">
                  {{ pendientesFiltrados.length }} de {{ pendientes.length }} remisión{{ pendientes.length !== 1 ? 'es' : '' }}
                </span>
              </div>

              <!-- Tabla por remisión (reemplaza tabla-resumen agrupada) -->
              <div class="tabla-remisiones">
                <div class="tabla-rem-header">
                  <span>No. Remisión</span>
                  <span>Cliente</span>
                  <span>NIT</span>
                  <span>Total</span>
                  <span>Tipo</span>
                  <span class="col-acciones-hdr">Acciones</span>
                </div>

                <div v-if="pendientesFiltrados.length === 0" class="tabla-rem-empty">
                  <i class="pi pi-search" /> Sin resultados para "{{ busquedaRem }}"
                </div>

                <div
                  v-for="rem in pendientesFiltrados"
                  :key="rem.id"
                  class="tabla-rem-row"
                >
                  <span class="rem-num-col">
                    <strong>{{ rem.numero }}</strong>
                  </span>
                  <span class="rem-cli-col">{{ rem.cliente?.razonSocial || '—' }}</span>
                  <span class="rem-nit-col">{{ rem.cliente?.nit || '—' }}</span>
                  <span class="rem-tot-col">{{ formatCOP(rem.total) }}</span>
                  <span>
                    <Tag
                      :value="rem.cliente?.nit === NIT_CF ? 'Cons. Final' : 'Individual'"
                      :severity="rem.cliente?.nit === NIT_CF ? 'info' : 'secondary'"
                      style="font-size:0.73rem"
                    />
                  </span>
                  <span class="rem-acc-col">
                    <Button
                      icon="pi pi-eye"
                      text rounded size="small"
                      severity="secondary"
                      v-tooltip.top="'Ver detalle'"
                      @click="abrirVerRem(rem)"
                    />
                    <Button
                      icon="pi pi-file-pdf"
                      text rounded size="small"
                      severity="warn"
                      v-tooltip.top="'Vista previa factura DIAN (con marca de agua)'"
                      @click="previaDian(rem)"
                      :loading="previaCargando[rem.id]"
                    />
                    <Button
                      icon="pi pi-pencil"
                      text rounded size="small"
                      severity="info"
                      v-tooltip.top="'Editar remisión'"
                      @click="abrirEditar(rem)"
                    />
                    <Button
                      icon="pi pi-times"
                      text rounded size="small"
                      severity="danger"
                      v-tooltip.top="'Anular remisión'"
                      @click="anularRemisionDetalle(rem)"
                    />
                  </span>
                </div>
              </div>

              <!-- Totales -->
              <div class="totales-resumen">
                <div class="total-fila">
                  <span>Subtotal sin IVA:</span>
                  <span>{{ formatCOP(resumen.totalSinIva) }}</span>
                </div>
                <div class="total-fila" v-if="resumen.totalIva > 0">
                  <span>IVA:</span>
                  <span>{{ formatCOP(resumen.totalIva) }}</span>
                </div>
                <div class="total-fila" v-if="resumen.totalIbua > 0">
                  <span>IBUA (Beb. Azucaradas):</span>
                  <span>{{ formatCOP(resumen.totalIbua) }}</span>
                </div>
                <div class="total-fila total-final">
                  <span>Total a facturar:</span>
                  <span>{{ formatCOP(resumen.totalGeneral) }}</span>
                </div>
              </div>

              <!-- Advertencia y botones de acción -->
              <div class="advertencia-dian">
                <i class="pi pi-exclamation-triangle" />
                <span>
                  Verifica los detalles de cada remisión <strong>antes</strong> de enviar.
                  Una vez enviado a la DIAN <strong>no se puede modificar</strong> — solo anular.
                </span>
              </div>

              <div class="btn-facturar">
                <Button
                  label="Ver detalle completo"
                  icon="pi pi-list"
                  outlined
                  severity="secondary"
                  @click="abrirDetalle"
                  :loading="cargandoDetalle"
                />
                <Button
                  :label="`Enviar ${resumen.facturasAGenerar} factura${resumen.facturasAGenerar !== 1 ? 's' : ''} a DIAN`"
                  icon="pi pi-cloud-upload"
                  size="large"
                  @click="confirmarFacturar"
                  :loading="facturando"
                />
              </div>
            </template>
          </div>

          <!-- ─── RESULTADO DEL ENVÍO ─────────────────────── -->
          <div v-if="resultadoEnvio" class="resultado-card" :class="resultadoEnvio.errores > 0 ? 'resultado-warn' : 'resultado-ok'">
            <div class="resultado-header">
              <i :class="resultadoEnvio.errores > 0 ? 'pi pi-exclamation-triangle' : 'pi pi-check-circle'" />
              <span>{{ resultadoEnvio.mensaje }}</span>
            </div>
            <div class="resultado-detalle">
              <span>✅ Facturas enviadas: <strong>{{ resultadoEnvio.facturasEnviadas }}</strong></span>
              <span v-if="resultadoEnvio.errores > 0">
                ❌ Con error: <strong>{{ resultadoEnvio.errores }}</strong>
                — puedes reintentarlas en el historial de abajo
              </span>
            </div>
          </div>

          <!-- ─── HISTORIAL ──────────────────────────────── -->
          <div class="historial-section">
            <div class="historial-toolbar">
              <h3 class="historial-titulo">
                <i class="pi pi-history" />
                Historial de facturas enviadas
              </h3>
              <div class="historial-filtros">
                <DatePicker v-model="histInicio" dateFormat="dd/mm/yy" placeholder="Desde" />
                <DatePicker v-model="histFin"    dateFormat="dd/mm/yy" placeholder="Hasta" />
                <Button icon="pi pi-search" outlined size="small" @click="cargarHistorial" :loading="cargandoHistorial" />
                <Button
                  icon="pi pi-sync"
                  outlined
                  size="small"
                  severity="secondary"
                  v-tooltip.top="'Vincular facturas históricas (necesario solo la primera vez)'"
                  :loading="sincronizando"
                  @click="sincronizarFacturas"
                />
              </div>
            </div>

            <div v-if="cargandoHistorial" class="historial-loading">
              <i class="pi pi-spin pi-spinner" /> Cargando...
            </div>

            <div v-else-if="historial.length === 0" class="historial-vacio">
              <i class="pi pi-inbox" style="font-size:2rem;color:#ccc" />
              <p>No hay facturas enviadas en este período</p>
            </div>

            <div v-else class="historial-tabla">
              <!-- Cabecera -->
              <div class="hist-row hist-header">
                <span>No. Remisión</span>
                <span>No. Factura DIAN</span>
                <span>Fecha</span>
                <span>Cliente</span>
                <span>Total</span>
                <span>Estado</span>
                <span>Acciones</span>
              </div>
              <!-- Filas -->
              <div
                class="hist-row"
                v-for="r in historial"
                :key="r.id"
                :class="r.errorFacturacion ? 'hist-error' : 'hist-ok'"
              >
                <span class="hist-num">{{ r.numero }}</span>
                <span class="hist-fev">
                  <strong v-if="r.numeroFacturaDian">{{ r.numeroFacturaDian }}</strong>
                  <span v-else class="sin-fev">—</span>
                </span>
                <span>{{ formatFecha(r.fecha) }}</span>
                <span class="hist-cliente">{{ r.cliente?.razonSocial || '—' }}</span>
                <span class="hist-total">{{ formatCOP(r.total) }}</span>
                <span>
                  <Tag v-if="r.estado === 'FACTURADA'" value="Facturada" severity="success" style="font-size:0.75rem" />
                  <Tag v-else-if="r.errorFacturacion" value="Error" severity="danger" style="font-size:0.75rem"
                       v-tooltip.top="r.errorFacturacion" />
                </span>
                <span class="hist-acciones">
                  <!-- Descargar PDF si ya fue facturada -->
                  <Button
                    v-if="r.estado === 'FACTURADA'"
                    icon="pi pi-file-pdf"
                    text rounded size="small" severity="danger"
                    v-tooltip.top="'Descargar factura electrónica PDF'"
                    @click="descargarFacturaPdf(r)"
                  />
                  <!-- Nota Crédito (NC) — visible para toda factura DIAN emitida -->
                  <Button
                    v-if="r.estado === 'FACTURADA' && r.numeroFacturaDian"
                    icon="pi pi-arrow-down-left"
                    text rounded size="small" severity="success"
                    v-tooltip.top="'Crear Nota Crédito'"
                    :loading="cargandoNota[r.id] === 'nc'"
                    @click="abrirNc(r)"
                  />
                  <!-- Nota Débito (ND) -->
                  <Button
                    v-if="r.estado === 'FACTURADA' && r.numeroFacturaDian"
                    icon="pi pi-arrow-up-right"
                    text rounded size="small" severity="warn"
                    v-tooltip.top="'Crear Nota Débito'"
                    :loading="cargandoNota[r.id] === 'nd'"
                    @click="abrirNd(r)"
                  />
                  <!-- Reintentar si tuvo error -->
                  <Button
                    v-if="r.errorFacturacion"
                    icon="pi pi-refresh"
                    text rounded size="small" severity="warn"
                    v-tooltip.top="'Reintentar envío'"
                    @click="reintentarRemision(r)"
                  />
                </span>
              </div>
            </div>
          </div>

        </div>
      </TabPanel>

      <!-- ══════════════════════════════════════════════════════
           TAB 2: FACTURAS MANUALES (funcionalidad existente)
           ══════════════════════════════════════════════════════ -->
      <TabPanel value="manual">
        <div class="manual-panel">

          <div class="page-toolbar">
            <div class="toolbar-left">
              <span class="p-input-icon-left">
                <i class="pi pi-search" />
                <InputText v-model="filtro" placeholder="Buscar numero, cliente..." />
              </span>
              <Select v-model="filtroEstado" :options="estadosFactura" placeholder="Todos los estados" showClear class="select-small" />
            </div>
            <div class="toolbar-right">
              <Button label="Nueva factura manual" icon="pi pi-plus" @click="dialogManual = true" />
            </div>
          </div>

          <div class="card-table">
            <DataTable :value="facturasFiltradas" :loading="loading" stripedRows paginator :rows="15" responsiveLayout="scroll">
              <Column field="numero" header="Numero" sortable style="width:150px" />
              <Column header="Cliente">
                <template #body="{ data }">{{ data.cliente?.razonSocial || '-' }}</template>
              </Column>
              <Column header="Fecha">
                <template #body="{ data }">{{ formatFecha(data.fecha) }}</template>
              </Column>
              <Column header="Estado">
                <template #body="{ data }">
                  <Tag :value="estadoLabel(data.estado)" :severity="estadoSeverity(data.estado)" />
                </template>
              </Column>
              <Column header="Total">
                <template #body="{ data }">{{ formatCOP(data.total) }}</template>
              </Column>
              <Column header="IVA">
                <template #body="{ data }">{{ formatCOP(data.totalIva) }}</template>
              </Column>
              <Column header="DIAN/Siigo">
                <template #body="{ data }">
                  <Tag v-if="data.siigoId" value="Emitida DIAN" severity="success" />
                  <Tag v-else value="Sin emitir" severity="secondary" />
                </template>
              </Column>
              <Column header="Acciones" style="width:160px">
                <template #body="{ data }">
                  <Button
                    v-if="!data.siigoId && data.estado !== 'ANULADA'"
                    icon="pi pi-cloud-upload"
                    text rounded size="small" severity="info"
                    @click="emitirSiigo(data)"
                    v-tooltip.top="'Emitir en Siigo (DIAN)'"
                  />
                  <Button
                    icon="pi pi-file-pdf"
                    text rounded size="small" severity="danger"
                    @click="descargarPdf(data)"
                    v-tooltip.top="'Descargar PDF'"
                  />
                  <Button
                    icon="pi pi-envelope"
                    text rounded size="small"
                    @click="enviarEmail(data)"
                    v-tooltip.top="'Enviar por email'"
                  />
                  <Button
                    v-if="data.estado !== 'ANULADA'"
                    icon="pi pi-times"
                    text rounded size="small" severity="danger"
                    @click="confirmarAnular(data)"
                    v-tooltip.top="'Anular'"
                  />
                </template>
              </Column>
              <template #empty>No hay facturas registradas</template>
            </DataTable>
          </div>

        </div>
      </TabPanel>

      <!-- ══════════════════════════════════════════════════════
           TAB 3: HISTORIAL DE NOTAS CRÉDITO Y DÉBITO
           ══════════════════════════════════════════════════════ -->
      <TabPanel value="notas">
        <div class="notas-panel">

          <!-- Toolbar: filtro fecha + botón cargar -->
          <div class="notas-toolbar">
            <div class="notas-toolbar-left">
              <label class="field-label" style="margin:0">Período:</label>
              <DatePicker
                v-model="notasRangoFecha"
                selectionMode="range"
                :manualInput="false"
                dateFormat="dd/mm/yy"
                placeholder="Seleccionar rango de fechas"
                showButtonBar
                style="width:240px"
              />
              <Button
                label="Cargar"
                icon="pi pi-search"
                @click="cargarNotas"
                :loading="cargandoNotas"
              />
            </div>
            <div class="notas-toolbar-right">
              <span class="notas-resumen">
                <Tag :value="`${notasCredito.length} NC`"  severity="success" style="margin-right:0.4rem" />
                <Tag :value="`${notasDebito.length} ND`"   severity="warn" />
              </span>
            </div>
          </div>

          <!-- Tabla NOTAS CRÉDITO -->
          <div class="notas-bloque">
            <div class="notas-bloque-header notas-bloque-nc">
              <i class="pi pi-arrow-down-left" />
              <span>Notas Crédito</span>
              <span class="notas-bloque-sub">Devoluciones, ajustes y anulaciones</span>
            </div>

            <DataTable
              :value="notasCredito"
              :loading="cargandoNotas"
              v-model:expandedRows="ncExpandidas"
              dataKey="id"
              stripedRows
              paginator :rows="10"
              responsiveLayout="scroll"
              class="notas-table"
            >
              <!-- Expand toggle -->
              <Column expander style="width:3rem" />

              <Column field="numero" header="N.° Nota" sortable style="width:120px">
                <template #body="{ data }">
                  <span class="nota-numero nc-num">{{ data.numero }}</span>
                </template>
              </Column>

              <Column header="Factura" style="width:130px">
                <template #body="{ data }">
                  <span class="nota-factura-ref">{{ data.facturaNumero }}</span>
                </template>
              </Column>

              <Column header="Cliente">
                <template #body="{ data }">{{ data.facturaClienteNombre || '—' }}</template>
              </Column>

              <Column header="Tipo" style="width:145px">
                <template #body="{ data }">
                  <Tag :value="labelTipoNc(data.tipo)" :severity="severityTipoNc(data.tipo)" />
                </template>
              </Column>

              <Column header="Fecha" sortable field="fecha" style="width:110px">
                <template #body="{ data }">{{ formatFecha(data.fecha) }}</template>
              </Column>

              <Column header="Valor" style="width:130px">
                <template #body="{ data }">
                  <strong class="nota-valor nc-val">{{ formatCOP(data.valorNota) }}</strong>
                </template>
              </Column>

              <Column header="Estado DIAN" style="width:130px">
                <template #body="{ data }">
                  <Tag :value="data.estado" :severity="severityEstadoNota(data.estado)" />
                </template>
              </Column>

              <Column header="Siigo ID" style="width:120px">
                <template #body="{ data }">
                  <span v-if="data.siigoId" class="siigo-id-chip">{{ data.siigoId }}</span>
                  <span v-else-if="data.errorSiigo" v-tooltip.top="data.errorSiigo" class="siigo-error-chip">
                    <i class="pi pi-exclamation-triangle" /> Error
                  </span>
                  <span v-else class="siigo-pending-chip">Pendiente</span>
                </template>
              </Column>

              <!-- Fila expandida: detalles de líneas -->
              <template #expansion="{ data }">
                <div class="nota-expansion">
                  <div class="nota-expansion-motivo" v-if="data.motivo">
                    <i class="pi pi-comment" />
                    <em>{{ data.motivo }}</em>
                  </div>
                  <table class="nota-det-table">
                    <thead>
                      <tr>
                        <th>Producto</th>
                        <th>Cant.</th>
                        <th>Precio original</th>
                        <th>Precio nuevo</th>
                        <th>IVA %</th>
                        <th>Valor línea</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="d in data.detalles" :key="d.id">
                        <td>{{ d.productoNombre }}</td>
                        <td>{{ d.cantidad }}</td>
                        <td>{{ formatCOP(d.precioUnitarioOriginal) }}</td>
                        <td>{{ d.precioUnitarioNuevo != null ? formatCOP(d.precioUnitarioNuevo) : '—' }}</td>
                        <td>{{ d.porcentajeIva }}%</td>
                        <td><strong>{{ formatCOP(d.valorLinea) }}</strong></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </template>

              <template #empty>
                <div class="notas-empty">
                  <i class="pi pi-inbox" /> No hay notas crédito en el período seleccionado
                </div>
              </template>
            </DataTable>
          </div>

          <!-- Tabla NOTAS DÉBITO -->
          <div class="notas-bloque" style="margin-top:1.5rem">
            <div class="notas-bloque-header notas-bloque-nd">
              <i class="pi pi-arrow-up-right" />
              <span>Notas Débito</span>
              <span class="notas-bloque-sub">Intereses, cambios de valor y gastos adicionales</span>
            </div>

            <DataTable
              :value="notasDebito"
              :loading="cargandoNotas"
              v-model:expandedRows="ndExpandidas"
              dataKey="id"
              stripedRows
              paginator :rows="10"
              responsiveLayout="scroll"
              class="notas-table"
            >
              <Column expander style="width:3rem" />

              <Column field="numero" header="N.° Nota" sortable style="width:120px">
                <template #body="{ data }">
                  <span class="nota-numero nd-num">{{ data.numero }}</span>
                </template>
              </Column>

              <Column header="Factura" style="width:130px">
                <template #body="{ data }">
                  <span class="nota-factura-ref">{{ data.facturaNumero }}</span>
                </template>
              </Column>

              <Column header="Cliente">
                <template #body="{ data }">{{ data.facturaClienteNombre || '—' }}</template>
              </Column>

              <Column header="Tipo" style="width:150px">
                <template #body="{ data }">
                  <Tag :value="labelTipoNd(data.tipo)" :severity="severityTipoNd(data.tipo)" />
                </template>
              </Column>

              <Column header="Fecha" sortable field="fecha" style="width:110px">
                <template #body="{ data }">{{ formatFecha(data.fecha) }}</template>
              </Column>

              <Column header="Valor" style="width:130px">
                <template #body="{ data }">
                  <strong class="nota-valor nd-val">{{ formatCOP(data.valorNota) }}</strong>
                </template>
              </Column>

              <Column header="Estado DIAN" style="width:130px">
                <template #body="{ data }">
                  <Tag :value="data.estado" :severity="severityEstadoNota(data.estado)" />
                </template>
              </Column>

              <Column header="Siigo ID" style="width:120px">
                <template #body="{ data }">
                  <span v-if="data.siigoId" class="siigo-id-chip">{{ data.siigoId }}</span>
                  <span v-else-if="data.errorSiigo" v-tooltip.top="data.errorSiigo" class="siigo-error-chip">
                    <i class="pi pi-exclamation-triangle" /> Error
                  </span>
                  <span v-else class="siigo-pending-chip">Pendiente</span>
                </template>
              </Column>

              <!-- Fila expandida: detalles de líneas ND -->
              <template #expansion="{ data }">
                <div class="nota-expansion">
                  <div class="nota-expansion-motivo" v-if="data.motivo">
                    <i class="pi pi-comment" />
                    <em>{{ data.motivo }}</em>
                  </div>
                  <table class="nota-det-table">
                    <thead>
                      <tr>
                        <th>Descripción</th>
                        <th>Cant.</th>
                        <th>Valor unitario</th>
                        <th>IVA %</th>
                        <th>Valor línea</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="d in data.detalles" :key="d.id">
                        <td>{{ d.descripcion }}</td>
                        <td>{{ d.cantidad }}</td>
                        <td>{{ formatCOP(d.precioUnitario) }}</td>
                        <td>{{ d.porcentajeIva }}%</td>
                        <td><strong>{{ formatCOP(d.valorLinea) }}</strong></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </template>

              <template #empty>
                <div class="notas-empty">
                  <i class="pi pi-inbox" /> No hay notas débito en el período seleccionado
                </div>
              </template>
            </DataTable>
          </div>

        </div>
      </TabPanel>
    </Tabs>

    <!-- ─── Dialog Vista Previa Detallada ───────────────────── -->
    <Dialog
      v-model:visible="dialogDetalle"
      header="Vista previa — Remisiones a enviar a DIAN"
      modal
      :style="{ width: '860px', maxWidth: '98vw' }"
      :draggable="false"
    >
      <div class="detalle-dialog">

        <!-- Aviso legal -->
        <div class="aviso-legal">
          <i class="pi pi-shield" />
          <span>
            Revisa cada remisión con cuidado. Si un monto o producto está incorrecto,
            <strong>anúlala aquí</strong> antes de enviar.
            Una factura enviada a la DIAN con datos erróneos genera obligaciones tributarias reales.
          </span>
        </div>

        <!-- Sin pendientes -->
        <div v-if="pendientes.length === 0" class="detalle-vacio">
          <i class="pi pi-check-circle" style="font-size:2rem;color:#2E7D32" />
          <p>No hay remisiones pendientes para enviar.</p>
        </div>

        <div v-else>
          <!-- Resumen rápido de totales -->
          <div class="detalle-totales-rapidos">
            <span>{{ pendientes.length }} remisión{{ pendientes.length !== 1 ? 'es' : '' }}</span>
            <span class="sep">·</span>
            <span>Total a enviar: <strong>{{ formatCOP(totalPendientes) }}</strong></span>
            <span class="sep">·</span>
            <span class="aviso-cf" v-if="pendientesCF.length > 0">
              <i class="pi pi-info-circle" />
              {{ pendientesCF.length }} de consumidor final → 1 factura consolidada
            </span>
          </div>

          <!-- Una tarjeta por remisión -->
          <div class="rem-card" v-for="rem in pendientes" :key="rem.id">
            <div class="rem-card-header">
              <div class="rem-card-info">
                <span class="rem-numero">{{ rem.numero }}</span>
                <span class="rem-cliente">{{ rem.cliente?.razonSocial }}</span>
                <span class="rem-nit">NIT: {{ rem.cliente?.nit }}</span>
                <Tag
                  v-if="rem.cliente?.nit === '222222222222'"
                  value="Consumidor Final"
                  severity="info"
                  style="font-size:0.72rem"
                />
                <!-- Forma de pago badge -->
                <span :class="['fp-badge', 'fp-' + (rem.formaPago || 'CONTADO').toLowerCase()]">
                  <i :class="fpIcono(rem.formaPago)" />
                  {{ fpLabel(rem.formaPago) }}
                  <span v-if="rem.formaPago === 'CREDITO' && rem.diasCredito"> · {{ rem.diasCredito }} días</span>
                </span>
              </div>
              <div class="rem-card-acciones">
                <span class="rem-total">{{ formatCOP(rem.total) }}</span>
                <Button
                  icon="pi pi-pencil"
                  label="Editar"
                  text
                  size="small"
                  severity="info"
                  @click="abrirEditar(rem)"
                  v-tooltip.top="'Editar cantidades o precios'"
                />
                <Button
                  icon="pi pi-times"
                  label="Anular"
                  text
                  size="small"
                  severity="danger"
                  @click="anularRemisionDetalle(rem)"
                  v-tooltip.top="'Anular esta remisión — no se enviará a DIAN'"
                />
              </div>
            </div>

            <!-- Tabla de productos de esta remisión -->
            <table class="rem-items-tabla">
              <thead>
                <tr>
                  <th>Producto</th>
                  <th class="col-num">Cant.</th>
                  <th class="col-num">Precio unit.</th>
                  <th class="col-num">IVA %</th>
                  <th class="col-num">Total línea</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="det in rem.detalles" :key="det.id">
                  <td>{{ det.descripcionProducto }}</td>
                  <td class="col-num">{{ det.cantidad }}</td>
                  <td class="col-num">{{ formatCOP(det.precioUnitario) }}</td>
                  <td class="col-num">{{ det.porcentajeIva }}%</td>
                  <td class="col-num total-linea">{{ formatCOP(det.totalLinea) }}</td>
                </tr>
              </tbody>
              <tfoot>
                <tr>
                  <td colspan="4" class="tfoot-label">Subtotal sin IVA</td>
                  <td class="col-num">{{ formatCOP(rem.subtotal) }}</td>
                </tr>
                <tr v-if="rem.totalIva > 0">
                  <td colspan="4" class="tfoot-label">IVA</td>
                  <td class="col-num">{{ formatCOP(rem.totalIva) }}</td>
                </tr>
                <tr v-if="rem.totalIbua > 0">
                  <td colspan="4" class="tfoot-label">IBUA (Beb. Azucaradas)</td>
                  <td class="col-num">{{ formatCOP(rem.totalIbua) }}</td>
                </tr>
                <tr class="tfoot-total">
                  <td colspan="4" class="tfoot-label"><strong>Total</strong></td>
                  <td class="col-num"><strong>{{ formatCOP(rem.total) }}</strong></td>
                </tr>
              </tfoot>
            </table>

            <div class="rem-obs" v-if="rem.observaciones">
              <i class="pi pi-comment" /> {{ rem.observaciones }}
            </div>
            <div class="rem-obs" v-if="rem.formaPago === 'CREDITO' && rem.fechaVencimientoPago">
              <i class="pi pi-calendar-clock" style="color:#1565C0" />
              <span style="color:#1565C0">Vence: <strong>{{ formatFecha(rem.fechaVencimientoPago) }}</strong></span>
            </div>
          </div>
        </div>

        <!-- Footer del dialog -->
        <div class="detalle-footer">
          <Button label="Cerrar" text @click="dialogDetalle = false" />
          <Button
            v-if="pendientes.length > 0"
            :label="`Confirmar y enviar ${pendientes.length} remisión${pendientes.length !== 1 ? 'es' : ''} a DIAN`"
            icon="pi pi-cloud-upload"
            @click="() => { dialogDetalle = false; confirmarFacturar() }"
          />
        </div>
      </div>
    </Dialog>

    <!-- ─── Dialog factura manual ──────────────────────────── -->
    <Dialog v-model:visible="dialogManual" header="Nueva Factura Manual" modal :style="{width:'600px'}" :draggable="false">
      <div class="factura-form">
        <div class="form-row">
          <div class="field">
            <label>Cliente *</label>
            <Select
              v-model="formManual.clienteId"
              :options="clientes"
              optionValue="id"
              :optionLabel="c => c.razonSocial + ' - ' + c.nit"
              filter
              placeholder="Seleccionar cliente"
              class="w-full"
            />
          </div>
          <div class="field">
            <label>Fecha de emision</label>
            <DatePicker v-model="formManual.fecha" dateFormat="dd/mm/yy" class="w-full" />
          </div>
        </div>

        <div class="form-row">
          <div class="field">
            <label>Forma de pago</label>
            <div class="fp-toggle">
              <button
                v-for="fp in [{value:'CONTADO',label:'Contado'},{value:'CREDITO',label:'Crédito'}]"
                :key="fp.value"
                class="fp-btn"
                :class="{ active: formManual.formaPago === fp.value }"
                @click="formManual.formaPago = fp.value; if (fp.value !== 'CREDITO') formManual.diasCredito = 30"
              >{{ fp.label }}</button>
            </div>
          </div>
          <div class="field" v-if="formManual.formaPago === 'CREDITO'">
            <label>Días de crédito</label>
            <div class="dias-btns">
              <button
                v-for="d in [15, 30, 45, 60, 90]"
                :key="d"
                class="dia-btn"
                :class="{ active: formManual.diasCredito === d }"
                @click="formManual.diasCredito = d"
              >{{ d }}</button>
            </div>
          </div>
        </div>

        <div class="detalles-section">
          <div class="detalles-header">
            <h4>Productos</h4>
            <Button label="Agregar" icon="pi pi-plus" size="small" outlined @click="agregarDetalleFactura" />
          </div>
          <div v-for="(det, idx) in formManual.detalles" :key="idx" class="detalle-row">
            <Select
              v-model="det.productoId"
              :options="productos"
              optionValue="id"
              :optionLabel="p => p.nombreCompleto"
              filter placeholder="Producto"
              class="detalle-producto"
              @change="onProdFactura(det)"
            />
            <InputNumber v-model="det.cantidad" :min="1" placeholder="Cant." style="width:90px" @update:modelValue="calcFact(det)" />
            <InputNumber v-model="det.precioUnitario" mode="currency" currency="COP" locale="es-CO" placeholder="Precio" style="width:130px" @update:modelValue="calcFact(det)" />
            <span class="detalle-subtotal">{{ formatCOP(det.subtotal) }}</span>
            <Button icon="pi pi-times" text rounded severity="danger" @click="formManual.detalles.splice(idx,1)" />
          </div>
        </div>

        <div class="total-factura" v-if="formManual.detalles.length > 0">
          <span>Total: <strong>{{ formatCOP(totalFacturaManual) }}</strong></span>
        </div>

        <div class="field">
          <label>Observaciones</label>
          <Textarea v-model="formManual.observaciones" rows="2" class="w-full" />
        </div>

        <Message v-if="manualError" severity="error" :closable="false">{{ manualError }}</Message>
        <div class="dialog-footer">
          <Button label="Cancelar" text @click="dialogManual = false" />
          <Button label="Crear factura" icon="pi pi-save" @click="crearFacturaManual" :loading="creando" />
        </div>
      </div>
    </Dialog>

  </div>

  <!-- ─── Dialog Ver Remisión (solo lectura) ──────────────── -->
  <Dialog
    v-model:visible="dialogVerRem"
    :header="`Remisión ${remisionVer?.numero || ''}`"
    modal
    :style="{ width: '680px', maxWidth: '98vw' }"
    :draggable="false"
  >
    <div v-if="remisionVer" class="ver-rem-dialog">

      <!-- Cabecera con datos del cliente -->
      <div class="ver-rem-header">
        <div class="ver-rem-info">
          <span class="ver-rem-numero">{{ remisionVer.numero }}</span>
          <span class="ver-rem-cliente">{{ remisionVer.cliente?.razonSocial }}</span>
          <span class="ver-rem-nit">NIT: {{ remisionVer.cliente?.nit }}</span>
          <Tag
            v-if="remisionVer.cliente?.nit === NIT_CF"
            value="Consumidor Final"
            severity="info"
            style="font-size:0.72rem"
          />
          <span :class="['fp-badge', 'fp-' + (remisionVer.formaPago || 'CONTADO').toLowerCase()]">
            <i :class="fpIcono(remisionVer.formaPago)" />
            {{ fpLabel(remisionVer.formaPago) }}
            <span v-if="remisionVer.formaPago === 'CREDITO' && remisionVer.diasCredito">
              · {{ remisionVer.diasCredito }} días
            </span>
          </span>
        </div>
        <span class="ver-rem-total">{{ formatCOP(remisionVer.total) }}</span>
      </div>

      <!-- Tabla de productos -->
      <table class="rem-items-tabla">
        <thead>
          <tr>
            <th>Producto</th>
            <th class="col-num">Cant.</th>
            <th class="col-num">Precio unit.</th>
            <th class="col-num">IVA %</th>
            <th class="col-num">Total línea</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="det in remisionVer.detalles" :key="det.id">
            <td>{{ det.descripcionProducto }}</td>
            <td class="col-num">{{ det.cantidad }}</td>
            <td class="col-num">{{ formatCOP(det.precioUnitario) }}</td>
            <td class="col-num">{{ det.porcentajeIva }}%</td>
            <td class="col-num total-linea">{{ formatCOP(det.totalLinea) }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td colspan="4" class="tfoot-label">Subtotal sin IVA</td>
            <td class="col-num">{{ formatCOP(remisionVer.subtotal) }}</td>
          </tr>
          <tr v-if="remisionVer.totalIva > 0">
            <td colspan="4" class="tfoot-label">IVA</td>
            <td class="col-num">{{ formatCOP(remisionVer.totalIva) }}</td>
          </tr>
          <tr v-if="remisionVer.totalIbua > 0">
            <td colspan="4" class="tfoot-label">IBUA (Beb. Azucaradas)</td>
            <td class="col-num">{{ formatCOP(remisionVer.totalIbua) }}</td>
          </tr>
          <tr class="tfoot-total">
            <td colspan="4" class="tfoot-label"><strong>Total</strong></td>
            <td class="col-num"><strong>{{ formatCOP(remisionVer.total) }}</strong></td>
          </tr>
        </tfoot>
      </table>

      <div class="rem-obs" v-if="remisionVer.observaciones">
        <i class="pi pi-comment" /> {{ remisionVer.observaciones }}
      </div>
      <div class="rem-obs" v-if="remisionVer.formaPago === 'CREDITO' && remisionVer.fechaVencimientoPago">
        <i class="pi pi-calendar-clock" style="color:#1565C0" />
        <span style="color:#1565C0">Vence: <strong>{{ formatFecha(remisionVer.fechaVencimientoPago) }}</strong></span>
      </div>

      <div class="detalle-footer">
        <Button label="Cerrar" text @click="dialogVerRem = false" />
        <Button
          label="Editar esta remisión"
          icon="pi pi-pencil"
          outlined
          severity="info"
          @click="() => { dialogVerRem = false; abrirEditar(remisionVer) }"
        />
      </div>
    </div>
  </Dialog>

  <!-- ─── Dialog Nota Crédito ────────────────────────────── -->
  <Dialog
    v-model:visible="dialogNc"
    header="Crear Nota Crédito"
    modal
    :style="{ width: '780px', maxWidth: '98vw' }"
    :draggable="false"
  >
    <div class="nota-form" v-if="remisionNota">

      <!-- Info de la factura referenciada -->
      <div class="nota-ref-bar">
        <i class="pi pi-file-check" style="color:#1565C0" />
        <span>
          Factura: <strong>{{ remisionNota.numeroFacturaDian }}</strong>
          · Cliente: <strong>{{ remisionNota.cliente?.razonSocial }}</strong>
          · Total: <strong>{{ formatCOP(remisionNota.total) }}</strong>
        </span>
      </div>

      <!-- Tipo NC -->
      <div class="field">
        <label class="field-label">Tipo de nota crédito *</label>
        <div class="tipo-toggle">
          <button
            v-for="t in tiposNc" :key="t.value"
            class="tipo-btn"
            :class="{ active: formNc.tipo === t.value }"
            @click="onCambioTipoNc(t.value)"
          >
            <i :class="t.icon" />
            <span>{{ t.label }}</span>
            <span class="tipo-desc">{{ t.desc }}</span>
          </button>
        </div>
      </div>

      <!-- Motivo -->
      <div class="field">
        <label class="field-label">Motivo *</label>
        <Textarea v-model="formNc.motivo" rows="2" class="w-full" placeholder="Describa el motivo de la nota crédito..." />
      </div>

      <!-- Líneas de la nota -->
      <div class="nota-lineas-section">
        <div class="nota-lineas-header">
          <h4>
            <i class="pi pi-list" style="margin-right:0.4rem" />
            {{ formNc.tipo === 'AJUSTE_PRECIO' ? 'Selecciona los productos a ajustar'
               : formNc.tipo === 'ANULACION'   ? 'Productos de la factura'
               : 'Productos a devolver' }}
          </h4>
        </div>

        <!-- ANULACION: aviso sin tabla -->
        <div v-if="formNc.tipo === 'ANULACION'" class="nc-anulacion-info">
          <i class="pi pi-info-circle" style="font-size:1.1rem;color:#1565C0;flex-shrink:0" />
          <span>Se anularán <strong>todos los productos</strong> de la factura ante la DIAN y se restaurará el stock completo.</span>
        </div>

        <!-- DEVOLUCION / AJUSTE_PRECIO: tabla -->
        <div v-else class="nota-lineas-tabla">

          <!-- Cabecera DEVOLUCION -->
          <div v-if="formNc.tipo === 'DEVOLUCION'" class="nota-lineas-head nl-devol">
            <span>Producto</span>
            <span class="col-nc-num">Cant. a devolver</span>
            <span class="col-nc-num">Precio unit.</span>
            <span class="col-nc-num">IVA %</span>
            <span class="col-nc-num">Subtotal</span>
          </div>
          <!-- Cabecera AJUSTE_PRECIO -->
          <div v-else class="nota-lineas-head nl-ajuste">
            <span style="justify-self:center">✓</span>
            <span>Producto</span>
            <span class="col-nc-num">Cant.</span>
            <span class="col-nc-num">Precio original</span>
            <span class="col-nc-num">Precio nuevo</span>
            <span class="col-nc-num">IVA %</span>
          </div>

          <!-- Filas DEVOLUCION -->
          <template v-if="formNc.tipo === 'DEVOLUCION'">
            <div class="nota-lineas-row nl-devol" v-for="(linea, idx) in formNc.lineas" :key="idx">
              <span class="nc-prod-nombre">{{ linea.descripcion }}</span>
              <InputNumber
                v-model="linea.cantidad"
                :min="1" :max="linea.cantidadMax"
                inputStyle="width:68px;text-align:center"
              />
              <span class="nc-precio-txt">{{ formatCOP(linea.precioUnitarioOriginal) }}</span>
              <span class="nc-iva-txt">{{ linea.porcentajeIva }}%</span>
              <span class="nc-subtotal-txt">
                {{ formatCOP((linea.cantidad ?? 0) * linea.precioUnitarioOriginal * (1 + linea.porcentajeIva / 100)) }}
              </span>
            </div>
          </template>

          <!-- Filas AJUSTE_PRECIO -->
          <template v-else>
            <div
              class="nota-lineas-row nl-ajuste"
              v-for="(linea, idx) in formNc.lineas"
              :key="idx"
              :class="{ 'nc-row-off': !linea.seleccionado }"
            >
              <Checkbox v-model="linea.seleccionado" :binary="true" />
              <span class="nc-prod-nombre" :class="{ 'nc-prod-off': !linea.seleccionado }">{{ linea.descripcion }}</span>
              <span class="col-nc-num" :class="{ 'nc-prod-off': !linea.seleccionado }">{{ linea.cantidad }}</span>
              <span class="nc-precio-txt" :class="{ 'nc-prod-off': !linea.seleccionado }">{{ formatCOP(linea.precioUnitarioOriginal) }}</span>
              <span v-if="linea.seleccionado" style="display:flex;align-items:center;gap:0.3rem">
                <InputNumber
                  v-model="linea.precioUnitarioNuevo"
                  mode="currency" currency="COP" locale="es-CO"
                  :maxFractionDigits="0"
                  placeholder="Precio correcto"
                  inputStyle="width:120px"
                  :class="{ 'p-invalid': linea.precioUnitarioNuevo !== null && linea.precioUnitarioNuevo !== undefined && Number(linea.precioUnitarioNuevo) >= Number(linea.precioUnitarioOriginal) }"
                />
                <i
                  v-if="linea.precioUnitarioNuevo !== null && linea.precioUnitarioNuevo !== undefined && Number(linea.precioUnitarioNuevo) >= Number(linea.precioUnitarioOriginal)"
                  class="pi pi-exclamation-triangle"
                  style="color:#E65100;font-size:1rem"
                  v-tooltip.top="'El precio nuevo debe ser menor que el original para generar crédito'"
                />
              </span>
              <span v-else class="nc-precio-txt nc-prod-off" style="text-align:right">—</span>
              <span class="nc-iva-txt" :class="{ 'nc-prod-off': !linea.seleccionado }">{{ linea.porcentajeIva }}%</span>
            </div>
          </template>
        </div>

        <!-- Barra de total -->
        <div class="nota-total-bar">
          <span>Total de la nota crédito:</span>
          <strong>{{ formNc.tipo === 'ANULACION' ? formatCOP(remisionNota?.total) : formatCOP(totalNc) }}</strong>
        </div>

        <!-- Aviso precio invertido (AJUSTE_PRECIO) -->
        <Message
          v-if="formNc.tipo === 'AJUSTE_PRECIO' && lineasPrecioInvertido.length > 0"
          severity="warn"
          :closable="false"
          style="margin-top:0.5rem"
        >
          <strong>Precio invertido:</strong> El "precio correcto" debe ser
          <strong>menor</strong> que el precio original. Si el precio subió, usa una
          <strong>Nota Débito — Cambio de valor</strong>.
        </Message>
        <Message
          v-else-if="formNc.tipo === 'AJUSTE_PRECIO' && totalNc <= 0 && formNc.lineas.some(l => l.seleccionado)"
          severity="info"
          :closable="false"
          style="margin-top:0.5rem"
        >
          Ingresa el precio correcto (menor al original) en los productos seleccionados.
        </Message>
      </div>

      <Message v-if="errorNc" severity="error" :closable="false">{{ errorNc }}</Message>

      <div class="dialog-footer">
        <Button label="Cancelar" text @click="dialogNc = false" />
        <Button
          label="Crear Nota Crédito"
          icon="pi pi-check"
          severity="success"
          :loading="creandoNc"
          :disabled="formNc.tipo === 'AJUSTE_PRECIO' && (totalNc <= 0 || lineasPrecioInvertido.length > 0)"
          @click="crearNc"
        />
      </div>
    </div>
  </Dialog>

  <!-- ─── Dialog Nota Débito ─────────────────────────────── -->
  <Dialog
    v-model:visible="dialogNd"
    header="Crear Nota Débito"
    modal
    :style="{ width: '720px', maxWidth: '98vw' }"
    :draggable="false"
  >
    <div class="nota-form" v-if="remisionNota">

      <!-- Info de la factura referenciada -->
      <div class="nota-ref-bar nota-ref-bar--nd">
        <i class="pi pi-file-edit" style="color:#E65100" />
        <span>
          Factura: <strong>{{ remisionNota.numeroFacturaDian }}</strong>
          · Cliente: <strong>{{ remisionNota.cliente?.razonSocial }}</strong>
          · Total: <strong>{{ formatCOP(remisionNota.total) }}</strong>
        </span>
      </div>

      <!-- Tipo ND -->
      <div class="field">
        <label class="field-label">Tipo de nota débito *</label>
        <div class="tipo-toggle">
          <button
            v-for="t in tiposNd" :key="t.value"
            class="tipo-btn tipo-btn--nd"
            :class="{ active: formNd.tipo === t.value }"
            @click="formNd.tipo = t.value"
          >
            <i :class="t.icon" />
            <span>{{ t.label }}</span>
            <span class="tipo-desc">{{ t.desc }}</span>
          </button>
        </div>
      </div>

      <!-- Motivo -->
      <div class="field">
        <label class="field-label">Motivo *</label>
        <Textarea v-model="formNd.motivo" rows="2" class="w-full" placeholder="Describa el motivo de la nota débito..." />
      </div>

      <!-- Líneas de la nota débito -->
      <div class="nota-lineas-section">
        <div class="nota-lineas-header">
          <h4>
            <i class="pi pi-list" style="margin-right:0.4rem" />
            {{ formNd.tipo === 'AJUSTE_PRECIO' ? 'Conceptos de cambio de valor' : 'Conceptos a cobrar' }}
          </h4>
          <div style="display:flex;gap:0.4rem;align-items:center">
            <Button
              v-if="formNd.tipo === 'AJUSTE_PRECIO' && lineasFacturaNd.length > 0"
              label="Importar de factura"
              icon="pi pi-download"
              size="small"
              outlined
              severity="secondary"
              v-tooltip.top="'Carga los productos de la factura original como punto de partida'"
              @click="importarProductosFacturaNd"
            />
            <Button
              label="Agregar línea"
              icon="pi pi-plus"
              size="small"
              outlined
              @click="agregarLineaNd"
            />
          </div>
        </div>

        <div class="nota-lineas-tabla">
          <div class="nota-lineas-head nd-head">
            <span>Descripción</span>
            <span class="col-nc-num">Cant.</span>
            <span class="col-nc-num">Valor unitario</span>
            <span class="col-nc-num">IVA %</span>
            <span class="col-nc-btn"></span>
          </div>

          <div
            class="nota-lineas-row nd-row"
            v-for="(linea, idx) in formNd.lineas"
            :key="idx"
          >
            <InputText
              v-model="linea.descripcion"
              placeholder="Descripción del concepto"
              class="w-full"
            />
            <InputNumber
              v-model="linea.cantidad"
              :min="1"
              inputStyle="width:60px;text-align:center"
            />
            <InputNumber
              v-model="linea.precioUnitario"
              mode="currency" currency="COP" locale="es-CO"
              :maxFractionDigits="0"
              inputStyle="width:110px"
            />
            <InputNumber
              v-model="linea.porcentajeIva"
              :min="0" :max="100"
              placeholder="0"
              inputStyle="width:55px;text-align:center"
            />
            <Button
              icon="pi pi-times"
              text rounded size="small" severity="danger"
              @click="formNd.lineas.splice(idx, 1)"
            />
          </div>

          <div v-if="formNd.lineas.length === 0" class="nota-lineas-empty">
            <i class="pi pi-info-circle" /> Agrega al menos una línea con el concepto a cobrar
          </div>
        </div>

        <!-- Total ND -->
        <div v-if="formNd.lineas.length > 0" class="nota-total-bar nota-total-bar--nd">
          <span>Total de la nota débito:</span>
          <strong>{{ formatCOP(totalNd) }}</strong>
        </div>
      </div>

      <Message v-if="errorNd" severity="error" :closable="false">{{ errorNd }}</Message>

      <div class="dialog-footer">
        <Button label="Cancelar" text @click="dialogNd = false" />
        <Button
          label="Crear Nota Débito"
          icon="pi pi-check"
          severity="warn"
          :loading="creandoNd"
          @click="crearNd"
        />
      </div>
    </div>
  </Dialog>

  <!-- ─── Dialog Editar Remisión ──────────────────────────── -->
  <Dialog
    v-model:visible="dialogEditar"
    :header="`Editar remisión ${editandoRem?.numero || ''}`"
    modal
    :style="{ width: '700px', maxWidth: '98vw' }"
    :draggable="false"
  >
    <div class="editar-form" v-if="editandoRem">

      <!-- Selector de cliente -->
      <div class="field">
        <label class="field-label"><i class="pi pi-user" style="margin-right:0.3rem"/>Cliente *</label>
        <Select
          v-model="editClienteId"
          :options="clientes"
          optionValue="id"
          :optionLabel="c => c.razonSocial + ' · ' + c.nit"
          filter
          filterPlaceholder="Buscar cliente..."
          placeholder="Seleccionar cliente"
          class="w-full"
        />
      </div>

      <div class="editar-aviso">
        <i class="pi pi-info-circle" />
        <span>Marca los productos que quieres incluir. Desmarca para quitarlos. Puedes agregar productos que no estaban.</span>
      </div>

      <!-- Resumen rápido seleccionados -->
      <div class="sel-resumen">
        <span class="sel-count">
          <i class="pi pi-check-square" style="color:#2E7D32;margin-right:0.3rem"/>
          {{ editLineas.filter(r=>r.seleccionado).length }} de {{ editLineas.length }} productos seleccionados
        </span>
      </div>

      <table class="editar-tabla">
        <thead>
          <tr>
            <th class="col-chk">✓</th>
            <th>Producto</th>
            <th class="col-num">Cajas</th>
            <th class="col-num">Uds sueltas</th>
            <th class="col-num">Precio / caja</th>
            <th class="col-num">Subtotal</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in editLineas" :key="i"
              :class="{ 'row-activa': row.seleccionado, 'row-vacia': !row.seleccionado }">
            <!-- Checkbox -->
            <td class="col-chk">
              <input
                type="checkbox"
                class="chk-prod"
                v-model="row.seleccionado"
                @change="toggleProd(row)"
              />
            </td>
            <td class="edit-prod-name">{{ row.descripcion }}</td>
            <td class="col-num">
              <InputNumber
                v-model="row.cajas"
                :min="0"
                :disabled="!row.seleccionado"
                inputStyle="width:60px;text-align:center"
                @update:modelValue="calcEditLinea(row)"
              />
            </td>
            <td class="col-num">
              <InputNumber
                v-model="row.unidades"
                :min="0"
                :disabled="!row.seleccionado"
                inputStyle="width:60px;text-align:center"
                @update:modelValue="calcEditLinea(row)"
              />
            </td>
            <td class="col-num">
              <InputNumber
                v-model="row.precioCaja"
                mode="currency" currency="COP" locale="es-CO"
                :maxFractionDigits="0"
                :disabled="!row.seleccionado"
                inputStyle="width:110px"
                @update:modelValue="calcEditLinea(row)"
              />
            </td>
            <td class="col-num edit-sub">{{ row.seleccionado ? formatCOP(row.subtotal) : '—' }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td colspan="5" class="tfoot-label"><strong>Total estimado</strong></td>
            <td class="col-num"><strong>{{ formatCOP(editTotal) }}</strong></td>
          </tr>
        </tfoot>
      </table>

      <!-- Forma de pago en el editor -->
      <div class="field" style="margin-top:1rem">
        <label style="font-size:0.82rem;font-weight:600;color:#555;display:block;margin-bottom:0.4rem">
          <i class="pi pi-wallet" style="margin-right:0.3rem" /> Forma de pago
        </label>
        <div class="fp-toggle">
          <button
            v-for="fp in [{value:'CONTADO',label:'Contado'},{value:'CREDITO',label:'Crédito'},{value:'TRANSFERENCIA',label:'Transferencia'}]"
            :key="fp.value"
            class="fp-btn"
            :class="{ active: editFormaPago === fp.value }"
            @click="editFormaPago = fp.value; if (fp.value !== 'CREDITO') editDiasCredito = 30"
          >{{ fp.label }}</button>
        </div>
        <div v-if="editFormaPago === 'CREDITO'" class="credito-row" style="margin-top:0.5rem">
          <span style="font-size:0.82rem;color:#1565C0">Días:</span>
          <div class="dias-btns">
            <button
              v-for="d in [15,30,45,60,90]" :key="d"
              class="dia-btn" :class="{ active: editDiasCredito === d }"
              @click="editDiasCredito = d"
            >{{ d }}</button>
          </div>
        </div>
      </div>

      <div class="field" style="margin-top:0.75rem">
        <label>Observaciones</label>
        <Textarea v-model="editObs" rows="2" class="w-full" placeholder="Opcional..." />
      </div>

      <Message v-if="editError" severity="error" :closable="false">{{ editError }}</Message>

      <div class="dialog-footer">
        <Button label="Cancelar" text @click="dialogEditar = false" />
        <Button label="Guardar cambios" icon="pi pi-save" @click="guardarEdicion" :loading="guardandoEdit" />
      </div>
    </div>
  </Dialog>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import api from '@/api/axios'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'
import Checkbox from 'primevue/checkbox'
import Tag from 'primevue/tag'
import Tabs from 'primevue/tabs'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'

const toast   = useToast()
const confirm = useConfirm()

// ── Tab ──────────────────────────────────────────────────────
const tabActivo = ref('dian')

// ── Facturación electrónica DIAN ─────────────────────────────
const hoy            = new Date()
const fechaDian      = ref(new Date())
const resumen        = ref(null)
const cargandoResumen = ref(false)
const facturando     = ref(false)
const resultadoEnvio = ref(null)

async function cargarResumen() {
  cargandoResumen.value = true
  resultadoEnvio.value = null
  try {
    const fecha = fechaDian.value
      ? formatLocalDate(fechaDian.value)
      : formatLocalDate(new Date())
    const [resResumen, resPend] = await Promise.allSettled([
      api.get('/facturacion/resumen',        { params: { fecha } }),
      api.get('/facturacion/pendientes-dia', { params: { fecha } })
    ])
    if (resResumen.status === 'fulfilled') resumen.value   = resResumen.value.data
    if (resPend.status   === 'fulfilled') pendientes.value = resPend.value.data || []
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo cargar el resumen', life: 4000 })
  } finally {
    cargandoResumen.value = false
  }
}

function confirmarFacturar() {
  confirm.require({
    message: `¿Confirmas el envío de ${resumen.value.facturasAGenerar} factura(s) a la DIAN vía Siigo?`,
    header: 'Confirmar facturación electrónica',
    icon: 'pi pi-cloud-upload',
    acceptLabel: 'Sí, enviar',
    rejectLabel: 'Cancelar',
    accept: ejecutarFacturacion
  })
}

async function ejecutarFacturacion() {
  facturando.value = true
  resultadoEnvio.value = null
  try {
    const fecha = fechaDian.value
      ? formatLocalDate(fechaDian.value)
      : formatLocalDate(new Date())
    const res = await api.post('/facturacion/facturar-dia', null, { params: { fecha } })
    resultadoEnvio.value = res.data
    const r = res.data
    if (r.errores === 0) {
      toast.add({ severity: 'success', summary: '¡Listo!', detail: r.mensaje, life: 5000 })
    } else {
      toast.add({ severity: 'warn', summary: 'Completado con errores', detail: r.mensaje, life: 6000 })
    }
    await cargarResumen()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'Error al facturar', life: 5000 })
  } finally {
    facturando.value = false
  }
}

// ── Vista previa detallada ────────────────────────────────────
const dialogDetalle    = ref(false)
const pendientes       = ref([])
const cargandoDetalle  = ref(false)

const NIT_CF = '222222222222'
const pendientesCF = computed(() => pendientes.value.filter(r => r.cliente?.nit === NIT_CF))
const totalPendientes  = computed(() =>
  pendientes.value.reduce((s, r) => s + Number(r.total || 0), 0)
)

// ── Búsqueda en tabla de remisiones (panel DIAN) ──────────────
const busquedaRem = ref('')
const pendientesFiltrados = computed(() => {
  if (!busquedaRem.value.trim()) return pendientes.value
  const q = busquedaRem.value.trim().toLowerCase()
  return pendientes.value.filter(r =>
    (r.numero || '').toLowerCase().includes(q) ||
    (r.cliente?.razonSocial || '').toLowerCase().includes(q) ||
    (r.cliente?.nit || '').includes(q)
  )
})

// ── Dialog Ver Remisión (solo lectura) ────────────────────────
const dialogVerRem  = ref(false)
const remisionVer   = ref(null)
function abrirVerRem(rem) {
  remisionVer.value  = rem
  dialogVerRem.value = true
}

// ── Vista previa PDF factura DIAN (marca de agua) ─────────────
const previaCargando = ref({}) // { [remId]: boolean } — spinner por fila

async function previaDian(rem) {
  previaCargando.value[rem.id] = true
  try {
    const res = await api.get(`/remisiones/${rem.id}/preview-dian`, {
      responseType: 'blob'
    })
    const url = URL.createObjectURL(
      new Blob([res.data], { type: 'application/pdf' })
    )
    // Abrir en pestaña nueva para visualización inline en el navegador
    window.open(url, '_blank')
    // Liberar la URL después de un tiempo para no acumular memoria
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'No se pudo generar la vista previa',
      life: 4000
    })
  } finally {
    previaCargando.value[rem.id] = false
  }
}

async function abrirDetalle() {
  cargandoDetalle.value = true
  try {
    const fecha = fechaDian.value
      ? formatLocalDate(fechaDian.value)
      : formatLocalDate(new Date())
    const res = await api.get('/facturacion/pendientes-dia', { params: { fecha } })
    pendientes.value = res.data || []
    dialogDetalle.value = true
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo cargar el detalle', life: 4000 })
  } finally {
    cargandoDetalle.value = false
  }
}

async function anularRemisionDetalle(remision) {
  confirm.require({
    message: `¿Anular la remisión ${remision.numero} (${formatCOP(remision.total)})? No se enviará a la DIAN.`,
    header: 'Anular remisión',
    icon: 'pi pi-exclamation-triangle',
    acceptSeverity: 'danger',
    acceptLabel: 'Anular',
    rejectLabel: 'Cancelar',
    accept: async () => {
      try {
        await api.put(`/remisiones/${remision.id}/anular`)
        toast.add({ severity: 'warn', summary: 'Anulada', detail: `${remision.numero} anulada y excluida del envío`, life: 3000 })
        // Refrescar detalle y resumen
        pendientes.value = pendientes.value.filter(r => r.id !== remision.id)
        await cargarResumen()
      } catch (e) {
        toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'No se pudo anular', life: 4000 })
      }
    }
  })
}

// ── Helpers forma de pago ────────────────────────────────────
function fpLabel(fp) {
  const m = { CONTADO: 'Contado', CREDITO: 'Crédito', TRANSFERENCIA: 'Transferencia', CHEQUE: 'Cheque' }
  return m[fp] || 'Contado'
}
function fpIcono(fp) {
  const m = { CONTADO: 'pi pi-money-bill', CREDITO: 'pi pi-calendar-clock', TRANSFERENCIA: 'pi pi-send', CHEQUE: 'pi pi-file' }
  return m[fp] || 'pi pi-money-bill'
}

// ── Editar remisión ──────────────────────────────────────────
const dialogEditar      = ref(false)
const editandoRem       = ref(null)
const editLineas        = ref([])
const editObs           = ref('')
const editClienteId     = ref(null)
const editFormaPago     = ref('CONTADO')
const editDiasCredito   = ref(30)
const editError         = ref('')
const guardandoEdit     = ref(false)

const editTotal = computed(() =>
  editLineas.value.reduce((s, r) => s + (r.subtotal || 0), 0)
)

function calcEditLinea(row) {
  const upk = row.upk || 1
  const uds = (row.cajas || 0) * upk + (row.unidades || 0)
  const precioU = (row.precioCaja || 0) / upk
  row.subtotal = uds * precioU
  // Auto-seleccionar si el usuario tecleó una cantidad
  if (uds > 0) row.seleccionado = true
}

function toggleProd(row) {
  if (!row.seleccionado) {
    // Al desmarcar → limpiar cantidades
    row.cajas = 0
    row.unidades = 0
    row.subtotal = 0
  } else if (row.cajas === 0 && row.unidades === 0) {
    // Al marcar sin cantidades → poner 1 caja por defecto
    row.cajas = 1
    calcEditLinea(row)
  }
}

async function abrirEditar(rem) {
  editandoRem.value = rem
  editObs.value = rem.observaciones || ''
  editClienteId.value = rem.cliente?.id || null
  editFormaPago.value = rem.formaPago || 'CONTADO'
  editDiasCredito.value = rem.diasCredito || 30
  editError.value = ''
  editLineas.value = []

  try {
    // Cargar todos los detalles del cargue para mostrar productos disponibles
    const cargueId = rem.cargueId
    if (!cargueId) {
      editError.value = 'No se puede identificar el cargue de esta remisión'
      dialogEditar.value = true
      return
    }
    const resCargue = await api.get(`/cargues/${cargueId}`)
    const cargueDetalles = resCargue.data?.detalles || []

    // Mapa de detalles actuales de la remision: productoNombre → {cajas, unidades, precioCaja}
    const remMap = {}
    for (const det of (rem.detalles || [])) {
      const upk = det.producto?.unidadesPorCaja || 1
      const qty = Number(det.cantidad || 0)
      remMap[det.descripcionProducto] = {
        cajas: Math.floor(qty / upk),
        unidades: qty % upk,
        precioCaja: Number(det.precioUnitario || 0) * upk
      }
    }

    // Construir líneas: todos los productos del cargue, pre-llenados con remision actual
    editLineas.value = cargueDetalles.map(cd => {
      const upk = cd.producto?.unidadesPorCaja || 1
      const nombre = cd.producto?.nombreCompleto || ''
      const actual = remMap[nombre] || { cajas: 0, unidades: 0, precioCaja: cd.producto?.precioCaja || 0 }
      const row = {
        cargueDetalleId: cd.id,
        descripcion: nombre,
        upk,
        cajas: actual.cajas,
        unidades: actual.unidades,
        precioCaja: actual.precioCaja || (cd.producto?.precioCaja || 0),
        seleccionado: actual.cajas > 0 || actual.unidades > 0,
        subtotal: 0
      }
      calcEditLinea(row)
      return row
    })
    dialogEditar.value = true
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo cargar el cargue', life: 4000 })
  }
}

async function guardarEdicion() {
  editError.value = ''
  const hayProductos = editLineas.value.some(r => (r.cajas || 0) > 0 || (r.unidades || 0) > 0)
  if (!hayProductos) {
    editError.value = 'Debe ingresar al menos un producto con cantidad mayor a 0'
    return
  }
  guardandoEdit.value = true
  try {
    const rem = editandoRem.value
    const detalles = editLineas.value
      .filter(r => (r.cajas || 0) > 0 || (r.unidades || 0) > 0)
      .map(r => ({
        cargueDetalleId: r.cargueDetalleId,
        cantidadCajas: r.cajas || 0,
        cantidadUnidades: r.unidades || 0,
        precioVentaUnidad: (r.precioCaja || 0) / (r.upk || 1)
      }))

    const payloadEdit = {
      clienteId: editClienteId.value,
      observaciones: editObs.value,
      formaPago: editFormaPago.value,
      detalles
    }
    if (editFormaPago.value === 'CREDITO') {
      payloadEdit.diasCredito = editDiasCredito.value || 30
    }
    await api.put(`/remisiones/${rem.id}/actualizar`, payloadEdit)

    toast.add({ severity: 'success', summary: 'Actualizada', detail: `${rem.numero} actualizada correctamente`, life: 3000 })
    dialogEditar.value = false
    await abrirDetalle()
    await cargarResumen()
  } catch (e) {
    editError.value = e.response?.data?.mensaje || 'Error al actualizar la remisión'
  } finally {
    guardandoEdit.value = false
  }
}

// ── Historial ────────────────────────────────────────────────
const historial         = ref([])
const cargandoHistorial = ref(false)
const sincronizando     = ref(false)
const histInicio        = ref(null)
const histFin           = ref(null)

async function cargarHistorial() {
  cargandoHistorial.value = true
  try {
    const params = {}
    if (histInicio.value) params.inicio = formatLocalDate(histInicio.value)
    if (histFin.value)    params.fin    = formatLocalDate(histFin.value)
    const res = await api.get('/facturacion/historial', { params })
    historial.value = res.data || []
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo cargar el historial', life: 4000 })
  } finally {
    cargandoHistorial.value = false
  }
}

async function sincronizarFacturas() {
  sincronizando.value = true
  try {
    const res = await api.post('/facturacion/sincronizar-facturas')
    const d = res.data || {}
    const creadas = d.facturasCreadas ?? 0
    toast.add({
      severity: creadas > 0 ? 'success' : 'info',
      summary: creadas > 0 ? `${creadas} factura(s) vinculadas` : 'Todo sincronizado',
      detail: creadas > 0
        ? `Se crearon ${creadas} registro(s). Recarga el historial para ver los botones NC/ND.`
        : 'Todas las remisiones ya tenían factura vinculada.',
      life: 6000
    })
    if (creadas > 0) await cargarHistorial()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo sincronizar', life: 4000 })
  } finally {
    sincronizando.value = false
  }
}

async function descargarFacturaPdf(remision) {
  try {
    const res = await api.get(`/facturacion/${remision.id}/pdf`, { responseType: 'blob' })
    const url  = URL.createObjectURL(res.data)
    const a    = document.createElement('a')
    const num  = remision.numeroFacturaDian || ('remision-' + remision.id)
    a.href     = url
    a.download = `factura_${num}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo generar el PDF', life: 4000 })
  }
}

async function reintentarRemision(remision) {
  try {
    const res = await api.post(`/facturacion/reintentar/${remision.id}`)
    const r = res.data
    if (r.exito !== false) {
      toast.add({ severity: 'success', summary: '¡Enviada!', detail: `Factura: ${r.numeroFactura}`, life: 4000 })
    } else {
      toast.add({ severity: 'error', summary: 'Error', detail: r.error || 'Error al reintentar', life: 5000 })
    }
    await cargarHistorial()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Error al reintentar el envío', life: 4000 })
  }
}

// ── Notas Crédito y Débito ────────────────────────────────────
const dialogNc        = ref(false)
const dialogNd        = ref(false)
const remisionNota    = ref(null)   // remision del historial que activó el dialog
const facturaIdNota   = ref(null)   // ID del registro Factura (cargado al abrir el dialog)
const creandoNc       = ref(false)
const creandoNd       = ref(false)
const errorNc         = ref('')
const errorNd         = ref('')
const cargandoNota    = ref({})     // { [remisionId]: 'nc'|'nd'|null }

// Snapshots de líneas de la factura para pre-poblar los diálogos NC/ND
const lineasFacturaNc  = ref([])
const lineasFacturaNd  = ref([])

// Total calculado en tiempo real para la NC
const totalNc = computed(() => {
  const f = formNc.value
  return f.lineas.reduce((sum, l) => {
    if (f.tipo === 'AJUSTE_PRECIO') {
      if (!l.seleccionado) return sum
      const diff = (Number(l.precioUnitarioOriginal) - Number(l.precioUnitarioNuevo || 0))
                   * Number(l.cantidad || 0)
      const positivo = Math.max(0, diff)
      return sum + positivo + positivo * (Number(l.porcentajeIva || 0) / 100)
    }
    const base = Number(l.cantidad || 0) * Number(l.precioUnitarioOriginal || 0)
    return sum + base + base * (Number(l.porcentajeIva || 0) / 100)
  }, 0)
})

// Líneas AJUSTE_PRECIO donde el precio nuevo es mayor o igual al original → precio invertido
const lineasPrecioInvertido = computed(() => {
  if (formNc.value.tipo !== 'AJUSTE_PRECIO') return []
  return formNc.value.lineas.filter(l =>
    l.seleccionado &&
    l.precioUnitarioNuevo !== null &&
    l.precioUnitarioNuevo !== undefined &&
    Number(l.precioUnitarioNuevo) >= Number(l.precioUnitarioOriginal)
  )
})

// Total calculado en tiempo real para la ND
const totalNd = computed(() =>
  formNd.value.lineas.reduce((sum, l) => {
    const base = Number(l.cantidad || 1) * Number(l.precioUnitario || 0)
    return sum + base + base * (Number(l.porcentajeIva || 0) / 100)
  }, 0)
)

const tiposNc = [
  { value: 'DEVOLUCION',    label: 'Devolución',    icon: 'pi pi-undo',         desc: 'Devuelve productos y restaura stock' },
  { value: 'AJUSTE_PRECIO', label: 'Ajuste precio', icon: 'pi pi-tag',          desc: 'Descuento sobre el precio cobrado' },
  { value: 'ANULACION',     label: 'Anulación',     icon: 'pi pi-times-circle', desc: 'Anula la factura completa' },
]
const tiposNd = [
  { value: 'INTERESES',       label: 'Intereses de mora', icon: 'pi pi-percentage',  desc: 'Mora o financiación (Art. 884 C.Co.)' },
  { value: 'AJUSTE_PRECIO',   label: 'Cambio de valor',   icon: 'pi pi-tag',         desc: 'Incremento de precio acordado' },
  { value: 'CARGO_ADICIONAL', label: 'Gastos por cobrar', icon: 'pi pi-plus-circle', desc: 'Flete, seguro u otro cargo extra' },
]

const formNcInicial = () => ({ tipo: 'DEVOLUCION', motivo: '', lineas: [] })
const formNdInicial = () => ({ tipo: 'INTERESES',  motivo: '', lineas: [] })
const formNc = ref(formNcInicial())
const formNd = ref(formNdInicial())

async function resolverFacturaId(remision) {
  if (remision.facturaId) return remision.facturaId
  const res = await api.get(`/facturas/por-numero/${encodeURIComponent(remision.numeroFacturaDian)}`)
  return res.data?.id
}

// Convierte detalles de factura en líneas editables para NC
function _detallesALineasNc(detalles) {
  return (detalles || []).map(d => ({
    productoId:             d.producto?.id ?? null,
    descripcion:            d.descripcion || d.producto?.nombreCompleto || '',
    cantidad:               Number(d.cantidad) || 1,
    cantidadMax:            Number(d.cantidad) || 1,
    precioUnitarioOriginal: Number(d.precioUnitario) || 0,
    precioUnitarioNuevo:    null,
    porcentajeIva:          Number(d.porcentajeIva) || 0,
    seleccionado:           true
  }))
}

async function abrirNc(remision) {
  cargandoNota.value[remision.id] = 'nc'
  try {
    const fid = await resolverFacturaId(remision)
    if (!fid) {
      toast.add({ severity: 'warn', summary: 'Sin factura vinculada',
        detail: 'Esta remisión no tiene factura registrada. Usa el botón ⟳ para sincronizar.', life: 5000 })
      return
    }
    // Cargar factura para pre-poblar los productos
    const facRes  = await api.get(`/facturas/${fid}`)
    const factura = facRes.data
    const lineas  = _detallesALineasNc(factura?.detalles)
    lineasFacturaNc.value     = lineas.map(l => ({ ...l }))   // snapshot inmutable
    remisionNota.value        = { ...remision, facturaId: fid }
    formNc.value              = { tipo: 'DEVOLUCION', motivo: '', lineas: lineas.map(l => ({ ...l })) }
    errorNc.value             = ''
    dialogNc.value            = true
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error',
      detail: 'No se pudo cargar la factura. Intente de nuevo o sincronice primero.', life: 5000 })
  } finally {
    cargandoNota.value[remision.id] = null
  }
}

// Restablecer líneas al cambiar el tipo de NC
function onCambioTipoNc(nuevoTipo) {
  formNc.value.tipo = nuevoTipo
  formNc.value.lineas = lineasFacturaNc.value.map(l => ({
    ...l,
    cantidad:            l.cantidadMax,
    precioUnitarioNuevo: null,
    seleccionado:        true
  }))
}

async function abrirNd(remision) {
  cargandoNota.value[remision.id] = 'nd'
  try {
    const fid = await resolverFacturaId(remision)
    if (!fid) {
      toast.add({ severity: 'warn', summary: 'Sin factura vinculada',
        detail: 'Esta remisión no tiene factura registrada. Usa el botón ⟳ para sincronizar.', life: 5000 })
      return
    }
    // Cargar factura para posible importación de productos (Cambio de valor)
    const facRes  = await api.get(`/facturas/${fid}`)
    const factura = facRes.data
    lineasFacturaNd.value = (factura?.detalles || []).map(d => ({
      descripcion:    d.descripcion || d.producto?.nombreCompleto || '',
      cantidad:       Number(d.cantidad) || 1,
      precioUnitario: Number(d.precioUnitario) || 0,
      porcentajeIva:  Number(d.porcentajeIva) || 0
    }))
    remisionNota.value = { ...remision, facturaId: fid }
    formNd.value       = formNdInicial()
    errorNd.value      = ''
    dialogNd.value     = true
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error',
      detail: 'No se encontró la factura. Usa el botón ⟳ para sincronizar primero.', life: 5000 })
  } finally {
    cargandoNota.value[remision.id] = null
  }
}

function agregarLineaNd() {
  formNd.value.lineas.push({ descripcion: '', cantidad: 1, precioUnitario: 0, porcentajeIva: 0 })
}

// Importar productos de la factura como líneas de la ND (útil para Cambio de valor)
function importarProductosFacturaNd() {
  formNd.value.lineas = lineasFacturaNd.value.map(l => ({ ...l }))
}

async function crearNc() {
  errorNc.value = ''
  const f = formNc.value
  if (!f.motivo.trim()) { errorNc.value = 'Ingrese el motivo'; return }

  let lineasPayload
  if (f.tipo === 'ANULACION') {
    // Anulación total: usar el snapshot completo de la factura
    lineasPayload = lineasFacturaNc.value
  } else if (f.tipo === 'AJUSTE_PRECIO') {
    lineasPayload = f.lineas.filter(l => l.seleccionado)
    if (lineasPayload.length === 0) { errorNc.value = 'Seleccione al menos un producto para ajustar'; return }
    for (const l of lineasPayload) {
      if (l.precioUnitarioNuevo === null || l.precioUnitarioNuevo === undefined || l.precioUnitarioNuevo < 0) {
        errorNc.value = 'Ingrese el precio correcto para cada producto seleccionado'; return
      }
      if (Number(l.precioUnitarioNuevo) >= Number(l.precioUnitarioOriginal)) {
        errorNc.value = `El precio correcto de "${l.descripcion}" debe ser MENOR al original (${formatCOP(l.precioUnitarioOriginal)}). Si el precio subió, usa una Nota Débito.`
        return
      }
    }
    if (totalNc.value <= 0) { errorNc.value = 'El valor total de la nota crédito debe ser mayor a $0'; return }
  } else {
    // DEVOLUCION
    lineasPayload = f.lineas
    if (lineasPayload.length === 0) { errorNc.value = 'No hay productos en la factura'; return }
    for (const l of lineasPayload) {
      if (!l.cantidad || l.cantidad < 1) { errorNc.value = 'La cantidad debe ser mayor a 0 en cada línea'; return }
    }
  }

  creandoNc.value = true
  try {
    const payload = {
      tipo:   f.tipo,
      motivo: f.motivo,
      lineas: lineasPayload.map(l => ({
        productoId:             l.productoId,
        descripcion:            l.descripcion,
        cantidad:               l.cantidad,
        precioUnitarioOriginal: l.precioUnitarioOriginal,
        precioUnitarioNuevo:    f.tipo === 'AJUSTE_PRECIO' ? l.precioUnitarioNuevo : undefined,
        porcentajeIva:          l.porcentajeIva
      }))
    }
    const res  = await api.post(`/notas-credito/factura/${remisionNota.value.facturaId}`, payload)
    const nota = res.data
    toast.add({
      severity: 'success',
      summary:  '¡Nota Crédito creada!',
      detail:   `${nota.numero} — ${formatCOP(nota.valorNota)} · Estado: ${nota.estado}`,
      life: 6000
    })
    dialogNc.value = false
    await cargarHistorial()
  } catch (e) {
    errorNc.value = e.response?.data?.mensaje || 'Error al crear la nota crédito'
  } finally {
    creandoNc.value = false
  }
}

async function crearNd() {
  errorNd.value = ''
  const f = formNd.value
  if (!f.motivo.trim()) { errorNd.value = 'Ingrese el motivo'; return }
  if (f.lineas.length === 0) { errorNd.value = 'Agregue al menos una línea'; return }
  for (const l of f.lineas) {
    if (!l.descripcion.trim()) { errorNd.value = 'Ingrese la descripción en cada línea'; return }
    if (!l.precioUnitario || l.precioUnitario <= 0) { errorNd.value = 'El valor unitario debe ser mayor a 0'; return }
  }
  creandoNd.value = true
  try {
    const payload = {
      tipo:   f.tipo,
      motivo: f.motivo,
      lineas: f.lineas
    }
    const res = await api.post(`/notas-debito/factura/${remisionNota.value.facturaId}`, payload)
    const nota = res.data
    toast.add({
      severity: 'success',
      summary: '¡Nota Débito creada!',
      detail: `${nota.numero} — ${formatCOP(nota.valorNota)} · Estado: ${nota.estado}`,
      life: 6000
    })
    dialogNd.value = false
    await cargarHistorial()
  } catch (e) {
    errorNd.value = e.response?.data?.mensaje || 'Error al crear la nota débito'
  } finally {
    creandoNd.value = false
  }
}

// ── Historial Notas NC / ND ───────────────────────────────────
const notasCredito    = ref([])
const notasDebito     = ref([])
const cargandoNotas   = ref(false)
const ncExpandidas    = ref({})
const ndExpandidas    = ref({})
// Rango de fecha para el filtro (DatePicker range → [Date, Date] | null)
const notasRangoFecha = ref(null)

async function cargarNotas() {
  cargandoNotas.value = true
  try {
    let params = {}
    if (notasRangoFecha.value?.[0]) {
      params.inicio = notasRangoFecha.value[0].toISOString().slice(0, 10)
    }
    if (notasRangoFecha.value?.[1]) {
      params.fin = notasRangoFecha.value[1].toISOString().slice(0, 10)
    }
    const [resNc, resNd] = await Promise.all([
      api.get('/notas-credito/historial', { params }),
      api.get('/notas-debito/historial',  { params })
    ])
    notasCredito.value = resNc.data || []
    notasDebito.value  = resNd.data || []
  } catch (e) {
    const msg = e.response?.data?.mensaje || e.message || 'No se pudo cargar el historial de notas'
    toast.add({ severity: 'error', summary: 'Error cargando notas', detail: msg, life: 8000 })
    console.error('[historial notas]', e.response?.data || e)
  } finally {
    cargandoNotas.value = false
  }
}

// Helpers de etiquetas y severidades para NC
function labelTipoNc(tipo) {
  return { DEVOLUCION: 'Devolución', AJUSTE_PRECIO: 'Ajuste precio', ANULACION: 'Anulación' }[tipo] ?? tipo
}
function severityTipoNc(tipo) {
  return { DEVOLUCION: 'info', AJUSTE_PRECIO: 'warn', ANULACION: 'danger' }[tipo] ?? 'secondary'
}

// Helpers para ND
function labelTipoNd(tipo) {
  return { INTERESES: 'Intereses de mora', AJUSTE_PRECIO: 'Cambio de valor', CARGO_ADICIONAL: 'Gastos por cobrar' }[tipo] ?? tipo
}
function severityTipoNd(tipo) {
  return { INTERESES: 'warn', AJUSTE_PRECIO: 'info', CARGO_ADICIONAL: 'secondary' }[tipo] ?? 'secondary'
}

// Estado de la nota ante DIAN
function severityEstadoNota(estado) {
  return { EMITIDA: 'success', PENDIENTE: 'warn', RECHAZADA: 'danger' }[estado] ?? 'secondary'
}

// ── Facturas manuales (existente) ─────────────────────────────
const facturas     = ref([])
const clientes     = ref([])
const productos    = ref([])
const loading      = ref(true)
const filtro       = ref('')
const filtroEstado = ref(null)
const dialogManual = ref(false)
const creando      = ref(false)
const manualError  = ref('')

const estadosFactura = ['BORRADOR', 'EMITIDA', 'ANULADA']

const formManualInicial = () => ({
  clienteId: null, fecha: new Date(), detalles: [], observaciones: '',
  formaPago: 'CONTADO', diasCredito: 30
})
const formManual = ref(formManualInicial())

const facturasFiltradas = computed(() => {
  return facturas.value.filter(f => {
    const mFiltro = !filtro.value ||
      f.numero?.toLowerCase().includes(filtro.value.toLowerCase()) ||
      f.cliente?.razonSocial?.toLowerCase().includes(filtro.value.toLowerCase())
    const mEstado = !filtroEstado.value || f.estado === filtroEstado.value
    return mFiltro && mEstado
  })
})

const totalFacturaManual = computed(() =>
  formManual.value.detalles.reduce((s, d) => s + (d.subtotal || 0), 0)
)

function estadoLabel(e) {
  return { BORRADOR: 'Borrador', EMITIDA: 'Emitida', ANULADA: 'Anulada' }[e] || e
}
function estadoSeverity(e) {
  return { BORRADOR: 'secondary', EMITIDA: 'success', ANULADA: 'danger' }[e] || 'secondary'
}
// Convierte Date a 'YYYY-MM-DD' usando hora LOCAL (no UTC)
function formatLocalDate(date) {
  if (!date) return formatLocalDate(new Date())
  const d = date instanceof Date ? date : new Date(date)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

function formatFecha(f) { return f ? new Date(f).toLocaleDateString('es-CO') : '-' }
function formatCOP(v) {
  if (!v && v !== 0) return '-'
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(v)
}

function agregarDetalleFactura() {
  formManual.value.detalles.push({ productoId: null, cantidad: 1, precioUnitario: 0, subtotal: 0 })
}
function onProdFactura(det) {
  const p = productos.value.find(x => x.id === det.productoId)
  if (p) { det.precioUnitario = p.precioUnidad || p.precioCaja / p.unidadesPorCaja }
  calcFact(det)
}
function calcFact(det) {
  det.subtotal = (det.cantidad || 0) * (det.precioUnitario || 0)
}

async function crearFacturaManual() {
  manualError.value = ''
  if (!formManual.value.clienteId) { manualError.value = 'Seleccione un cliente'; return }
  if (formManual.value.detalles.length === 0) { manualError.value = 'Agregue al menos un producto'; return }
  creando.value = true
  try {
    await api.post('/facturas/manual', formManual.value)
    toast.add({ severity: 'success', summary: 'Factura creada', detail: 'Factura creada exitosamente', life: 3000 })
    dialogManual.value = false
    formManual.value = formManualInicial()
    cargarFacturas()
  } catch (e) {
    manualError.value = e.response?.data?.mensaje || 'Error al crear factura'
  } finally {
    creando.value = false
  }
}

async function emitirSiigo(factura) {
  try {
    await api.post(`/facturas/${factura.id}/emitir`)
    toast.add({ severity: 'success', summary: 'Emitida', detail: 'Factura enviada a Siigo (DIAN)', life: 3000 })
    cargarFacturas()
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error Siigo', detail: e.response?.data?.mensaje || 'Error al emitir en Siigo', life: 5000 })
  }
}

async function descargarPdf(factura) {
  try {
    const res = await api.get(`/facturas/${factura.id}/pdf`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url; a.download = `factura_${factura.numero}.pdf`; a.click()
    URL.revokeObjectURL(url)
  } catch {
    toast.add({ severity: 'error', summary: 'Error', detail: 'No se pudo generar el PDF', life: 4000 })
  }
}

async function enviarEmail(factura) {
  try {
    await api.post(`/facturas/${factura.id}/enviar-email`)
    toast.add({ severity: 'success', summary: 'Email enviado', detail: 'Factura enviada al cliente', life: 3000 })
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'Error al enviar email', life: 4000 })
  }
}

function confirmarAnular(factura) {
  confirm.require({
    message: `¿Anular factura ${factura.numero}? Esta accion no se puede revertir.`,
    header: 'Confirmar anulacion',
    icon: 'pi pi-exclamation-triangle',
    acceptSeverity: 'danger',
    acceptLabel: 'Anular', rejectLabel: 'Cancelar',
    accept: async () => {
      try {
        await api.patch(`/facturas/${factura.id}/anular`)
        toast.add({ severity: 'warn', summary: 'Anulada', detail: 'Factura anulada correctamente', life: 3000 })
        cargarFacturas()
      } catch (e) {
        toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.mensaje || 'Error al anular', life: 4000 })
      }
    }
  })
}

async function cargarFacturas() {
  loading.value = true
  try {
    const [resF, resC, resP] = await Promise.all([
      api.get('/facturas'),
      api.get('/clientes'),
      api.get('/productos')
    ])
    facturas.value  = Array.isArray(resF.data) ? resF.data : []
    clientes.value  = Array.isArray(resC.data) ? resC.data : []
    productos.value = Array.isArray(resP.data) ? resP.data : []
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Error cargando facturacion', life: 4000 })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  cargarFacturas()
  cargarResumen()
  cargarHistorial()
})

// Cargar notas automáticamente al entrar a la pestaña por primera vez
watch(tabActivo, (val) => {
  if (val === 'notas' && notasCredito.value.length === 0 && notasDebito.value.length === 0) {
    cargarNotas()
  }
})
</script>

<style scoped>
.facturacion-page { display: flex; flex-direction: column; gap: 1rem; }

/* ── Panel DIAN ──────────────────────────────────────────────── */
.dian-panel { display: flex; flex-direction: column; gap: 1.25rem; padding-top: 1rem; }

.dian-toolbar {
  display: flex; align-items: flex-end; gap: 1rem; flex-wrap: wrap;
  background: white; padding: 1rem 1.25rem;
  border-radius: 10px; box-shadow: 0 2px 6px rgba(0,0,0,0.05);
}
.campo-fecha { display: flex; flex-direction: column; gap: 0.35rem; }
.campo-fecha label { font-size: 0.85rem; font-weight: 600; color: #444; }

.resumen-card {
  background: white; border-radius: 12px; padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.07); display: flex; flex-direction: column; gap: 1.25rem;
}

/* Indicadores */
.indicadores { display: flex; gap: 1rem; flex-wrap: wrap; }
.ind-item {
  flex: 1; min-width: 120px; border-radius: 10px; padding: 1rem;
  display: flex; flex-direction: column; align-items: center; gap: 0.3rem;
}
.ind-pendiente { background: #FFF3E0; }
.ind-ok        { background: #E8F5E9; }
.ind-error     { background: #FFEBEE; }
.ind-facturas  { background: #E3F2FD; }
.ind-num  { font-size: 2rem; font-weight: 800; }
.ind-label { font-size: 0.78rem; color: #666; text-align: center; }
.ind-pendiente .ind-num { color: #E65100; }
.ind-ok        .ind-num { color: #2E7D32; }
.ind-error     .ind-num { color: #C62828; }
.ind-facturas  .ind-num { color: #1565C0; }

/* Agrupación */
.agrupacion-info {
  background: #f8f9fa; border-radius: 8px; padding: 0.75rem 1rem;
  display: flex; flex-direction: column; gap: 0.5rem;
}
.agrup-item { display: flex; align-items: center; gap: 0.6rem; font-size: 0.9rem; color: #444; }
.agrup-item i { color: #1565C0; }

/* Tabla resumen */
.tabla-resumen { border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden; }
.tabla-resumen-header, .tabla-resumen-row {
  display: grid;
  grid-template-columns: 2fr 1.2fr 0.6fr 1.2fr 1fr;
  gap: 0.5rem; padding: 0.6rem 1rem; align-items: center;
}
.tabla-resumen-header {
  background: #f5f5f5; font-size: 0.78rem;
  font-weight: 700; color: #666; text-transform: uppercase; letter-spacing: 0.3px;
}
.tabla-resumen-row { font-size: 0.88rem; border-top: 1px solid #f0f0f0; }
.tabla-resumen-row:hover { background: #fafafa; }
.nombre-col { font-weight: 600; color: #333; }
.nit-col    { color: #666; }
.cant-col   { text-align: center; font-weight: 600; color: #1565C0; }
.total-col  { font-weight: 700; color: #D32F2F; }

/* Totales */
.totales-resumen {
  border-top: 1px solid #f0f0f0; padding-top: 0.75rem;
  display: flex; flex-direction: column; gap: 0.3rem; align-items: flex-end;
}
.total-fila { display: flex; gap: 1.5rem; font-size: 0.9rem; color: #555; }
.total-final { font-size: 1.1rem; font-weight: 700; color: #D32F2F; border-top: 2px solid #f0f0f0; padding-top: 0.5rem; }

/* Botón facturar */
.btn-facturar { display: flex; justify-content: flex-end; }

/* Sin pendientes */
.sin-pendientes {
  display: flex; flex-direction: column; align-items: center; gap: 0.75rem;
  padding: 2rem; text-align: center; color: #666;
}

/* Resultado */
.resultado-card {
  border-radius: 10px; padding: 1rem 1.25rem;
  display: flex; flex-direction: column; gap: 0.5rem;
}
.resultado-ok   { background: #E8F5E9; border-left: 4px solid #2E7D32; }
.resultado-warn { background: #FFF8E1; border-left: 4px solid #F57F17; }
.resultado-header {
  display: flex; align-items: center; gap: 0.6rem;
  font-weight: 700; font-size: 1rem;
}
.resultado-ok   .resultado-header { color: #2E7D32; }
.resultado-warn .resultado-header { color: #E65100; }
.resultado-detalle { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.9rem; color: #555; }

/* ── Advertencia DIAN ───────────────────────────────────────── */
.advertencia-dian {
  display: flex; align-items: flex-start; gap: 0.6rem;
  background: #FFF8E1; border-left: 4px solid #F9A825;
  border-radius: 6px; padding: 0.75rem 1rem; font-size: 0.88rem; color: #555;
}
.advertencia-dian i { color: #F9A825; flex-shrink: 0; margin-top: 2px; }

/* ── Botón facturar (ahora con 2 botones) ───────────────────── */
.btn-facturar { display: flex; justify-content: flex-end; gap: 0.75rem; flex-wrap: wrap; }

/* ── Dialog detalle ─────────────────────────────────────────── */
.detalle-dialog  { display: flex; flex-direction: column; gap: 1rem; }

.aviso-legal {
  display: flex; align-items: flex-start; gap: 0.6rem;
  background: #ffebee; border-left: 4px solid #C62828;
  border-radius: 6px; padding: 0.75rem 1rem; font-size: 0.87rem; color: #444;
}
.aviso-legal i { color: #C62828; flex-shrink: 0; margin-top: 2px; }

.detalle-vacio { display: flex; flex-direction: column; align-items: center; gap: 0.5rem; padding: 2rem; color: #888; }

.detalle-totales-rapidos {
  display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;
  background: #f5f5f5; padding: 0.6rem 1rem; border-radius: 8px;
  font-size: 0.88rem; color: #555;
}
.detalle-totales-rapidos strong { color: #D32F2F; }
.sep { color: #ccc; }
.aviso-cf { display: flex; align-items: center; gap: 0.3rem; color: #1565C0; }

/* Tarjeta de cada remisión */
.rem-card {
  border: 1px solid #e0e0e0; border-radius: 10px; overflow: hidden;
  margin-bottom: 0.5rem;
}
.rem-card-header {
  display: flex; justify-content: space-between; align-items: center;
  background: #fafafa; padding: 0.6rem 1rem; flex-wrap: wrap; gap: 0.5rem;
  border-bottom: 1px solid #f0f0f0;
}
.rem-card-info   { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
.rem-numero      { font-weight: 700; color: #555; font-size: 0.85rem; }
.rem-cliente     { font-weight: 600; color: #333; }
.rem-nit         { font-size: 0.8rem; color: #999; }
.rem-card-acciones { display: flex; align-items: center; gap: 0.75rem; }
.rem-total       { font-weight: 800; color: #D32F2F; font-size: 1rem; }

/* Tabla de ítems */
.rem-items-tabla {
  width: 100%; border-collapse: collapse; font-size: 0.85rem;
}
.rem-items-tabla th {
  background: #f5f5f5; padding: 0.5rem 0.75rem;
  text-align: left; font-size: 0.78rem; color: #666;
  text-transform: uppercase; letter-spacing: 0.3px;
  border-bottom: 1px solid #e0e0e0;
}
.rem-items-tabla td { padding: 0.45rem 0.75rem; border-bottom: 1px solid #f5f5f5; }
.rem-items-tabla tbody tr:hover { background: #fafafa; }
.col-num { text-align: right; }
.total-linea { font-weight: 600; color: #333; }
.tfoot-label { color: #666; text-align: right; padding-right: 1rem; }
.tfoot-total td { background: #f9f9f9; padding-top: 0.6rem; border-top: 1px solid #e0e0e0; }

.rem-obs { padding: 0.5rem 1rem; font-size: 0.82rem; color: #888; display: flex; align-items: center; gap: 0.4rem; }

/* ── Forma de pago badge ──────────────────────────────────── */
.fp-badge {
  display: inline-flex; align-items: center; gap: 0.3rem;
  padding: 2px 8px; border-radius: 12px;
  font-size: 0.72rem; font-weight: 700;
}
.fp-badge.fp-contado       { background: #E8F5E9; color: #2E7D32; }
.fp-badge.fp-credito       { background: #E3F2FD; color: #1565C0; }
.fp-badge.fp-transferencia { background: #F3E5F5; color: #6A1B9A; }
.fp-badge.fp-cheque        { background: #FFF3E0; color: #E65100; }

/* ── fp-toggle / credito-row (reutilizados en edit dialog) ── */
.fp-toggle { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.fp-btn {
  padding: 0.3rem 0.8rem; border: 1.5px solid #e0e0e0; border-radius: 16px;
  background: white; font-size: 0.78rem; font-weight: 600; color: #888;
  cursor: pointer; transition: all 0.2s;
}
.fp-btn.active { border-color: #1565C0; background: #E3F2FD; color: #1565C0; }
.credito-row { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
.dias-btns { display: flex; gap: 0.3rem; }
.dia-btn {
  padding: 0.15rem 0.55rem; border: 1.5px solid #90CAF9; border-radius: 12px;
  background: white; font-size: 0.75rem; font-weight: 700; color: #1565C0; cursor: pointer;
  transition: all 0.15s;
}
.dia-btn.active { background: #1565C0; color: white; border-color: #1565C0; }

.detalle-footer {
  display: flex; justify-content: flex-end; gap: 0.75rem;
  padding-top: 0.75rem; border-top: 1px solid #f0f0f0; margin-top: 0.5rem;
}

/* ── Historial ───────────────────────────────────────────────── */
.historial-section {
  background: white; border-radius: 12px; padding: 1.25rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.07);
}
.historial-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  flex-wrap: wrap; gap: 0.75rem; margin-bottom: 1rem;
}
.historial-titulo { margin: 0; font-size: 1rem; color: #333; display: flex; align-items: center; gap: 0.5rem; }
.historial-filtros { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
.historial-loading { display: flex; align-items: center; gap: 0.5rem; padding: 1.5rem; color: #888; }
.historial-vacio   { display: flex; flex-direction: column; align-items: center; gap: 0.5rem; padding: 2rem; color: #aaa; }

.historial-tabla { display: flex; flex-direction: column; }
.hist-row {
  display: grid;
  grid-template-columns: 1.1fr 1.2fr 0.9fr 2fr 1.1fr 0.9fr 1.4fr;
  gap: 0.5rem; padding: 0.55rem 0.75rem; align-items: center;
  font-size: 0.85rem; border-bottom: 1px solid #f5f5f5;
}
.hist-header {
  background: #f5f5f5; font-size: 0.75rem; font-weight: 700;
  color: #666; text-transform: uppercase; letter-spacing: 0.3px;
  border-radius: 6px; border-bottom: none; margin-bottom: 2px;
}
.hist-ok:hover  { background: #fafafa; }
.hist-error     { background: #fff8f8; }
.hist-error:hover { background: #fff0f0; }
.hist-num    { color: #555; font-size: 0.82rem; }
.hist-fev    { font-size: 0.88rem; }
.hist-fev strong { color: #D32F2F; }
.sin-fev     { color: #ccc; }
.hist-cliente { color: #333; font-weight: 500; }
.hist-total  { font-weight: 700; color: #1565C0; }
.hist-acciones { display: flex; gap: 0.25rem; }

/* ── Panel manual ────────────────────────────────────────────── */
.manual-panel { display: flex; flex-direction: column; gap: 1rem; padding-top: 1rem; }
.page-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  background: white; padding: 1rem 1.25rem; border-radius: 10px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05); gap: 1rem; flex-wrap: wrap;
}
.toolbar-left  { display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: center; }
.select-small  { width: 180px; }
.card-table { background: white; border-radius: 10px; padding: 1rem; box-shadow: 0 2px 6px rgba(0,0,0,0.05); }

/* ── Dialog factura manual ─────────────────────────────────── */
.factura-form    { display: flex; flex-direction: column; gap: 1rem; }
.form-row        { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.field           { display: flex; flex-direction: column; gap: 0.35rem; }
.field label     { font-size: 0.85rem; font-weight: 600; color: #444; }
.detalles-section  { border: 1px solid #e0e0e0; border-radius: 8px; padding: 1rem; }
.detalles-header   { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem; }
.detalles-header h4 { margin: 0; }
.detalle-row       { display: flex; align-items: center; gap: 0.5rem; padding: 0.4rem 0; border-bottom: 1px solid #f5f5f5; }
.detalle-producto  { flex: 1; min-width: 160px; }
.detalle-subtotal  { font-weight: 700; color: #333; min-width: 100px; text-align: right; }
.total-factura     { background: #f9f9f9; padding: 0.75rem 1rem; border-radius: 8px; font-size: 1rem; text-align: right; }
.total-factura strong { color: #D32F2F; font-size: 1.2rem; }
.dialog-footer     { display: flex; justify-content: flex-end; gap: 0.75rem; padding-top: 0.5rem; border-top: 1px solid #f0f0f0; margin-top: 0.5rem; }

/* ── Dialog editar remisión ──────────────────────────────── */
.editar-form   { display: flex; flex-direction: column; gap: 1rem; }
.field-label   { font-size: 0.82rem; font-weight: 600; color: #555; display: block; margin-bottom: 0.4rem; }
/* Filas seleccionadas / deseleccionadas */
.row-activa td { background: #f0faf0 !important; }
.row-activa .edit-prod-name { color: #1B5E20; font-weight: 700; }
.row-vacia  td { background: #fafafa !important; opacity: 0.55; }
.row-vacia  .edit-prod-name { color: #999; }
/* Checkbox columna */
.col-chk { width: 36px; text-align: center !important; }
.chk-prod {
  width: 17px; height: 17px; cursor: pointer;
  accent-color: #2E7D32;
}
/* Resumen seleccionados */
.sel-resumen {
  display: flex; align-items: center;
  background: #f5f5f5; border-radius: 8px; padding: 0.45rem 0.9rem;
  font-size: 0.82rem;
}
.sel-count { color: #444; font-weight: 600; }
.editar-aviso  {
  display: flex; align-items: flex-start; gap: 0.5rem;
  background: #E3F2FD; color: #1565C0; padding: 0.6rem 0.8rem;
  border-radius: 8px; font-size: 0.85rem;
}
.editar-tabla {
  width: 100%; border-collapse: collapse; font-size: 0.85rem;
}
.editar-tabla th {
  background: #f5f5f5; padding: 0.5rem 0.6rem; text-align: left;
  font-weight: 700; color: #555; border-bottom: 2px solid #e0e0e0;
}
.editar-tabla td { padding: 0.4rem 0.6rem; border-bottom: 1px solid #f0f0f0; vertical-align: middle; }
.editar-tabla tfoot td { border-top: 2px solid #e0e0e0; border-bottom: none; font-size: 0.9rem; }
.edit-prod-name { font-weight: 500; color: #333; max-width: 200px; }
.edit-sub       { color: #D32F2F; font-weight: 700; }

/* ── Barra búsqueda remisiones ─────────────────────────────── */
.rem-busqueda-bar {
  display: flex; align-items: center; gap: 1rem; flex-wrap: wrap;
}
.rem-search-wrap { flex: 1; min-width: 220px; position: relative; display: flex; align-items: center; }
.rem-search-wrap > .pi { position: absolute; left: 0.75rem; color: #aaa; z-index: 1; pointer-events: none; }
.rem-search-input { width: 100%; padding-left: 2.2rem !important; }
.rem-count-info { font-size: 0.8rem; color: #888; white-space: nowrap; }

/* ── Tabla por remisión ─────────────────────────────────────── */
.tabla-remisiones { border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden; }

.tabla-rem-header {
  display: grid;
  grid-template-columns: 1.2fr 2fr 1.3fr 1.2fr 1fr 1.1fr;
  gap: 0.5rem; padding: 0.55rem 0.9rem; align-items: center;
  background: #f5f5f5; font-size: 0.75rem; font-weight: 700;
  color: #666; text-transform: uppercase; letter-spacing: 0.3px;
}
.col-acciones-hdr { text-align: center; }

.tabla-rem-row {
  display: grid;
  grid-template-columns: 1.2fr 2fr 1.3fr 1.2fr 1fr 1.1fr;
  gap: 0.5rem; padding: 0.55rem 0.9rem; align-items: center;
  font-size: 0.87rem; border-top: 1px solid #f0f0f0;
  transition: background 0.1s;
}
.tabla-rem-row:hover { background: #fafafa; }

.rem-num-col  { color: #555; }
.rem-num-col strong { color: #1565C0; }
.rem-cli-col  { font-weight: 600; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rem-nit-col  { color: #888; font-size: 0.82rem; }
.rem-tot-col  { font-weight: 700; color: #D32F2F; }
.rem-acc-col  { display: flex; gap: 0.15rem; justify-content: center; }

.tabla-rem-empty {
  padding: 1rem 1.25rem; font-size: 0.88rem; color: #aaa;
  display: flex; align-items: center; gap: 0.5rem;
  border-top: 1px solid #f0f0f0;
}

/* ── Notas Crédito / Débito ─────────────────────────────────── */
.nota-form { display: flex; flex-direction: column; gap: 1rem; }

.nota-ref-bar {
  display: flex; align-items: center; gap: 0.6rem;
  background: #E3F2FD; border-left: 4px solid #1565C0;
  border-radius: 6px; padding: 0.6rem 0.9rem;
  font-size: 0.86rem; color: #333;
}
.nota-ref-bar--nd {
  background: #FFF3E0; border-left-color: #E65100;
}
.nota-ref-bar strong { color: #1565C0; }
.nota-ref-bar--nd strong { color: #E65100; }

/* Tipo toggle */
.tipo-toggle { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.tipo-btn {
  display: flex; flex-direction: column; align-items: flex-start; gap: 2px;
  padding: 0.5rem 0.9rem; border: 1.5px solid #e0e0e0; border-radius: 10px;
  background: white; cursor: pointer; font-size: 0.84rem; font-weight: 600;
  color: #555; transition: all 0.15s; min-width: 130px;
}
.tipo-btn i { font-size: 1rem; color: #1565C0; }
.tipo-btn.active { border-color: #1565C0; background: #E3F2FD; color: #1565C0; }
.tipo-btn--nd i { color: #E65100; }
.tipo-btn--nd.active { border-color: #E65100; background: #FFF3E0; color: #E65100; }
.tipo-desc { font-size: 0.72rem; font-weight: 400; color: #999; }
.tipo-btn.active .tipo-desc { color: #5c8fcf; }
.tipo-btn--nd.active .tipo-desc { color: #d4814a; }

/* Líneas */
.nota-lineas-section { border: 1px solid #e8e8e8; border-radius: 10px; overflow: hidden; }
.nota-lineas-header {
  display: flex; justify-content: space-between; align-items: center;
  background: #fafafa; padding: 0.6rem 1rem; border-bottom: 1px solid #e8e8e8;
}
.nota-lineas-header h4 { margin: 0; font-size: 0.9rem; color: #333; }


.nota-lineas-head {
  display: grid;
  gap: 0.4rem; padding: 0.45rem 0.8rem;
  background: #f5f5f5; font-size: 0.72rem; font-weight: 700;
  color: #666; text-transform: uppercase; letter-spacing: 0.3px;
}
/* Devolución: Producto | Cant. | Precio | IVA | Subtotal */
.nota-lineas-head.nl-devol,
.nota-lineas-row.nl-devol {
  grid-template-columns: 2.2fr 0.85fr 1.1fr 0.55fr 1fr;
}
/* Ajuste precio NC: ✓ | Producto | Cant | P.orig | P.nuevo | IVA */
.nota-lineas-head.nl-ajuste,
.nota-lineas-row.nl-ajuste {
  grid-template-columns: 1.8rem 2fr 0.6fr 1.05fr 1.15fr 0.55fr;
}
/* ND: Descripción | Cant | Valor | IVA | ✕ */
.nota-lineas-head.nd-head,
.nota-lineas-row.nd-row {
  grid-template-columns: 2.2fr 0.7fr 1.2fr 0.6fr 2.2rem;
}
.nota-lineas-row {
  display: grid;
  gap: 0.4rem; padding: 0.45rem 0.8rem; align-items: center;
  border-top: 1px solid #f2f2f2; font-size: 0.84rem;
}
.nota-lineas-row:hover { background: #fafafa; }
.col-nc-num { text-align: right; }

.nota-lineas-empty {
  padding: 1rem; font-size: 0.86rem; color: #aaa;
  display: flex; align-items: center; gap: 0.4rem;
  border-top: 1px solid #f2f2f2;
}

/* Textos dentro de las filas NC */
.nc-prod-nombre { font-size: 0.84rem; font-weight: 500; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nc-precio-txt  { font-size: 0.83rem; color: #555; text-align: right; }
.nc-iva-txt     { font-size: 0.83rem; color: #777; text-align: right; }
.nc-subtotal-txt{ font-size: 0.83rem; font-weight: 600; color: #1565C0; text-align: right; }

/* Fila desactivada (ajuste precio — no seleccionada) */
.nc-row-off { background: #fafafa; }
.nc-prod-off { color: #bbb !important; text-decoration: line-through; }

/* Aviso anulación */
.nc-anulacion-info {
  display: flex; align-items: flex-start; gap: 0.7rem;
  padding: 0.9rem 1rem; background: #E3F2FD;
  font-size: 0.88rem; color: #333; border-top: 1px solid #e0e0e0;
}

/* Barra de total */
.nota-total-bar {
  display: flex; justify-content: flex-end; align-items: center;
  gap: 0.6rem; padding: 0.5rem 1rem;
  background: #f0f7ff; border-top: 1px solid #c8dff8;
  font-size: 0.88rem; color: #444;
}
.nota-total-bar strong { font-size: 1rem; color: #1565C0; }
.nota-total-bar--nd { background: #fff8f0; border-top-color: #f7c99a; }
.nota-total-bar--nd strong { color: #E65100; }

/* ── Panel Notas NC/ND ──────────────────────────────────────── */
.notas-panel { display: flex; flex-direction: column; gap: 1.25rem; padding-top: 1rem; }

.notas-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: 0.75rem;
}
.notas-toolbar-left  { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
.notas-toolbar-right { display: flex; align-items: center; gap: 0.4rem; }
.notas-resumen { font-size: 0.85rem; }

.notas-bloque { border: 1px solid #e8e8e8; border-radius: 10px; overflow: hidden; }
.notas-bloque-header {
  display: flex; align-items: center; gap: 0.5rem;
  padding: 0.65rem 1rem; font-weight: 700; font-size: 0.9rem;
}
.notas-bloque-nc { background: #E8F5E9; color: #2E7D32; border-bottom: 1px solid #C8E6C9; }
.notas-bloque-nd { background: #FFF3E0; color: #BF360C; border-bottom: 1px solid #FFCCBC; }
.notas-bloque-sub { font-weight: 400; font-size: 0.78rem; opacity: 0.75; margin-left: 0.25rem; }

.notas-table { border-radius: 0; }
.nota-numero   { font-weight: 700; font-size: 0.86rem; letter-spacing: 0.3px; }
.nc-num { color: #2E7D32; }
.nd-num { color: #BF360C; }
.nota-factura-ref { font-size: 0.84rem; color: #1565C0; font-weight: 600; }
.nota-valor { font-size: 0.92rem; }
.nc-val { color: #2E7D32; }
.nd-val { color: #BF360C; }

/* Chips estado Siigo */
.siigo-id-chip      { background: #E8F5E9; color: #2E7D32; border-radius: 12px; padding: 2px 8px; font-size: 0.78rem; font-weight: 600; }
.siigo-error-chip   { background: #FFEBEE; color: #C62828; border-radius: 12px; padding: 2px 8px; font-size: 0.78rem; cursor: help; }
.siigo-pending-chip { background: #FFF9C4; color: #795548; border-radius: 12px; padding: 2px 8px; font-size: 0.78rem; }

/* Fila expandida — detalle de líneas */
.nota-expansion {
  padding: 0.75rem 1.25rem 1rem;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
}
.nota-expansion-motivo {
  display: flex; align-items: flex-start; gap: 0.4rem;
  font-size: 0.85rem; color: #555; margin-bottom: 0.7rem;
}
.nota-expansion-motivo i { color: #888; margin-top: 2px; }

.nota-det-table {
  width: 100%; border-collapse: collapse; font-size: 0.83rem;
}
.nota-det-table th {
  text-align: left; font-weight: 700; color: #666; font-size: 0.72rem;
  text-transform: uppercase; letter-spacing: 0.3px;
  padding: 0.3rem 0.6rem; border-bottom: 1px solid #e0e0e0;
  background: #f5f5f5;
}
.nota-det-table td {
  padding: 0.38rem 0.6rem; border-bottom: 1px solid #f2f2f2; color: #333;
}
.nota-det-table tr:last-child td { border-bottom: none; }
.nota-det-table td:nth-child(2),
.nota-det-table td:nth-child(3),
.nota-det-table td:nth-child(4),
.nota-det-table td:nth-child(5),
.nota-det-table td:last-child { text-align: right; }
.nota-det-table th:nth-child(2),
.nota-det-table th:nth-child(3),
.nota-det-table th:nth-child(4),
.nota-det-table th:nth-child(5),
.nota-det-table th:last-child { text-align: right; }

.notas-empty {
  padding: 1.5rem; text-align: center; color: #aaa; font-size: 0.88rem;
  display: flex; align-items: center; justify-content: center; gap: 0.5rem;
}

/* ── Dialog Ver Remisión ────────────────────────────────────── */
.ver-rem-dialog { display: flex; flex-direction: column; gap: 1rem; }
.ver-rem-header {
  display: flex; justify-content: space-between; align-items: center;
  background: #fafafa; padding: 0.75rem 1rem;
  border: 1px solid #e0e0e0; border-radius: 8px; flex-wrap: wrap; gap: 0.5rem;
}
.ver-rem-info  { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
.ver-rem-numero  { font-weight: 700; color: #555; font-size: 0.85rem; }
.ver-rem-cliente { font-weight: 600; color: #333; }
.ver-rem-nit     { font-size: 0.8rem; color: #999; }
.ver-rem-total   { font-weight: 800; color: #D32F2F; font-size: 1.1rem; flex-shrink: 0; }
</style>
