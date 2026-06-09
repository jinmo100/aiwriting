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

    @Test
    void ragMigrationHardensUserScopeAndBackgroundJobConstraints() throws IOException {
        String migration = read("src/main/resources/db/migration/V11__rag_knowledge_base.sql");

        assertThat(migration)
            .contains("CONSTRAINT ck_background_jobs_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED'))")
            .contains("CONSTRAINT ck_background_jobs_attempt_count_non_negative CHECK (attempt_count >= 0)")
            .contains("CONSTRAINT ck_background_jobs_max_attempts_non_negative CHECK (max_attempts >= 0)")
            .contains("FOREIGN KEY (user_id, embedding_config_id)")
            .contains("REFERENCES embedding_configs(owner_user_id, id)")
            .contains("FOREIGN KEY (user_id, essay_id)")
            .contains("REFERENCES essays(user_id, id)")
            .contains("FOREIGN KEY (essay_id, score_id)")
            .contains("REFERENCES essay_scores(essay_id, id)")
            .contains("COMMENT ON COLUMN rag_feedbacks.api_config_id IS '生成反馈复用的 Chat Provider 配置 ID；公开/共享配置可能跨用户可用，归属和可用性由 service 层结合 visibility/allow_public_use 校验'");
    }

    @Test
    void ragMigrationUsesIdempotentIndexesAndSeedUpsert() throws IOException {
        String migration = read("src/main/resources/db/migration/V11__rag_knowledge_base.sql");

        assertThat(migration)
            .doesNotContainPattern("(?m)^CREATE (UNIQUE )?INDEX (?!IF NOT EXISTS)")
            .contains("CREATE UNIQUE INDEX IF NOT EXISTS ux_rag_documents_skill_version")
            .contains("ON CONFLICT (skill_tag, version) DO UPDATE SET")
            .contains("ON CONFLICT (document_id, chunk_no) DO UPDATE SET")
            .doesNotContain("embedding_vector)")
            .doesNotContain("INSERT INTO rag_chunk_embeddings");
    }

    @Test
    void deploymentDocsWarnAboutPgvectorVolumeUpgrade() throws IOException {
        String deployment = read("docs/DEPLOYMENT.md");

        assertThat(deployment)
            .contains("从旧 release 内置 PostgreSQL 升级到 pgvector")
            .contains("postgres:16-alpine")
            .contains("pgvector/pgvector:pg16")
            .contains("备份数据库")
            .contains("pg_dump")
            .contains("测试环境");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
