package com.distriasociados.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

@Entity                         // Le dice a Spring: "esto es una tabla"
@Table(name = "usuarios")       // Nombre de la tabla en PostgreSQL
@Data                           // Lombok: genera getters, setters automáticamente
@Builder                        // Lombok: permite construir objetos fácilmente
@NoArgsConstructor              // Lombok: constructor vacío (lo necesita JPA)
@AllArgsConstructor             // Lombok: constructor con todos los campos
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 15)
    private String cedula;          // Esta es la que usan para hacer login

    @Column(length = 100)
    private String correo;

    @Column(length = 20)
    private String celular;
    
    @JsonProperty(access = Access.WRITE_ONLY)  // Entra al crear/resetear, nunca sale en respuestas
    @Column(nullable = false)
    private String password;        // Se guarda encriptada, nunca en texto plano

    @Enumerated(EnumType.STRING)    // Guarda el rol como texto en la BD
    @Column(nullable = false)
    private Rol rol;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(nullable = false)
    private Boolean primerLogin = true;  // Al primer login obliga a cambiar contraseña

    @Column(nullable = false)
    private Boolean recordarme = false;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    private LocalDateTime actualizadoEn;

    @PrePersist     // Se ejecuta automáticamente antes de guardar
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        actualizadoEn = LocalDateTime.now();
    }

    @PreUpdate      // Se ejecuta automáticamente antes de actualizar
    protected void onUpdate() {
        actualizadoEn = LocalDateTime.now();
    }

    // Enum dentro de la misma clase para los roles
    public enum Rol {
        ADMIN,
        AUXILIAR,
        VENDEDOR
    }
}