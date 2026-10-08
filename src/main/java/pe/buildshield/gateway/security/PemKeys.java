package pe.buildshield.gateway.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Lee la clave pública RSA en formato PEM (X.509), como llega por variable de entorno. Igual que en el Core. */
public final class PemKeys {

    private PemKeys() {
    }

    public static RSAPublicKey publicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("Falta la clave pública RSA (BUILDSHIELD_JWT_PUBLIC_KEY)");
        }
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decode(pem)));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Clave pública RSA inválida", ex);
        }
    }

    private static byte[] decode(String pem) {
        String base64 = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
