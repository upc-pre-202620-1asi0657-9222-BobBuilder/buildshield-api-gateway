package pe.buildshield.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.buildshield.gateway.support.GatewayTestBase;
import pe.buildshield.gateway.support.TestTokens;

import java.util.stream.Stream;

/** El gateway corta lo no autenticado (401) y deja pasar las rutas públicas sin token. */
class SecurityTest extends GatewayTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users", "/api/v1/worksites", "/api/v1/stock", "/api/v1/orders",
            "/api/v1/dispatches/1/custody", "/api/v1/discrepancies", "/api/v1/plans", "/api/v1/notifications",
            "/api/v1/audit/events", "/api/v1/auth/sign-out"})
    void protected_routes_reject_requests_without_token(String path) {
        client().get().uri(path)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.details").isArray();
    }

    static Stream<String> invalidTokens() {
        return Stream.of(TestTokens.expired(), TestTokens.signedByAnotherKey(), TestTokens.withIssuer("buildshield-core"),
                TestTokens.withoutOrganization(), TestTokens.tooLong(), TestTokens.issuedInTheFuture(),
                TestTokens.withSubject("not-a-uuid"), "not-a-jwt");
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void protected_routes_reject_invalid_tokens(String token) {
        client().get().uri("/api/v1/worksites")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/sign-up", "/api/v1/auth/sign-in", "/api/v1/auth/refresh",
            "/api/v1/auth/password-reset", "/api/v1/auth/password-reset/confirm"})
    void public_auth_routes_pass_without_token(String path) {
        client().post().uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("iam")
                .jsonPath("$.path").isEqualTo(path);
    }

    @Test
    void an_expired_token_does_not_block_a_public_route() {
        client().post().uri("/api/v1/auth/refresh")
                .headers(h -> h.setBearerAuth(TestTokens.expired()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("iam");
    }

    @Test
    void evidence_content_passes_without_token_and_other_evidence_routes_do_not() {
        String path = "/api/v1/discrepancies/7/evidences/9/content";
        client().get().uri(path + "?token=firma")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("discrepancy")
                .jsonPath("$.path").isEqualTo(path);
        client().get().uri("/api/v1/discrepancies/7/evidences").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void health_is_public() {
        client().get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void gateway_routes_endpoint_requires_a_token_outside_local_profile() {
        client().get().uri("/actuator/gateway/routes").exchange().expectStatus().isUnauthorized();
        client().get().uri("/actuator/gateway/routes")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.route_id == 'audit-dispatch-custody')]").exists()
                .jsonPath("$[?(@.route_id == 'ordering')]").exists();
    }
}
