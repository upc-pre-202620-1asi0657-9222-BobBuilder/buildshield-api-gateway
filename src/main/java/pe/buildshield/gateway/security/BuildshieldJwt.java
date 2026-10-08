package pe.buildshield.gateway.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;

/**
 * Política de tokens de BuildShield, la misma del Core y de los servicios: RS256, vigencia de 15
 * minutos y 30 s de tolerancia de reloj. Versión reactiva para el gateway.
 */
public final class BuildshieldJwt {

    public static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private BuildshieldJwt() {
    }

    /** Decodificador que valida firma, emisor, vigencia y claims obligatorios. */
    public static ReactiveJwtDecoder decoder(RSAPublicKey publicKey, String issuer, Clock clock) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtIssuerValidator(issuer),
                new TokenPolicyValidator(TOKEN_LIFETIME, CLOCK_SKEW, clock)));
        return decoder;
    }
}
