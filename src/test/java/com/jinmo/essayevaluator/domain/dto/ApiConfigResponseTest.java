package com.jinmo.essayevaluator.domain.dto;

import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiConfigResponseTest {

    @Test
    void buildsPreviewFromResolvedPlainApiKeyWithoutReturningFullKey() {
        ApiConfig config = new ApiConfig();
        config.setId(1L);
        config.setConfigName("test");
        config.setApiKeyEncrypted("encrypted");

        ApiConfigResponse response = ApiConfigResponse.fromEntity(config, "sk-test-secret-abcd");

        assertThat(response.hasApiKey()).isTrue();
        assertThat(response.apiKeyPreview()).isEqualTo("sk-...abcd");
        assertThat(response.toString()).doesNotContain("secret");
    }
}
