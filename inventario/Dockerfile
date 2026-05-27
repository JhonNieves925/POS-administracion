# ─── Etapa 1: Compilar el proyecto con Maven ─────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copiar primero solo el pom y wrapper para aprovechar la caché de capas
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Dar permisos de ejecución al wrapper
RUN chmod +x mvnw

# Descargar dependencias (cacheado si pom.xml no cambia)
RUN ./mvnw dependency:go-offline -q

# Copiar el código fuente y compilar
COPY src ./src
RUN ./mvnw clean package -DskipTests -q

# ─── Etapa 2: Imagen final solo con JRE (más liviana) ─────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copiar solo el JAR generado
COPY --from=build /app/target/inventario-0.0.1-SNAPSHOT.jar app.jar

# Puerto que expone Spring Boot
EXPOSE 8080

# Variables de entorno por defecto (se sobreescriben en Render)
ENV SPRING_PROFILES_ACTIVE=prod

# Arrancar la aplicación con límite de memoria para el plan gratuito
ENTRYPOINT ["java", "-Xmx450m", "-Xms200m", "-jar", "app.jar"]
