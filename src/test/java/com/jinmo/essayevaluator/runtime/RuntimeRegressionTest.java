package com.jinmo.essayevaluator.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeRegressionTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void apiConfigMigrationKeepsLegacyPlainApiKeyNullable() throws IOException {
        String migration = read("src/main/resources/db/migration/V10__api_config_runtime_fixes.sql");

        assertThat(migration).contains("ALTER TABLE api_configs ALTER COLUMN api_key DROP NOT NULL");
    }

    @Test
    void providerInvalidationUsesSpringRedisListenerContainerInsteadOfManualSubscribeLoop() throws IOException {
        String service = read("src/main/java/com/jinmo/essayevaluator/ai/provider/ProviderConfigInvalidationService.java");

        assertThat(service).contains("RedisMessageListenerContainer");
        assertThat(service).contains("ChannelTopic");
        assertThat(service).doesNotContain("getConnection()");
        assertThat(service).doesNotContain("connection.subscribe");
        assertThat(service).doesNotContain("Provider 配置缓存失效订阅已结束，将稍后重试");
    }

    @Test
    void configViewDoesNotDuplicateSameModelIdAndDisplayName() throws IOException {
        String view = read("frontend/src/views/ConfigView.vue");

        assertThat(view).contains("formatModelLabel(model)");
        assertThat(view).contains("function formatModelLabel");
        assertThat(view).contains("displayName === id");
        assertThat(view).doesNotContain("`${model.id} - ${model.displayName}`");
    }

    @Test
    void devEnvTemplateDocumentsRealProviderSmokeVariables() throws IOException {
        String envExample = read(".env.dev.example");
        String readme = read("README.md");

        assertThat(envExample)
            .contains("E2E_PROVIDER_TYPE")
            .contains("E2E_PROVIDER_BASE_URL")
            .contains("E2E_PROVIDER_API_KEY")
            .contains("E2E_PROVIDER_MODEL");
        assertThat(readme)
            .contains("E2E_PROVIDER_*")
            .contains("不要用本地 stub");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
