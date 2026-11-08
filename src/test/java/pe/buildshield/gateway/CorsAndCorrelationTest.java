package pe.buildshield.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import pe.buildshield.gateway.support.GatewayTestBase;
import pe.buildshield.gateway.support.TestTokens;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** CORS para la web (una sola vez, aunque el servicio también mande cabeceras) y X-Correlation-Id. */
class CorsAndCorrelationTest extends GatewayTestBase {

    private static final String WEB = "http://localhost:4200";

    @Test
    void preflight_from_the_web_is_accepted_without_token() {
        client().options().uri("/api/v1/worksites")
                .header(HttpHeaders.ORIGIN, WEB)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
    }

    @Test
    void preflight_from_an_unknown_origin_is_rejected() {
        client().options().uri("/api/v1/worksites")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void routed_response_has_a_single_allow_origin_even_if_the_service_sends_its_own() {
        EntityExchangeResult<byte[]> result = client().method(HttpMethod.GET).uri("/api/v1/worksites")
                .header(HttpHeaders.ORIGIN, WEB)
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult();
        HttpHeaders headers = result.getResponseHeaders();
        assertThat(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).containsExactly(WEB);
        assertThat(headers.get("X-Correlation-Id")).hasSize(1);
    }

    @Test
    void unauthenticated_response_from_the_web_still_carries_cors_headers() {
        client().get().uri("/api/v1/worksites")
                .header(HttpHeaders.ORIGIN, WEB)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB);
    }

    @Test
    void generates_a_correlation_id_when_absent_and_forwards_it() {
        EntityExchangeResult<byte[]> result = client().post().uri("/api/v1/auth/sign-in")
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult();
        List<String> ids = result.getResponseHeaders().get("X-Correlation-Id");
        assertThat(ids).hasSize(1);
        String id = ids.getFirst();
        assertThat(id).matches("[0-9a-f-]{36}");
        assertThat(new String(result.getResponseBodyContent())).contains("\"correlationId\":\"" + id + "\"");
    }

    @Test
    void propagates_a_valid_correlation_id() {
        client().get().uri("/api/v1/orders")
                .header("X-Correlation-Id", "web-1234.abc")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "web-1234.abc")
                .expectBody().jsonPath("$.correlationId").isEqualTo("web-1234.abc");
    }

    @Test
    void replaces_an_invalid_correlation_id() {
        client().get().uri("/api/v1/orders")
                .header("X-Correlation-Id", "bad id with spaces")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Correlation-Id", id -> assertThat(id).matches("[0-9a-f-]{36}"));
    }

    @Test
    void unauthenticated_response_carries_the_correlation_id() {
        client().get().uri("/api/v1/orders")
                .header("X-Correlation-Id", "trace-401")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Correlation-Id", "trace-401");
    }
}
