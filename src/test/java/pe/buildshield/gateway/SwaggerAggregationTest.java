package pe.buildshield.gateway;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import pe.buildshield.gateway.support.GatewayTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/** Swagger agregado: /swagger-ui.html lista la especificación de cada servicio, servida a través del gateway. */
class SwaggerAggregationTest extends GatewayTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"iam", "organization", "inventory", "ordering", "discrepancy", "subscription",
            "notification", "audit"})
    void each_service_spec_is_proxied_without_token(String service) {
        client().get().uri("/v3/api-docs/" + service)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo(service)
                .jsonPath("$.path").isEqualTo("/api/v1/api-docs");
    }

    @Test
    void swagger_config_lists_the_eight_services() {
        client().get().uri("/gateway/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls.length()").isEqualTo(8)
                .jsonPath("$.urls[?(@.name == 'iam')].url").isEqualTo("/v3/api-docs/iam")
                .jsonPath("$.urls[?(@.name == 'audit')].url").isEqualTo("/v3/api-docs/audit");
    }

    @Test
    void swagger_ui_is_public() {
        client().get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isIn(200, 302));
    }
}
