package com.jinmo.essayevaluator.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.embedding.EmbeddingClient;
import com.jinmo.essayevaluator.embedding.EmbeddingConfig;
import com.jinmo.essayevaluator.embedding.EmbeddingConfigService;
import com.jinmo.essayevaluator.embedding.EmbeddingProviderType;
import com.jinmo.essayevaluator.job.BackgroundJob;
import com.jinmo.essayevaluator.job.BackgroundJobHandler;
import com.jinmo.essayevaluator.job.BackgroundJobStatus;
import com.jinmo.essayevaluator.job.BackgroundJobType;
import com.jinmo.essayevaluator.mapper.RagChunkEmbeddingMapper;
import com.jinmo.essayevaluator.mapper.RagChunkMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagIndexJobHandlerTest {

    @Mock
    private EmbeddingConfigService embeddingConfigService;
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private RagChunkMapper ragChunkMapper;
    @Mock
    private RagChunkEmbeddingMapper ragChunkEmbeddingMapper;

    @Test
    void indexHandlerWritesEmbeddingsForOwnerAndConfig() throws Exception {
        EmbeddingConfig config = embeddingConfig(11L, 7L);
        RagChunk chunk = chunk(101L, "Subject and verb must agree.", "hash-101");
        when(embeddingConfigService.loadOwnedConfigForUser(7L, 11L)).thenReturn(config);
        when(embeddingConfigService.resolvePlainApiKey(config)).thenReturn("sk-embedding");
        when(ragChunkMapper.findActiveChunks()).thenReturn(List.of(chunk));
        when(embeddingClient.embed(config, "sk-embedding", List.of(chunk.getContent())))
            .thenReturn(new EmbeddingClient.EmbeddingResult(
                List.of(Collections.nCopies(1536, 0.01d)),
                12L,
                "text-embedding-3-large",
                1536
            ));

        BackgroundJobHandler.JobResult result = newHandler().handle(job("""
            {"embeddingConfigId":11,"force":false}
            """));

        assertThat(result.status()).isEqualTo(BackgroundJobStatus.COMPLETED);
        assertThat((Map<String, Object>) result.result())
            .containsEntry("totalChunks", 1)
            .containsEntry("processedChunks", 1)
            .containsEntry("failedChunks", 0)
            .containsEntry("embeddingVersion", "text-embedding-3-large:1536:RAG_KB_V1");
        ArgumentCaptor<String> vectorCaptor = ArgumentCaptor.forClass(String.class);
        verify(ragChunkEmbeddingMapper).upsertEmbedding(
            eq(7L),
            eq(11L),
            eq(101L),
            eq("text-embedding-3-large"),
            eq(1536),
            eq("text-embedding-3-large:1536:RAG_KB_V1"),
            eq("hash-101"),
            vectorCaptor.capture()
        );
        assertThat(vectorCaptor.getValue()).startsWith("[0.01").endsWith("]");
    }

    @Test
    void indexHandlerMarksSkippedWhenConfigMissing() throws Exception {
        when(embeddingConfigService.loadOwnedConfigForUser(7L, 11L))
            .thenThrow(new BusinessException("Embedding 配置不存在"));

        BackgroundJobHandler.JobResult result = newHandler().handle(job("""
            {"embeddingConfigId":11,"force":false}
            """));

        assertThat(result.status()).isEqualTo(BackgroundJobStatus.SKIPPED);
        assertThat((Map<String, Object>) result.result())
            .containsEntry("action", "OPEN_EMBEDDING_CONFIG");
        verify(ragChunkMapper, never()).findActiveChunks();
    }

    @Test
    void indexHandlerDoesNotReadAnotherUsersConfig() throws Exception {
        when(embeddingConfigService.loadOwnedConfigForUser(7L, 22L))
            .thenThrow(new BusinessException("Embedding 配置不存在"));

        newHandler().handle(job("""
            {"embeddingConfigId":22,"force":false}
            """));

        verify(embeddingConfigService).loadOwnedConfigForUser(7L, 22L);
        verify(embeddingConfigService, never()).loadOwnedConfig(22L);
    }

    @Test
    void forceRebuildReplacesCurrentVersionEmbeddings() throws Exception {
        EmbeddingConfig config = embeddingConfig(11L, 7L);
        RagChunk chunk = chunk(101L, "Use a clear topic sentence.", "hash-101");
        when(embeddingConfigService.loadOwnedConfigForUser(7L, 11L)).thenReturn(config);
        when(embeddingConfigService.resolvePlainApiKey(config)).thenReturn("sk-embedding");
        when(ragChunkMapper.findActiveChunks()).thenReturn(List.of(chunk));
        when(embeddingClient.embed(config, "sk-embedding", List.of(chunk.getContent())))
            .thenReturn(new EmbeddingClient.EmbeddingResult(
                List.of(Collections.nCopies(1536, 0.02d)),
                10L,
                "text-embedding-3-large",
                1536
            ));

        newHandler().handle(job("""
            {"embeddingConfigId":11,"force":true}
            """));

        InOrder inOrder = inOrder(ragChunkEmbeddingMapper);
        inOrder.verify(ragChunkEmbeddingMapper).deleteByUserConfigVersion(
            7L,
            11L,
            "text-embedding-3-large:1536:RAG_KB_V1"
        );
        inOrder.verify(ragChunkEmbeddingMapper).upsertEmbedding(
            eq(7L),
            eq(11L),
            eq(101L),
            eq("text-embedding-3-large"),
            eq(1536),
            eq("text-embedding-3-large:1536:RAG_KB_V1"),
            eq("hash-101"),
            anyString()
        );
    }

    private RagIndexJobHandler newHandler() {
        return new RagIndexJobHandler(
            embeddingConfigService,
            embeddingClient,
            ragChunkMapper,
            ragChunkEmbeddingMapper,
            new ObjectMapper()
        );
    }

    private BackgroundJob job(String payloadJson) {
        BackgroundJob job = new BackgroundJob();
        job.setId(1L);
        job.setJobType(BackgroundJobType.RAG_INDEX);
        job.setOwnerUserId(7L);
        job.setRequestedByUserId(7L);
        job.setPayloadJson(payloadJson);
        return job;
    }

    private EmbeddingConfig embeddingConfig(Long id, Long ownerUserId) {
        EmbeddingConfig config = new EmbeddingConfig();
        config.setId(id);
        config.setOwnerUserId(ownerUserId);
        config.setProviderType(EmbeddingProviderType.OPENAI_EMBEDDINGS);
        config.setBaseUrl("https://api.example.com/v1");
        config.setModelName("text-embedding-3-large");
        config.setDimensions(1536);
        config.setTimeoutSeconds(30);
        return config;
    }

    private RagChunk chunk(Long id, String content, String contentHash) {
        RagChunk chunk = new RagChunk();
        chunk.setId(id);
        chunk.setDocumentId(1L);
        chunk.setChunkNo(1);
        chunk.setContent(content);
        chunk.setContentHash(contentHash);
        chunk.setIsActive(true);
        return chunk;
    }
}
