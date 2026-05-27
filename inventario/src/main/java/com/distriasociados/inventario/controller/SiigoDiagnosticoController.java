package com.distriasociados.inventario.controller;

import com.distriasociados.inventario.exception.NegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/siigo/diagnostico")
public class SiigoDiagnosticoController {

    @Data
    public static class DiagnosticoRequest {
        @NotBlank(message = "username requerido") private String username;
        @NotBlank(message = "accessKey requerido") private String accessKey;
        @NotBlank(message = "partnerId requerido") private String partnerId;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> diagnostico(
            @Valid @RequestBody DiagnosticoRequest req) {

        RestTemplate rt = crearRestTemplate();
        String token = autenticar(rt, req);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Partner-Id", req.getPartnerId());
        HttpEntity<?> entity = new HttpEntity<>(headers);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("autenticacion", "OK");

        resultado.put("documentosFV",
            consultarSiigo(rt, "https://api.siigo.com/v1/document-types?type=FV", entity));
        resultado.put("documentosNC",
            consultarSiigo(rt, "https://api.siigo.com/v1/document-types?type=NC", entity));
        resultado.put("documentosND",
            consultarSiigo(rt, "https://api.siigo.com/v1/document-types?type=ND", entity));
        resultado.put("impuestos",
            consultarSiigo(rt, "https://api.siigo.com/v1/taxes", entity));
        resultado.put("formasPagoFV",
            consultarSiigo(rt, "https://api.siigo.com/v1/payment-types?document_type=FV", entity));

        return ResponseEntity.ok(resultado);
    }

    private String autenticar(RestTemplate rt, DiagnosticoRequest req) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.set("Partner-Id", req.getPartnerId());

            Map<String, String> body = Map.of(
                "username", req.getUsername(),
                "access_key", req.getAccessKey()
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = rt.exchange(
                "https://api.siigo.com/auth",
                HttpMethod.POST,
                new HttpEntity<>(body, h),
                Map.class
            ).getBody();

            if (resp == null || resp.get("access_token") == null) {
                throw new NegocioException("Siigo no devolvió access_token. Verifica las credenciales.");
            }
            return resp.get("access_token").toString();

        } catch (HttpStatusCodeException e) {
            throw new NegocioException("Error al autenticar en Siigo (" + e.getStatusCode() +
                "): credenciales incorrectas o Partner-Id inválido.");
        } catch (NegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new NegocioException("No se pudo conectar con Siigo: " + e.getMessage());
        }
    }

    private Object consultarSiigo(RestTemplate rt, String url, HttpEntity<?> entity) {
        try {
            return rt.exchange(url, HttpMethod.GET, entity, Object.class).getBody();
        } catch (HttpStatusCodeException e) {
            return Map.of("error", "HTTP " + e.getStatusCode(), "detalle", e.getResponseBodyAsString());
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    private RestTemplate crearRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return new RestTemplate(factory);
    }
}
