package com.distriasociados.inventario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class InventarioApplication {

	public static void main(String[] args) {
		// Forzar zona horaria Colombia en toda la JVM
		// Evita que LocalDate.now() devuelva el día anterior cuando el SO está en UTC
		TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
		SpringApplication.run(InventarioApplication.class, args);
	}

}
