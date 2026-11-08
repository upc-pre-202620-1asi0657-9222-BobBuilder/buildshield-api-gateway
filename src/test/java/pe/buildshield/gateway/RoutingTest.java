package pe.buildshield.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import pe.buildshield.gateway.support.GatewayTestBase;
import pe.buildshield.gateway.support.TestTokens;

import static org.assertj.core.api.Assertions.assertThat;

/** Cada prefijo de la tabla de rutas (plan, sección 4) llega a su microservicio con la ruta intacta. */
class RoutingTest extends GatewayTestBase {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "/api/v1/users, iam",
            "/api/v1/users/9b0c/role, iam",
            "/api/v1/users/me, iam",
            "/api/v1/auth/sign-out, iam",
            "/api/v1/organizations/current, organization",
            "/api/v1/organizations/me, organization",
            "/api/v1/worksites, organization",
            "/api/v1/worksites/1, organization",
            "/api/v1/warehouses, organization",
            "/api/v1/materials/7, organization",
            "/api/v1/assignments, organization",
            "/api/v1/stock, inventory",
            "/api/v1/stock/entries, inventory",
            "/api/v1/stock/worksites, inventory",
            "/api/v1/stock/worksites/1/balance, inventory",
            "/api/v1/stock/reservations, inventory",
            "/api/v1/stock/movements, inventory",
            "/api/v1/stock/movements/history, inventory",
            "/api/v1/stock/counts, inventory",
            "/api/v1/stock/counts/1, inventory",
            "/api/v1/stock/counts/1/lines, inventory",
            "/api/v1/stock/counts/1/finish, inventory",
            "/api/v1/stock/counts/1/review, inventory",
            "/api/v1/stock/counts/1/adjustment, inventory",
            "/api/v1/stock/counts/1/cancel, inventory",
            "/api/v1/stock/returns, inventory",
            "/api/v1/stock/returns/1, inventory",
            "/api/v1/stock/returns/1/request, inventory",
            "/api/v1/stock/returns/1/approve, inventory",
            "/api/v1/stock/returns/1/reject, inventory",
            "/api/v1/stock/returns/1/dispatch, inventory",
            "/api/v1/stock/returns/1/receive, inventory",
            "/api/v1/stock/returns/1/cancel, inventory",
            "/api/v1/orders, ordering",
            "/api/v1/orders/5/approve, ordering",
            "/api/v1/orders/5/reject, ordering",
            "/api/v1/dispatches, ordering",
            "/api/v1/dispatches/42, ordering",
            "/api/v1/dispatches/42/depart, ordering",
            "/api/v1/dispatches/by-manifest/MAN-1, ordering",
            "/api/v1/dispatches/42/carrier, ordering",
            "/api/v1/dispatches/42/departure-weighing, ordering",
            "/api/v1/dispatches/42/incidents, ordering",
            "/api/v1/dispatches/42/manifest, ordering",
            "/api/v1/receptions, ordering",
            "/api/v1/receptions/3/lines/7, ordering",
            "/api/v1/receptions/3/comparison, ordering",
            "/api/v1/receptions/3/partial, ordering",
            "/api/v1/receptions/3/arrival-weighing, ordering",
            "/api/v1/receptions/3/confirm, ordering",
            "/api/v1/discrepancies, discrepancy",
            "/api/v1/discrepancies/8, discrepancy",
            "/api/v1/discrepancies/8/evidences, discrepancy",
            "/api/v1/discrepancies/8/response, discrepancy",
            "/api/v1/discrepancies/8/accept, discrepancy",
            "/api/v1/discrepancies/8/escalate, discrepancy",
            "/api/v1/discrepancies/8/resolve, discrepancy",
            "/api/v1/subscriptions/current, subscription",
            "/api/v1/subscriptions/checkout, subscription",
            "/api/v1/subscriptions/checkout/cs_1/simulated-payment, subscription",
            "/api/v1/subscriptions/current/billing-period, subscription",
            "/api/v1/plans, subscription",
            "/api/v1/notifications, notification",
            "/api/v1/notifications/unread-count, notification",
            "/api/v1/notifications/read-all, notification",
            "/api/v1/notifications/recipient-settings, notification",
            "/api/v1/notifications/recipient-settings/worksites/1/types/DISPATCH_IN_TRANSIT, notification",
            "/api/v1/audit/events, audit",
            "/api/v1/audit/verify, audit",
            "/api/v1/audit/reports/reconciliation, audit",
            "/api/v1/audit/reports/reconciliation/export, audit",
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

    @ParameterizedTest(name = "POST {0} -> {1}")
    @CsvSource({
            "/api/v1/discrepancies, discrepancy",
            "/api/v1/stock/counts, inventory",
            "/api/v1/stock/counts/1/review, inventory",
            "/api/v1/stock/returns, inventory",
            "/api/v1/stock/returns/1/receive, inventory",
            "/api/v1/subscriptions/checkout, subscription",
    })
    void write_routes_of_the_current_controllers_reach_their_service(String path, String service) {
        client().post().uri(path)
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo(service)
                .jsonPath("$.method").isEqualTo("POST")
                .jsonPath("$.path").isEqualTo(path);
    }

    @Test
    void a_multipart_evidence_upload_reaches_discrepancy_intact() {
        byte[] photo = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("file", new ByteArrayResource(photo) {
            @Override
            public String getFilename() {
                return "foto.jpg";
            }
        }, MediaType.IMAGE_JPEG);
        client().post().uri("/api/v1/discrepancies/8/evidences")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("discrepancy")
                .jsonPath("$.path").isEqualTo("/api/v1/discrepancies/8/evidences")
                .jsonPath("$.contentType").value(type -> assertThat((String) type)
                        .startsWith("multipart/form-data;boundary="))
                .jsonPath("$.bodyContainsFile").isEqualTo(true);
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
