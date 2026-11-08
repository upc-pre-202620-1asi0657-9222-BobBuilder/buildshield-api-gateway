package pe.buildshield.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import pe.buildshield.gateway.security.JsonErrorWriter;
import reactor.core.publisher.Mono;

import java.io.IOException;

/**
 * Un servicio caído no es un error interno del gateway: conexión rechazada, host desconocido o conexión cortada a
 * media respuesta se responden 503 {@code SERVICE_UNAVAILABLE}, en el formato único de error, en vez del 500 por
 * defecto. Las cabeceras ya puestas ({@code X-Correlation-Id} y CORS) se conservan. Cualquier otra excepción sigue
 * al manejador por defecto de Spring Boot, que tiene orden -1.
 */
@Component
@Order(-2)
public class DownstreamUnavailableHandler implements WebExceptionHandler {

    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    static final String MESSAGE = "El servicio no está disponible en este momento. Vuelve a intentarlo en unos segundos.";

    private static final Logger log = LoggerFactory.getLogger(DownstreamUnavailableHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted() || !isConnectionFailure(ex)) {
            return Mono.error(ex);
        }
        log.warn("Servicio no disponible para {} {}: {}", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(), ex.getMessage());
        return JsonErrorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE, MESSAGE);
    }

    /** El cliente HTTP de Netty entrega estas fallas como {@link IOException}, a veces envueltas. */
    private static boolean isConnectionFailure(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
