package pe.buildshield.gateway.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un backend HTTP simulado por microservicio (servidor del JDK en un puerto libre). Cada uno responde
 * un JSON con su nombre y lo que recibió, y además devuelve sus propias cabeceras CORS y de correlación
 * para comprobar que el gateway no las duplica.
 */
public final class FakeServices {

    public static final List<String> NAMES = List.of("iam", "organization", "inventory", "ordering",
            "discrepancy", "subscription", "notification", "audit");

    private static final Map<String, HttpServer> SERVERS = new LinkedHashMap<>();

    /** Organizaciones con la suscripción vencida, según el subscription-service simulado. */
    public static final Set<String> READ_ONLY_ORGANIZATIONS = ConcurrentHashMap.newKeySet();

    /** Consultas recibidas por el subscription-service simulado en {@code /api/v1/subscriptions/current}. */
    public static final AtomicInteger SUBSCRIPTION_LOOKUPS = new AtomicInteger();

    /** Si es true, el subscription-service simulado responde 503 a la consulta de la suscripción. */
    public static volatile boolean subscriptionDown;

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

    /** Como subscription-service: {@code readOnly} según la organización del token. */
    private static void currentSubscription(HttpExchange exchange, String auth) throws IOException {
        SUBSCRIPTION_LOOKUPS.incrementAndGet();
        if (subscriptionDown) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        String organization = organizationOf(auth);
        byte[] bytes = ("{\"service\":\"subscription\",\"path\":\"/api/v1/subscriptions/current\","
                + "\"organizationId\":\"" + organization + "\",\"readOnly\":"
                + READ_ONLY_ORGANIZATIONS.contains(organization) + "}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String organizationOf(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return "";
        }
        String[] parts = auth.substring(7).split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"org\"\\s*:\\s*\"([^\"]+)\"").matcher(claims);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static void respond(String name, HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (name.equals("subscription") && exchange.getRequestURI().getPath().equals("/api/v1/subscriptions/current")
                && exchange.getRequestMethod().equals("GET")) {
            currentSubscription(exchange, auth);
            return;
        }
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
