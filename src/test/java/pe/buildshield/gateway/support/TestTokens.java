package pe.buildshield.gateway.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

/** Claves RSA de prueba y tokens firmados con la misma forma que emite IAM. */
public final class TestTokens {

    public static final String ISSUER = "buildshield-iam";

    private static final KeyPair KEYS = generate();
    private static final KeyPair OTHER_KEYS = generate();

    private TestTokens() {
    }

    public static String publicKeyPem() {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(KEYS.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    /** Token válido de un administrador. */
    public static String valid() {
        return sign(KEYS.getPrivate(), claims -> { });
    }

    public static String expired() {
        Instant issued = Instant.now().minus(Duration.ofHours(1));
        return sign(KEYS.getPrivate(), claims -> claims
                .issueTime(Date.from(issued))
                .expirationTime(Date.from(issued.plus(Duration.ofMinutes(15)))));
    }

    public static String signedByAnotherKey() {
        return sign(OTHER_KEYS.getPrivate(), claims -> { });
    }

    public static String withIssuer(String issuer) {
        return sign(KEYS.getPrivate(), claims -> claims.issuer(issuer));
    }

    public static String withoutOrganization() {
        return sign(KEYS.getPrivate(), claims -> claims.claim("org", null));
    }

    public static String tooLong() {
        Instant now = Instant.now();
        return sign(KEYS.getPrivate(), claims -> claims.expirationTime(Date.from(now.plus(Duration.ofHours(2)))));
    }

    public static String issuedInTheFuture() {
        Instant future = Instant.now().plus(Duration.ofMinutes(5));
        return sign(KEYS.getPrivate(), claims -> claims
                .issueTime(Date.from(future))
                .notBeforeTime(null)
                .expirationTime(Date.from(future.plus(Duration.ofMinutes(10)))));
    }

    public static String withSubject(String subject) {
        return sign(KEYS.getPrivate(), claims -> claims.subject(subject));
    }

    private static String sign(PrivateKey key, Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .jwtID(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(15))))
                .claim("org", UUID.randomUUID().toString())
                .claim("role", "ADMINISTRATOR");
        customizer.accept(claims);
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
