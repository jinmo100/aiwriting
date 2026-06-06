package com.jinmo.aiwriting.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyEncryptionServiceTest {

    @Test
    void encryptsAndDecryptsApiKeyWithoutReturningPlaintextCipher() {
        ApiKeyEncryptionService service = new ApiKeyEncryptionService("dev-only-test-secret-at-least-32-bytes");

        String encrypted = service.encrypt("sk-test-secret");

        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).doesNotContain("sk-test-secret");
        assertThat(service.decrypt(encrypted)).isEqualTo("sk-test-secret");
    }

    @Test
    void rejectsInvalidCiphertext() {
        ApiKeyEncryptionService service = new ApiKeyEncryptionService("dev-only-test-secret-at-least-32-bytes");

        assertThatThrownBy(() -> service.decrypt("not-a-valid-cipher"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("无法解密 API Key");
    }
}
