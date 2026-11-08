package pe.buildshield.gateway.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/** Gateway real en un puerto libre, apuntando a los ocho backends simulados. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class GatewayTestBase {

    @Autowired
    protected WebTestClient client;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        FakeServices.start();
        for (String name : FakeServices.NAMES) {
            registry.add("buildshield.services." + name, () -> FakeServices.uri(name));
        }
        registry.add("buildshield.security.jwt-public-key", TestTokens::publicKeyPem);
    }

    protected WebTestClient client() {
        return client.mutate().responseTimeout(Duration.ofSeconds(10)).build();
    }
}
