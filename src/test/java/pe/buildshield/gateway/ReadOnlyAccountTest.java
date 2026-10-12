package pe.buildshield.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import pe.buildshield.gateway.support.FakeServices;
import pe.buildshield.gateway.support.GatewayTestBase;
import pe.buildshield.gateway.support.TestTokens;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US10: con la suscripción vencida, el gateway corta las operaciones que cambian estado con 402 y deja pasar las
 * lecturas y lo necesario para pagar (suscripción, checkout, planes, auth). El estado se consulta a
 * subscription-service con caché corta por organización.
 */
class ReadOnlyAccountTest extends GatewayTestBase {

    private final UUID expired = UUID.randomUUID();
    private final UUID active = UUID.randomUUID();

    @AfterEach
    void reset() {
        FakeServices.READ_ONLY_ORGANIZATIONS.clear();
        FakeServices.subscriptionDown = false;
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void writes_of_a_read_only_organization_are_rejected_with_402(String method) {
        FakeServices.READ_ONLY_ORGANIZATIONS.add(expired.toString());

        client().method(HttpMethod.valueOf(method)).uri("/api/v1/worksites")
                .headers(h -> h.setBearerAuth(TestTokens.forOrganization(expired, "ADMINISTRATOR")))
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.code").isEqualTo("SUBSCRIPTION_READ_ONLY")
                .jsonPath("$.message").value(message -> assertThat((String) message).contains("solo lectura"))
                .jsonPath("$.details").isArray();
    }

    @Test
    void reads_and_payment_routes_pass_for_a_read_only_organization() {
        FakeServices.READ_ONLY_ORGANIZATIONS.add(expired.toString());
        String token = TestTokens.forOrganization(expired, "ADMINISTRATOR");

        client().get().uri("/api/v1/orders").headers(h -> h.setBearerAuth(token))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("ordering");
        for (String path : new String[]{"/api/v1/subscriptions/checkout",
                "/api/v1/subscriptions/checkout/cs_sim_1/simulated-payment", "/api/v1/auth/sign-out",
                "/api/v1/auth/refresh"}) {
            client().post().uri(path).headers(h -> h.setBearerAuth(token))
                    .exchange().expectStatus().isOk().expectBody().jsonPath("$.path").isEqualTo(path);
        }
        client().put().uri("/api/v1/subscriptions/current/billing-period").headers(h -> h.setBearerAuth(token))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("subscription");
    }

    @Test
    void writes_of_an_active_organization_pass_and_the_status_is_cached() {
        String token = TestTokens.forOrganization(active, "SITE_MANAGER");
        int before = FakeServices.SUBSCRIPTION_LOOKUPS.get();

        for (int i = 0; i < 3; i++) {
            client().post().uri("/api/v1/orders").headers(h -> h.setBearerAuth(token))
                    .exchange().expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("ordering");
        }

        assertThat(FakeServices.SUBSCRIPTION_LOOKUPS.get() - before).isEqualTo(1);
    }

    @Test
    void consulting_the_subscription_forgets_the_cached_status_so_a_payment_takes_effect_at_once() {
        String token = TestTokens.forOrganization(expired, "ADMINISTRATOR");
        FakeServices.READ_ONLY_ORGANIZATIONS.add(expired.toString());
        client().post().uri("/api/v1/worksites").headers(h -> h.setBearerAuth(token))
                .exchange().expectStatus().isEqualTo(402);

        FakeServices.READ_ONLY_ORGANIZATIONS.remove(expired.toString());
        client().post().uri("/api/v1/worksites").headers(h -> h.setBearerAuth(token))
                .exchange().expectStatus().isEqualTo(402);
        client().get().uri("/api/v1/subscriptions/current").headers(h -> h.setBearerAuth(token))
                .exchange().expectStatus().isOk();
        client().post().uri("/api/v1/worksites").headers(h -> h.setBearerAuth(token))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("organization");
    }

    @Test
    void if_subscription_service_is_down_writes_are_not_blocked() {
        FakeServices.subscriptionDown = true;
        UUID unknown = UUID.randomUUID();

        client().post().uri("/api/v1/stock/entries")
                .headers(h -> h.setBearerAuth(TestTokens.forOrganization(unknown, "WAREHOUSE_MANAGER")))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("inventory");
    }

    @Test
    void the_public_webhook_is_never_blocked() {
        client().post().uri("/api/v1/subscriptions/webhooks/stripe")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("subscription");
    }
}
