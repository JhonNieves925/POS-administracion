package com.distriasociados.inventario.config;

import com.distriasociados.inventario.exception.NegocioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "facturacion.proveedor", havingValue = "siigo")
public class SiigoClientService {

    private final RestTemplate restTemplate;

    @Value("${siigo.api.url}")
    private String baseUrl;

    @Value("${siigo.api.username}")
    private String username;

    @Value("${siigo.api.access-key}")
    private String accessKey;

    @Value("${siigo.api.partner-id}")
    private String partnerId;

    public SiigoClientService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restTemplate = new RestTemplate(factory);
    }

    // Autenticación — obtiene el token de acceso
    public String obtenerToken() {
        String url = baseUrl + "/auth";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
            "username", username,
            "access_key", accessKey
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getBody() == null || !response.getBody().containsKey("access_token")) {
                log.error("Siigo autenticación: respuesta sin access_token");
                throw new NegocioException("No se pudo autenticar con el proveedor de facturación DIAN");
            }

            return response.getBody().get("access_token").toString();

        } catch (HttpStatusCodeException e) {
            log.error("Siigo autenticación HTTP {}: {}", e.getStatusCode(), e.getStatusText());
            throw new NegocioException("Error de autenticación con el proveedor DIAN. Verifique las credenciales.");
        } catch (ResourceAccessException e) {
            log.error("Siigo no disponible: {}", e.getMessage());
            throw new NegocioException("El servicio de facturación DIAN no está disponible. Intente en unos minutos.");
        }
    }

    // Crear factura electrónica en Siigo
    public Map crearFactura(String token, Map<String, Object> payload) {
        String url = baseUrl + "/v1/invoices";

        HttpHeaders headers = crearHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Siigo crearFactura HTTP {}: {}", e.getStatusCode(), e.getStatusText());
            throw new NegocioException("La DIAN rechazó la factura. Verifique los datos e intente nuevamente.");
        } catch (ResourceAccessException e) {
            log.error("Siigo crearFactura timeout: {}", e.getMessage());
            throw new NegocioException("El servicio de facturación DIAN no respondió a tiempo. Intente nuevamente.");
        }
    }

    // Consultar factura por ID de Siigo
    public Map consultarFactura(String token, String siigoId) {
        String url = baseUrl + "/v1/invoices/" + siigoId;

        HttpEntity<Void> request = new HttpEntity<>(crearHeaders(token));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Siigo consultarFactura HTTP {}: {}", e.getStatusCode(), e.getStatusText());
            throw new NegocioException("No se pudo consultar la factura en la DIAN.");
        } catch (ResourceAccessException e) {
            log.error("Siigo consultarFactura timeout: {}", e.getMessage());
            throw new NegocioException("El servicio de facturación DIAN no respondió a tiempo.");
        }
    }

    // Anular factura
    public Map anularFactura(String token, String siigoId) {
        String url = baseUrl + "/v1/invoices/" + siigoId;

        HttpEntity<Void> request = new HttpEntity<>(crearHeaders(token));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, request, Map.class);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Siigo anularFactura HTTP {}: {}", e.getStatusCode(), e.getStatusText());
            throw new NegocioException("La DIAN no pudo anular la factura. Verifique el estado e intente nuevamente.");
        } catch (ResourceAccessException e) {
            log.error("Siigo anularFactura timeout: {}", e.getMessage());
            throw new NegocioException("El servicio de facturación DIAN no respondió a tiempo.");
        }
    }

    // Headers comunes para todas las peticiones a Siigo
    private HttpHeaders crearHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("Partner-Id", partnerId);
        return headers;
    }
}