package com.jinmo.essayevaluator.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.ai.provider.AIProviderErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleEmbeddingClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void embedsOpenAiCompatibleResponseAndChecks1536Dimensions() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        startServer(200, embeddingResponse(1536), requestBody, authorization);

        OpenAiCompatibleEmbeddingClient client = newClient();
        EmbeddingClient.EmbeddingResult result = client.embed(config(serverBaseUrl() + "/v1"), "sk-test", List.of("hello"));

        assertThat(authorization.get()).isEqualTo("Bearer sk-test");
        assertThat(requestBody.get())
            .contains("\"model\":\"text-embedding-3-large\"")
            .contains("\"input\":[\"hello\"]")
            .contains("\"dimensions\":1536");
        assertThat(result.embeddings()).hasSize(1);
        assertThat(result.embeddings().getFirst()).hasSize(1536);
        assertThat(result.dimensions()).isEqualTo(1536);
        assertThat(result.latencyMillis()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void rejectsUnexpectedEmbeddingDimensions() throws Exception {
        startServer(200, embeddingResponse(3), new AtomicReference<>(), new AtomicReference<>());

        OpenAiCompatibleEmbeddingClient client = newClient();

        assertThatThrownBy(() -> client.embed(config(serverBaseUrl() + "/v1"), "sk-test", List.of("hello")))
            .isInstanceOf(EmbeddingClientException.class)
            .extracting("errorCode")
            .isEqualTo(AIProviderErrorCode.INVALID_PROVIDER_CONFIG);
    }

    @Test
    void classifiesUnauthorizedResponse() throws Exception {
        startServer(401, "{\"error\":{\"message\":\"invalid api key\"}}", new AtomicReference<>(), new AtomicReference<>());

        OpenAiCompatibleEmbeddingClient client = newClient();

        assertThatThrownBy(() -> client.embed(config(serverBaseUrl() + "/v1"), "bad-key", List.of("hello")))
            .isInstanceOf(EmbeddingClientException.class)
            .extracting("errorCode")
            .isEqualTo(AIProviderErrorCode.AUTH_ERROR);
    }

    private void startServer(
        int status,
        String responseJson,
        AtomicReference<String> requestBody,
        AtomicReference<String> authorization
    ) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    private String serverBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String embeddingResponse(int dimensions) {
        String vector = String.join(",", Collections.nCopies(dimensions, "0.01"));
        return """
            {"data":[{"index":0,"embedding":[%s]}],"model":"text-embedding-3-large"}
            """.formatted(vector);
    }

    private EmbeddingConfig config(String baseUrl) {
        EmbeddingConfig config = new EmbeddingConfig();
        config.setProviderType(EmbeddingProviderType.OPENAI_EMBEDDINGS);
        config.setBaseUrl(baseUrl);
        config.setModelName("text-embedding-3-large");
        config.setDimensions(1536);
        config.setTimeoutSeconds(5);
        return config;
    }

    private OpenAiCompatibleEmbeddingClient newClient() {
        return new OpenAiCompatibleEmbeddingClient(HttpClient.newHttpClient(), new ObjectMapper());
    }
}
