package pe.buildshield.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Seguridad del gateway. Dos cadenas, como en el Core:
 * <ol>
 *   <li>Rutas públicas (registro, inicio de sesión, renovación, recuperación, salud, documentación) y
 *       preflight CORS: <b>sin</b> validación de token. Un token vencido enviado por costumbre no debe
 *       impedir renovar la sesión.</li>
 *   <li>Todo lo demás: exige un JWT RS256 válido del emisor de IAM. La autorización por rol la hace
 *       cada servicio con {@code @PreAuthorize}; el gateway solo corta lo no autenticado y el servicio
 *       vuelve a validar el token (defensa en profundidad, QAS04).</li>
 * </ol>
 * CORS se resuelve aquí una sola vez para la web ({@code http://localhost:4200}).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class GatewaySecurityConfiguration {

    @Bean
    ReactiveJwtDecoder jwtDecoder(GatewaySecurityProperties properties) {
        return BuildshieldJwt.decoder(PemKeys.publicKey(properties.jwtPublicKey()), properties.issuer(),
                Clock.systemUTC());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewaySecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setExposedHeaders(List.of("X-Correlation-Id", "Location", "Content-Disposition"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    @Order(1)
    SecurityWebFilterChain publicRoutes(ServerHttpSecurity http, GatewaySecurityProperties properties,
            CorsConfigurationSource cors) {
        return stateless(http, cors)
                .securityMatcher(new OrServerWebExchangeMatcher(
                        ServerWebExchangeMatchers.pathMatchers(HttpMethod.OPTIONS, "/**"),
                        ServerWebExchangeMatchers.pathMatchers(properties.allPublicPaths())))
                .authorizeExchange(auth -> auth.anyExchange().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityWebFilterChain protectedRoutes(ServerHttpSecurity http, CorsConfigurationSource cors,
            ReactiveJwtDecoder jwtDecoder) {
        ServerAuthenticationEntryPoint unauthenticated = (exchange, ex) -> JsonErrorWriter.write(exchange,
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Debes iniciar sesión para usar este recurso");
        ServerAccessDeniedHandler forbidden = (exchange, ex) -> JsonErrorWriter.write(exchange,
                HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para realizar esta operación");
        return stateless(http, cors)
                .authorizeExchange(auth -> auth.anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder))
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .build();
    }

    private static ServerHttpSecurity stateless(ServerHttpSecurity http, CorsConfigurationSource cors) {
        return http
                .cors(c -> c.configurationSource(cors))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable);
    }
}
