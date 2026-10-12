package pe.buildshield.gateway.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado de la suscripción de cada organización, leído de {@code GET /api/v1/subscriptions/current} de
 * subscription-service con el mismo token del usuario y recordado por organización durante
 * {@code buildshield.read-only.cache-ttl} (30 s por defecto).
 *
 * <p>Si subscription-service no responde (caído, lento o sin suscripción todavía) la cuenta se trata como operativa
 * y no se recuerda nada: una falla del servicio de pagos no debe congelar las operaciones de todas las empresas.
 */
@Component
public class SubscriptionStatusClient {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionStatusClient.class);

    private final WebClient http;
    private final ReadOnlyAccountProperties properties;
    private final Clock clock;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public SubscriptionStatusClient(WebClient.Builder builder,
            @Value("${buildshield.services.subscription}") String subscriptionUri, ReadOnlyAccountProperties properties) {
        this(builder.baseUrl(subscriptionUri).build(), properties, Clock.systemUTC());
    }

    SubscriptionStatusClient(WebClient http, ReadOnlyAccountProperties properties, Clock clock) {
        this.http = http;
        this.properties = properties;
        this.clock = clock;
    }

    /** ¿La cuenta de la organización está en solo lectura? Usa el token del usuario para consultar. */
    public Mono<Boolean> readOnly(UUID organizationId, String authorization) {
        Instant now = clock.instant();
        Cached cached = cache.get(organizationId);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return Mono.just(cached.readOnly());
        }
        return http.get().uri("/api/v1/subscriptions/current")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(properties.timeout())
                .map(body -> {
                    boolean readOnly = body.path("readOnly").asBoolean(false);
                    cache.put(organizationId, new Cached(readOnly, clock.instant().plus(properties.cacheTtl())));
                    return readOnly;
                })
                .onErrorResume(ex -> {
                    log.warn("No se pudo leer la suscripción de la organización {}: {}. Se deja pasar.",
                            organizationId, ex.getMessage());
                    return Mono.just(false);
                });
    }

    /** Olvida el estado recordado (por ejemplo, al pagar o consultar la suscripción). */
    public void forget(UUID organizationId) {
        cache.remove(organizationId);
    }

    private record Cached(boolean readOnly, Instant expiresAt) {
    }
}
