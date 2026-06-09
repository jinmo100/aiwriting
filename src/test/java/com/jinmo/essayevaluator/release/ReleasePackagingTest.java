package com.jinmo.essayevaluator.release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReleasePackagingTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void releaseComposeUsesPrebuiltGhcrImagesAndBundlesDatastoresWithoutPublishingThem() throws IOException {
        String compose = read("docker-compose.release.yml");

        assertThat(compose).contains("ghcr.io/jinmo100/essay-evaluator-backend");
        assertThat(compose).contains("ghcr.io/jinmo100/essay-evaluator-frontend");
        assertThat(compose).contains("${APP_VERSION:-latest}");
        assertThat(compose).contains("pgvector/pgvector:pg16");
        assertThat(compose).contains("redis:7-alpine");
        assertThat(compose).contains("${FRONTEND_BIND:-0.0.0.0}:${FRONTEND_PORT:-8088}:80");
        assertThat(compose).doesNotContain("dockerfile:");
        assertThat(compose).doesNotContain("${POSTGRES_PORT:-5432}:5432");
        assertThat(compose).doesNotContain("${REDIS_PORT:-6379}:6379");
    }

    @Test
    void dockerfilesAreSelfContainedAndReproducible() throws IOException {
        String backendDockerfile = read("Dockerfile.backend");
        String frontendDockerfile = read("Dockerfile.frontend");
        String rootDockerignore = read(".dockerignore");
        String frontendDockerignore = read("frontend/.dockerignore");

        assertThat(backendDockerfile).contains("AS builder");
        assertThat(backendDockerfile).contains("./gradlew bootJar --no-daemon");
        assertThat(backendDockerfile).contains("MaxRAMPercentage=75.0");
        assertThat(frontendDockerfile).contains("npm ci");
        assertThat(rootDockerignore).contains(".env.dev.local");
        assertThat(rootDockerignore).contains("frontend");
        assertThat(frontendDockerignore).contains("node_modules");
        assertThat(frontendDockerignore).contains("dist");
    }

    @Test
    void publishWorkflowDocumentsEdgeAndStableImageTags() throws IOException {
        String workflow = read(".github/workflows/publish-images.yml");
        String deployment = read("docs/DEPLOYMENT.md");
        String envExample = read(".env.release.example");

        assertThat(workflow).contains("ghcr.io/jinmo100/essay-evaluator-backend");
        assertThat(workflow).contains("ghcr.io/jinmo100/essay-evaluator-frontend");
        assertThat(workflow).contains("linux/amd64,linux/arm64");
        assertThat(workflow).contains("type=raw,value=edge");
        assertThat(workflow).contains("type=raw,value=latest,enable=${{ startsWith(github.ref, 'refs/tags/v') }}");
        assertThat(deployment).contains("latest = 最新稳定版");
        assertThat(deployment).contains("edge = main 分支最新版");
        assertThat(envExample).contains("APP_VERSION=latest");
        assertThat(envExample).contains("ESSAY_EVALUATOR_SESSION_COOKIE_SECURE=false");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
