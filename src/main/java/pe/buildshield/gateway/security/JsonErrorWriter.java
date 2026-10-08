package pe.buildshield.gateway.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Escribe los errores del gateway con el formato único de BuildShield
 * ({@code {"code","message","details":[]}}), el mismo del Core y de los servicios.
 */
final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set("WWW-Authenticate", "Bearer");
        }
        String body = "{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"details\":[]}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
