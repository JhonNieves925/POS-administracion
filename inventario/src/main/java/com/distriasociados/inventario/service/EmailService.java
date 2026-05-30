package com.distriasociados.inventario.service;

import com.distriasociados.inventario.entity.Cliente;
import com.distriasociados.inventario.entity.Remision;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Servicio de envío de correos electrónicos.
 *
 * El método principal {@link #enviarFacturaElectronica(Remision)} es @Async:
 * se ejecuta en un hilo separado para no bloquear la respuesta de facturación.
 * Cualquier fallo de correo es capturado y registrado — NUNCA interrumpe la facturación.
 *
 * Prerequisitos en Render:
 *   GMAIL_USERNAME    = dirección Gmail desde la que se envían los correos (ej: termuxh245@gmail.com)
 *   GMAIL_APP_PASSWORD = App Password de 16 caracteres generada en myaccount.google.com/security
 *
 * IMPORTANTE: con Gmail SMTP el remitente (From) debe ser el mismo correo autenticado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final PdfService     pdfService;

    /**
     * Debe coincidir con spring.mail.username — Gmail rechaza si el From es diferente al autenticado.
     */
    @Value("${spring.mail.username:termuxh245@gmail.com}")
    private String correoRemitente;

    @Value("${empresa.nombre:DISTRIASOCIADOS SAS}")
    private String nombreEmpresa;

    @Value("${empresa.nit:901.897.762-1}")
    private String nitEmpresa;

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat      FMT_COP   =
        NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    // ──────────────────────────────────────────────────────────────────────
    //  FACTURA ELECTRÓNICA DIAN
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Envía la factura electrónica al cliente de forma asíncrona.
     * Solo actúa si el cliente tiene correo registrado y {@code envioCorreo = true}.
     *
     * @param remision Remisión ya marcada como FACTURADA con numeroFacturaDian asignado.
     */
    @Async
    public void enviarFacturaElectronica(Remision remision) {
        Cliente cliente = remision.getCliente();

        // ── Guardas de elegibilidad ──────────────────────────────────────
        if (cliente.getCorreo() == null || cliente.getCorreo().isBlank()) {
            log.debug("[EMAIL] Cliente '{}' sin correo — omitiendo envío de factura",
                cliente.getRazonSocial());
            return;
        }
        if (!Boolean.TRUE.equals(cliente.getEnvioCorreo())) {
            log.debug("[EMAIL] Cliente '{}' tiene envioCorreo=false — omitiendo",
                cliente.getRazonSocial());
            return;
        }

        // ── Construcción y envío ─────────────────────────────────────────
        try {
            // 1. Generar PDF de la factura electrónica
            byte[] pdfBytes = pdfService.generarFacturaDian(remision);

            // 2. Construir el mensaje MIME con adjunto
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");

            helper.setFrom(correoRemitente, nombreEmpresa);
            helper.setTo(cliente.getCorreo());
            helper.setSubject("Factura Electrónica " + remision.getNumeroFacturaDian()
                + " — " + nombreEmpresa);
            helper.setText(construirHtmlFactura(remision, cliente), true);

            // 3. Adjuntar PDF
            String nombreArchivo = "factura_"
                + remision.getNumeroFacturaDian()
                    .replace("/", "-")
                    .replace(" ", "_")
                + ".pdf";
            helper.addAttachment(nombreArchivo,
                new ByteArrayResource(pdfBytes), "application/pdf");

            // 4. Enviar
            mailSender.send(mensaje);
            log.info("[EMAIL] ✅ Factura {} enviada a {} — {}",
                remision.getNumeroFacturaDian(),
                cliente.getCorreo(),
                cliente.getRazonSocial());

        } catch (MessagingException e) {
            log.error("[EMAIL] Error construyendo mensaje para {}: {}",
                cliente.getCorreo(), e.getMessage());
        } catch (Exception e) {
            // Capturamos TODO — el fallo de correo NUNCA debe afectar la facturación
            log.error("[EMAIL] Error enviando factura {} a {}: {}",
                remision.getNumeroFacturaDian(), cliente.getCorreo(), e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  CONSTRUCCIÓN DEL HTML
    // ──────────────────────────────────────────────────────────────────────

    private String construirHtmlFactura(Remision remision, Cliente cliente) {
        String fechaStr    = remision.getFecha() != null
            ? remision.getFecha().format(FMT_FECHA) : "—";
        String totalStr    = formatearCOP(remision.getTotal());
        String numFactura  = esc(remision.getNumeroFacturaDian());
        String nomCliente  = esc(cliente.getRazonSocial());
        String nomEmpresa  = esc(nombreEmpresa);

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>"

            // ── Contenedor principal ──────────────────────────────────────
            + "<div style='font-family:Arial,Helvetica,sans-serif;"
            + "max-width:600px;margin:0 auto;border:1px solid #e0e0e0;border-radius:8px;"
            + "overflow:hidden;'>"

            // ── Cabecera roja ─────────────────────────────────────────────
            + "<div style='background:#D32F2F;padding:20px 24px;'>"
            + "<h1 style='color:white;margin:0;font-size:20px;'>" + nomEmpresa + "</h1>"
            + "<p style='color:#ffcdd2;margin:5px 0 0;font-size:12px;'>NIT "
            + esc(nitEmpresa) + " &nbsp;·&nbsp; Facturación Electrónica DIAN</p>"
            + "</div>"

            // ── Saludo ────────────────────────────────────────────────────
            + "<div style='padding:24px;background:#f9f9f9;'>"
            + "<p style='color:#333;font-size:15px;margin-top:0;'>"
            + "Estimado/a <strong>" + nomCliente + "</strong>,</p>"
            + "<p style='color:#555;font-size:14px;'>"
            + "Adjunto encontrará su <strong>factura electrónica <em>" + numFactura + "</em></strong> "
            + "emitida y validada ante la DIAN. Puede abrirla directamente desde el archivo PDF adjunto."
            + "</p>"

            // ── Recuadro de datos ─────────────────────────────────────────
            + "<div style='background:white;border:1px solid #e0e0e0;border-radius:8px;"
            + "padding:16px 20px;margin:16px 0;'>"
            + "<table style='width:100%;border-collapse:collapse;'>"
            + fila("N.° Factura DIAN",
                "<strong>" + numFactura + "</strong>", false)
            + fila("Fecha de emisión", fechaStr, false)
            + fila("Cliente", nomCliente, false)
            + fila("Total",
                "<span style='font-weight:bold;color:#D32F2F;font-size:17px;'>"
                + totalStr + "</span>", true)
            + "</table></div>"

            // ── Aviso DIAN ────────────────────────────────────────────────
            + "<div style='background:#e8f5e9;border-left:4px solid #2E7D32;"
            + "padding:10px 14px;border-radius:0 6px 6px 0;margin-bottom:16px;'>"
            + "<p style='color:#1B5E20;font-size:13px;margin:0;'>"
            + "Esta factura tiene plena validez tributaria ante la DIAN y ha sido "
            + "firmada electrónicamente según la Ley 527 de 1999."
            + "</p></div>"

            // ── Contacto ──────────────────────────────────────────────────
            + "<p style='color:#888;font-size:12px;'>"
            + "¿Preguntas sobre esta factura? Contáctenos: "
            + "<a href='mailto:" + esc(correoRemitente) + "' style='color:#D32F2F;'>"
            + esc(correoRemitente) + "</a>"
            + "</p></div>"

            // ── Pie de página ─────────────────────────────────────────────
            + "<div style='background:#eeeeee;padding:10px 24px;text-align:center;'>"
            + "<p style='color:#aaa;font-size:11px;margin:0;'>"
            + "Mensaje automático generado por el sistema de facturación de "
            + nomEmpresa + ". No responda a este correo."
            + "</p></div>"

            + "</div></body></html>";
    }

    /** Genera una fila de la tabla de datos de la factura. */
    private String fila(String etiqueta, String valor, boolean conBordeSup) {
        String bordeStyle = conBordeSup
            ? "border-top:1px solid #f0f0f0;padding-top:10px;" : "";
        return "<tr>"
            + "<td style='color:#888;font-size:13px;padding:5px 0;" + bordeStyle + "'>"
            + esc(etiqueta) + "</td>"
            + "<td style='font-size:13px;text-align:right;" + bordeStyle + "'>"
            + valor + "</td>"
            + "</tr>";
    }

    /** Escapa caracteres HTML para evitar inyección en el cuerpo del correo. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String formatearCOP(BigDecimal valor) {
        if (valor == null) return "—";
        return FMT_COP.format(valor);
    }
}
