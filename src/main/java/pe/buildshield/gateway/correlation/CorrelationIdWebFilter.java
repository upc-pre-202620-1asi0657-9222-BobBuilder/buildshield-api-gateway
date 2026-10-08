package pe.buildshield.gateway.correlation;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Propaga {@value CorrelationId#HEADER}: si la petición lo trae (y es válido) se reenvía tal cual; si
 * no, el gateway genera uno. Va antes que la seguridad, de modo que también las respuestas 401 llevan
 * el identificador. El servicio de destino lo recibe en la petición y lo pone en su MDC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = CorrelationId.resolve(exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(CorrelationId.HEADER, correlationId))
                .build();
        exchange.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);
        return chain.filter(exchange.mutate().request(request).build());
    }
}
