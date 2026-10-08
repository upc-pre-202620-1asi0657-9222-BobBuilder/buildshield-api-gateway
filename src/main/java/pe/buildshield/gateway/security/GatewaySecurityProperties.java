package pe.buildshield.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.stream.Stream;

/**
 * Configuración de seguridad del gateway ({@code buildshield.security.*}).
 *
 * @param issuer                emisor esperado en el claim {@code iss} (el de IAM)
 * @param jwtPublicKey          clave pública RSA en PEM ({@code BUILDSHIELD_JWT_PUBLIC_KEY})
 * @param publicPaths           rutas que pasan sin token (registro, inicio de sesión, salud, documentación)
 * @param additionalPublicPaths rutas públicas extra por perfil (por ejemplo, las rutas del actuator en local)
 * @param allowedOrigins        orígenes permitidos por CORS (la web en {@code http://localhost:4200})
 */
@ConfigurationProperties(prefix = "buildshield.security")
public record GatewaySecurityProperties(String issuer, String jwtPublicKey, List<String> publicPaths,
        List<String> additionalPublicPaths, List<String> allowedOrigins) {

    public GatewaySecurityProperties {
        issuer = issuer == null ? "buildshield-iam" : issuer;
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        additionalPublicPaths = additionalPublicPaths == null ? List.of() : List.copyOf(additionalPublicPaths);
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    /** Todas las rutas públicas: las comunes más las del perfil activo. */
    public String[] allPublicPaths() {
        return Stream.concat(publicPaths.stream(), additionalPublicPaths.stream()).toArray(String[]::new);
    }
}
