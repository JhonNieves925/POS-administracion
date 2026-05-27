package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "clientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPersona tipoPersona;    // NATURAL o JURIDICA

    @Column(nullable = false, length = 150)
    private String razonSocial;         // Nombre o razón social

    @Column(nullable = false, unique = true, length = 15)
    private String nit;                 // NIT o cédula

    @Column(length = 1)
    private String dv;                  // Dígito de verificación del NIT

    @Column(length = 20)
    private String telefono;

    @Column(length = 100)
    private String correo;

    @Column(length = 200)
    private String direccion;

    @Column(length = 80)
    private String ciudad;

    @Column(length = 80)
    private String municipio;

    @Enumerated(EnumType.STRING)
    private Regimen regimen;            // Tributario

    // Opciones de envío de remisión/factura
    @Column(nullable = false)
    private Boolean envioCorreo = false;

    @Column(nullable = false)
    private Boolean envioWhatsapp = false;  // Inactivo hasta pagar API

    @Column(nullable = false)
    private Boolean soloFacturaFisica = true;

    @Column(length = 50)
    private String siigoId;             // ID del cliente en Siigo

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    private LocalDateTime actualizadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        actualizadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        actualizadoEn = LocalDateTime.now();
    }

    public enum TipoPersona {
        NATURAL, JURIDICA
    }

    public enum Regimen {
        SIMPLIFICADO, COMUN, GRAN_CONTRIBUYENTE, NO_RESPONSABLE
    }
}