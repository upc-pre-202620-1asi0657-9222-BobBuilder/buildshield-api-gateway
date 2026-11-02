package pe.buildshield.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

/** Con subscription apagado, sus rutas responden 503 y una caída de pagos no congela la operación (US10). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SubscriptionServiceDownTest {

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        FakeServices.start();
        for (String name : FakeServices.NAMES) {
            String uri = "subscription".equals(name) ? "http://127.0.0.1:" + closedPort() : FakeServices.uri(name);
            registry.add("buildshield.services." + name, () -> uri);
        }
        registry.add("buildshield.security.jwt-public-key", TestTokens::publicKeyPem);
    }

    @Test
    void its_own_routes_answer_503() {
        client().get().uri("/api/v1/subscriptions/current")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void writes_to_the_other_services_are_not_blocked() {
        client().post().uri("/api/v1/discrepancies")
                .headers(h -> h.setBearerAuth(TestTokens.valid()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.service").isEqualTo("discrepancy");
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
