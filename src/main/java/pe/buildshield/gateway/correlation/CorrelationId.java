package pe.buildshield.gateway.correlation;

import java.util.UUID;
import java.util.regex.Pattern;

/** Identificador de correlación de la petición: mismo header y mismo formato válido que el Core. */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    /** El recibido si es válido; si falta o no es válido, uno nuevo. */
    public static String resolve(String received) {
        return isValid(received) ? received : UUID.randomUUID().toString();
    }
}
