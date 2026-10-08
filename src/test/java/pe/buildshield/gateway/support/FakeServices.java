package pe.buildshield.gateway.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Un backend HTTP simulado por microservicio (servidor del JDK en un puerto libre). Cada uno responde
 * un JSON con su nombre y lo que recibió, y además devuelve sus propias cabeceras CORS y de correlación
 * para comprobar que el gateway no las duplica.
 */
public final class FakeServices {

    public static final List<String> NAMES = List.of("iam", "organization", "inventory", "ordering",
            "discrepancy", "subscription", "notification", "audit");

    private static final Map<String, HttpServer> SERVERS = new LinkedHashMap<>();

    private FakeServices() {
    }

    public static synchronized void start() {
        if (!SERVERS.isEmpty()) {
            return;
        }
        for (String name : NAMES) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", exchange -> respond(name, exchange));
                server.start();
                SERVERS.put(name, server);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    public static String uri(String name) {
        return "http://127.0.0.1:" + SERVERS.get(name).getAddress().getPort();
    }

    private static void respond(String name, HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        String correlation = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        String body = "{\"service\":\"" + name + "\""
                + ",\"method\":\"" + exchange.getRequestMethod() + "\""
                + ",\"path\":\"" + exchange.getRequestURI().getPath() + "\""
                + ",\"authorization\":\"" + (auth == null ? "" : auth) + "\""
                + ",\"correlationId\":\"" + (correlation == null ? "" : correlation) + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        // Como haría un servicio con su propio CORS o su filtro de correlación.
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "http://localhost:4200");
        exchange.getResponseHeaders().add("Vary", "Origin");
        exchange.getResponseHeaders().add("X-Correlation-Id", correlation == null ? "from-service" : correlation);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
