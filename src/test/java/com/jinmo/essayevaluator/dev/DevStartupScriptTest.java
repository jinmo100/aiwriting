package com.jinmo.essayevaluator.dev;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DevStartupScriptTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void backendDevScriptIsIdempotentAndPropagatesGradleExitCode() throws IOException {
        String script = read("scripts/start-backend-dev.ps1");

        assertThat(script).contains("[int]$BackendPort");
        assertThat(script).contains("Test-BackendAlreadyRunning");
        assertThat(script).contains("/api/auth/me");
        assertThat(script).contains("Backend already running");
        assertThat(script).contains("exit $LASTEXITCODE");
    }

    @Test
    void tunnelScriptCanLoadDevEnvWhenRunDirectly() throws IOException {
        String script = read("scripts/start-vps-postgres-tunnel.ps1");

        assertThat(script).contains("[string]$EnvFile");
        assertThat(script).contains("Load-EnvFile");
        assertThat(script).contains(".env.dev.local");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
