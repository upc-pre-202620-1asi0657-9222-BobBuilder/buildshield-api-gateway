package pe.buildshield.gateway.security;

import org.junit.jupiter.api.Test;
import pe.buildshield.gateway.support.TestTokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PemKeysTest {

    @Test
    void reads_a_pem_public_key() {
        assertThat(PemKeys.publicKey(TestTokens.publicKeyPem()).getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void rejects_a_missing_key_with_a_clear_message() {
        assertThatThrownBy(() -> PemKeys.publicKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUILDSHIELD_JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> PemKeys.publicKey(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_garbage() {
        assertThatThrownBy(() -> PemKeys.publicKey("-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Clave pública RSA inválida");
    }
}
