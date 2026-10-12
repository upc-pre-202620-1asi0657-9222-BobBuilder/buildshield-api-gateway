package pe.buildshield.gateway.subscription;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Configuración por defecto, filtro desactivado y vencimiento de la caché del estado de la suscripción. */
class ReadOnlyAccountUnitTest {

    @Test
    void defaults_exempt_payment_and_session_routes() {
        ReadOnlyAccountProperties defaults = new ReadOnlyAccountProperties(null, null, null, null);

        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.cacheTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(defaults.timeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(defaults.exemptPaths()).containsExactly("/api/v1/subscriptions/**", "/api/v1/plans/**",
                "/api/v1/auth/**");
        assertThat(new ReadOnlyAccountProperties(false, Duration.ofSeconds(5), Duration.ofSeconds(1), List.of("/x"))
                .exemptPaths()).containsExactly("/x");
    }

    @Test
    void a_disabled_filter_lets_everything_through_and_runs_before_routing() {
        ReadOnlyAccountProperties disabled = new ReadOnlyAccountProperties(false, null, null, null);
        ReadOnlyAccountFilter filter = new ReadOnlyAccountFilter(null, disabled);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/worksites"));
        AtomicInteger passed = new AtomicInteger();
        GatewayFilterChain chain = ignored -> {
            passed.incrementAndGet();
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(passed).hasValue(1);
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(filter.getOrder()).isLessThan(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void the_cached_status_expires_and_is_read_again() {
        AtomicInteger calls = new AtomicInteger();
        WebClient http = WebClient.builder().exchangeFunction(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json").body("{\"readOnly\":true}").build());
        }).build();
        MovingClock clock = new MovingClock();
        SubscriptionStatusClient client = new SubscriptionStatusClient(http,
                new ReadOnlyAccountProperties(true, Duration.ofSeconds(30), Duration.ofSeconds(1), null), clock);
        UUID organization = UUID.randomUUID();

        assertThat(client.readOnly(organization, "Bearer x").block()).isTrue();
        clock.now = clock.now.plusSeconds(29);
        assertThat(client.readOnly(organization, "Bearer x").block()).isTrue();
        assertThat(calls).hasValue(1);
        clock.now = clock.now.plusSeconds(2);
        assertThat(client.readOnly(organization, "Bearer x").block()).isTrue();
        assertThat(calls).hasValue(2);
    }

    private static final class MovingClock extends Clock {

        Instant now = Instant.parse("2026-10-11T18:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
