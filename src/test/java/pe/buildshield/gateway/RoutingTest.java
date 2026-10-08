package pe.buildshield.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.buildshield.gateway.support.GatewayTestBase;
import pe.buildshield.gateway.support.TestTokens;

/** Cada prefijo de la tabla de rutas (plan, sección 4) llega a su microservicio con la ruta intacta. */
class RoutingTest extends GatewayTestBase {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "/api/v1/users, iam",
            "/api/v1/users/9b0c/role, iam",
            "/api/v1/auth/sign-out, iam",
            "/api/v1/organizations/current, organization",
            "/api/v1/worksites, organization",
            "/api/v1/worksites/1, organization",
            "/api/v1/warehouses, organization",
            "/api/v1/materials/7, organization",
            "/api/v1/assignments, organization",
            "/api/v1/stock, inventory",
            "/api/v1/stock/entries, inventory",
            "/api/v1/orders, ordering",
            "/api/v1/orders/5/approve, ordering",
            "/api/v1/dispatches, ordering",
            "/api/v1/dispatches/42, ordering",
            "/api/v1/dispatches/42/depart, ordering",
            "/api/v1/dispatches/by-manifest/MAN-1, ordering",
            "/api/v1/receptions/3/confirm, ordering",
            "/api/v1/discrepancies/8, discrepancy",
            "/api/v1/subscriptions/current, subscription",
            "/api/v1/plans, subscription",
            "/api/v1/notifications, notification",
            "/api/v1/audit/events, audit",
            "/api/v1/dispatches/42/custody, audit",
    })
    void routes_each_prefix_to_its_service(String path, String service) {
        client().get().uri(path)
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo(service)
                .jsonPath("$.path").isEqualTo(path);
    }

    @Test
    @DisplayName("la custodia de un despacho va a auditoría antes que la regla general de dispatches")
    void custody_takes_precedence_over_dispatches() {
        client().get().uri("/api/v1/dispatches/0f8e1c2a-0000-4000-8000-000000000001/custody")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("audit");
        // Una ruta más profunda no es la de custodia: sigue en ordering.
        client().get().uri("/api/v1/dispatches/42/custody/extra")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("ordering");
    }

    @Test
    void forwards_the_authorization_header_and_the_method() {
        String token = TestTokens.valid();
        client().post().uri("/api/v1/orders")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.method").isEqualTo("POST")
                .jsonPath("$.authorization").isEqualTo("Bearer " + token);
    }

    @Test
    void unknown_route_is_not_found_for_an_authenticated_user() {
        client().get().uri("/api/v1/unknown")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isNotFound();
    }
}
