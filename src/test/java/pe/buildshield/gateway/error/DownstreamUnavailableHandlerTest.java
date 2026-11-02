package pe.buildshield.gateway.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamUnavailableHandlerTest {

    private final DownstreamUnavailableHandler handler = new DownstreamUnavailableHandler();

    static Stream<Throwable> connectionFailures() {
        return Stream.of(new ConnectException("Connection refused"), new UnknownHostException("ordering-service"),
                new SocketException("Connection reset"), new IOException("Connection prematurely closed"),
                new IllegalStateException("envuelta", new ConnectException("Connection refused")));
    }

    @ParameterizedTest
    @MethodSource("connectionFailures")
    void a_connection_failure_is_a_503(Throwable failure) {
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(handler.handle(exchange, failure)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> assertThat(body).contains("\"code\":\"SERVICE_UNAVAILABLE\"")
                        .contains("\"details\":[]"))
                .verifyComplete();
    }

    @Test
    void any_other_failure_goes_to_the_default_handler() {
        IllegalStateException bug = new IllegalStateException("bug");
        StepVerifier.create(handler.handle(exchange(), bug)).expectErrorMatches(bug::equals).verify();

        TimeoutException slow = new TimeoutException("response timeout");
        StepVerifier.create(handler.handle(exchange(), slow)).expectErrorMatches(slow::equals).verify();
    }

    @Test
    void a_response_already_committed_is_not_rewritten() {
        MockServerWebExchange exchange = exchange();
        exchange.getResponse().setComplete().block();
        ConnectException failure = new ConnectException("Connection refused");

        StepVerifier.create(handler.handle(exchange, failure)).expectErrorMatches(failure::equals).verify();
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
    }
}
