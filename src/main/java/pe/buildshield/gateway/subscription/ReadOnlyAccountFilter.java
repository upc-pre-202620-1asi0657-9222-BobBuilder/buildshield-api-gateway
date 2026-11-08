package pe.buildshield.gateway.subscription;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * US10: con la suscripción vencida la cuenta queda en solo lectura. El gateway corta toda operación que cambie
 * estado (POST, PUT, PATCH y DELETE) de una organización en solo lectura con 402 {@code SUBSCRIPTION_READ_ONLY}, en
 * el formato único de error. Las lecturas pasan siempre, y también lo necesario para pagar y para la sesión
 * ({@code buildshield.read-only.exempt-paths}: suscripción, checkout, webhook de Stripe, planes, auth).
 *
 * <p>El estado se consulta a subscription-service (dueño de la suscripción) con caché corta por organización: así
 * el corte no depende de copias en cada servicio y, al pagar, el permiso vuelve en segundos. Cualquier petición a
 * {@code /api/v1/subscriptions/**} de la organización olvida el estado recordado (la web consulta su suscripción
 * después de pagar).
 */
@Component
public class ReadOnlyAccountFilter implements GlobalFilter, Ordered {

    public static final String SUBSCRIPTION_READ_ONLY = "SUBSCRIPTION_READ_ONLY";
    static final String MESSAGE = "La suscripción de tu organización venció: la cuenta está en solo lectura. "
            + "Puedes consultar tu información; el administrador puede pagar un plan para volver a registrar cambios.";

    private static final Set<HttpMethod> WRITES = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH,
            HttpMethod.DELETE);
    private static final String SUBSCRIPTION_PATHS = "/api/v1/subscriptions/**";

    private final SubscriptionStatusClient subscriptions;
    private final ReadOnlyAccountProperties properties;
    private final AntPathMatcher paths = new AntPathMatcher();

    public ReadOnlyAccountFilter(SubscriptionStatusClient subscriptions, ReadOnlyAccountProperties properties) {
        this.subscriptions = subscriptions;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        HttpMethod method = exchange.getRequest().getMethod();
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .flatMap(token -> {
                    UUID organizationId = UUID.fromString(token.getToken().getClaimAsString("org"));
                    if (paths.match(SUBSCRIPTION_PATHS, path)) {
                        subscriptions.forget(organizationId);
                    }
                    if (!WRITES.contains(method) || exempt(path)) {
                        return Mono.just(true);
                    }
                    String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                    return subscriptions.readOnly(organizationId, authorization).map(readOnly -> !readOnly);
                })
                .defaultIfEmpty(true)
                .flatMap(allowed -> allowed ? chain.filter(exchange) : reject(exchange));
    }

    private boolean exempt(String path) {
        return properties.exemptPaths().stream().anyMatch(pattern -> paths.match(pattern, path));
    }

    private static Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.PAYMENT_REQUIRED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":\"" + SUBSCRIPTION_READ_ONLY + "\",\"message\":\"" + MESSAGE + "\",\"details\":[]}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /** Después de la seguridad (ya hay token validado) y antes del enrutamiento al servicio. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
