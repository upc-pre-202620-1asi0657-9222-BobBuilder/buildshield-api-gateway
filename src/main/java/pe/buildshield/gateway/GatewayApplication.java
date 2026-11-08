package pe.buildshield.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * API Gateway de BuildShield: única entrada HTTP (puerto 8080). Enruta cada prefijo de {@code /api/v1}
 * a su microservicio, valida el JWT antes de reenviar, agrega {@code X-Correlation-Id}, resuelve CORS
 * una sola vez y publica el Swagger agregado de todos los servicios.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
