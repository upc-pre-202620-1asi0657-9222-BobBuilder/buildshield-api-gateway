package pe.buildshield.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import pe.buildshield.gateway.support.FakeServices;
import pe.buildshield.gateway.support.TestTokens;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Gateway real con ordering e iam apagados (puerto cerrado): la conexión rechazada se responde 503, no 500. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DownstreamUnavailableTest {

    private static final String WEB = "http://localhost:4200";
    private static final Set<String> DOWN = Set.of("ordering", "iam");

    @Autowired
    private WebTestClient client;

    @Autowired
    private HttpClientProperties httpClient;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        FakeServices.start();
        for (String name : FakeServices.NAMES) {
            String uri = DOWN.contains(name) ? "http://127.0.0.1:" + closedPort() : FakeServices.uri(name);
            registry.add("buildshield.services." + name, () -> uri);
        }
        registry.add("buildshield.security.jwt-public-key", TestTokens::publicKeyPem);
    }

    @AfterEach
    void reset() {
        FakeServices.READ_ONLY_ORGANIZATIONS.clear();
    }

    @Test
    void a_service_that_is_down_answers_503_in_the_common_error_format() {
        client().get().uri("/api/v1/orders")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.message").value(message -> assertThat((String) message).contains("no está disponible"))
                .jsonPath("$.details").isArray();
    }

    @Test
    void the_503_keeps_the_correlation_id_and_the_cors_headers() {
        client().post().uri("/api/v1/orders")
                .header(HttpHeaders.ORIGIN, WEB)
                .header("X-Correlation-Id", "trace-503")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("X-Correlation-Id", "trace-503")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB);
    }

    @Test
    void a_public_route_of_a_service_that_is_down_answers_503_too() {
        client().post().uri("/api/v1/auth/sign-in")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void the_read_only_block_still_wins_over_a_service_that_is_down() {
        UUID expired = UUID.randomUUID();
        FakeServices.READ_ONLY_ORGANIZATIONS.add(expired.toString());

        client().post().uri("/api/v1/orders")
                .headers(h -> h.setBearerAuth(TestTokens.forOrganization(expired, "SITE_MANAGER")))
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectBody().jsonPath("$.code").isEqualTo("SUBSCRIPTION_READ_ONLY");
    }

    @Test
    void the_other_services_keep_working() {
        client().get().uri("/api/v1/stock/counts")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("inventory");
    }

    @Test
    void a_token_is_still_required_before_reaching_a_service_that_is_down() {
        client().get().uri("/api/v1/orders").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void the_connect_timeout_is_short() {
        assertThat(httpClient.getConnectTimeout()).isEqualTo(3000);
    }

    private WebTestClient client() {
        return client.mutate().responseTimeout(Duration.ofSeconds(10)).build();
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
