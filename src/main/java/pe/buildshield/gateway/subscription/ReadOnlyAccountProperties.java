package pe.buildshield.gateway.subscription;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Cuenta en solo lectura al vencer la suscripción ({@code buildshield.read-only.*}, US10).
 *
 * @param enabled     aplica el corte en el gateway (por defecto sí)
 * @param cacheTtl    cuánto se recuerda el estado de la suscripción de cada organización
 * @param timeout     espera máxima de la consulta a subscription-service
 * @param exemptPaths rutas que cambian estado y aun así pasan: lo necesario para pagar y para la sesión
 */
@ConfigurationProperties(prefix = "buildshield.read-only")
public record ReadOnlyAccountProperties(Boolean enabled, Duration cacheTtl, Duration timeout,
        List<String> exemptPaths) {

    public ReadOnlyAccountProperties {
        enabled = enabled == null || enabled;
        cacheTtl = cacheTtl == null ? Duration.ofSeconds(30) : cacheTtl;
        timeout = timeout == null ? Duration.ofSeconds(2) : timeout;
        exemptPaths = exemptPaths == null ? List.of("/api/v1/subscriptions/**", "/api/v1/plans/**",
                "/api/v1/auth/**") : List.copyOf(exemptPaths);
    }
}
