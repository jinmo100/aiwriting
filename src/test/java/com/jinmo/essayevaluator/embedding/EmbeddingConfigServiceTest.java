package com.jinmo.essayevaluator.embedding;

import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.mapper.EmbeddingConfigMapper;
import com.jinmo.essayevaluator.security.ApiKeyEncryptionService;
import com.jinmo.essayevaluator.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.jinmo.essayevaluator.embedding.EmbeddingRequestDtos.EmbeddingConfigCreateRequest;
import static com.jinmo.essayevaluator.embedding.EmbeddingRequestDtos.EmbeddingConfigUpdateRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingConfigServiceTest {

    @Mock
    private EmbeddingConfigMapper embeddingConfigMapper;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ApiKeyEncryptionService apiKeyEncryptionService;
    @Mock
    private EmbeddingClient embeddingClient;

    @Test
    void createConfigEncryptsApiKeyAndReturnsPreview() {
        when(currentUserService.requireUserId()).thenReturn(7L);
        when(apiKeyEncryptionService.encrypt("sk-embedding-secret")).thenReturn("encrypted-key");

        EmbeddingResponseDtos.EmbeddingConfigResponse response = newService().createConfig(
            new EmbeddingConfigCreateRequest(
                "默认 Embedding",
                "OPENAI_EMBEDDINGS",
                "https://api.example.com/v1/embeddings",
                "sk-embedding-secret",
                "text-embedding-3-large",
                1536,
                30,
                true
            )
        );

        verify(embeddingConfigMapper).resetDefaultForOwner(7L);
        ArgumentCaptor<EmbeddingConfig> configCaptor = ArgumentCaptor.forClass(EmbeddingConfig.class);
        verify(embeddingConfigMapper).insert(configCaptor.capture());
        EmbeddingConfig config = configCaptor.getValue();
        assertThat(config.getOwnerUserId()).isEqualTo(7L);
        assertThat(config.getProviderType()).isEqualTo(EmbeddingProviderType.OPENAI_EMBEDDINGS);
        assertThat(config.getBaseUrl()).isEqualTo("https://api.example.com/v1");
        assertThat(config.getApiKeyEncrypted()).isEqualTo("encrypted-key");
        assertThat(config.getIsDefault()).isTrue();
        assertThat(response.hasApiKey()).isTrue();
        assertThat(response.apiKeyPreview()).isEqualTo("sk-...cret");
        assertThat(response.toString()).doesNotContain("embedding-secret");
    }

    @Test
    void setDefaultOnlyAffectsCurrentUser() {
        when(currentUserService.requireUserId()).thenReturn(7L);
        EmbeddingConfig existing = ownedConfig(11L, 7L);
        when(embeddingConfigMapper.selectOne(any())).thenReturn(existing);

        newService().setDefault(11L);

        verify(embeddingConfigMapper).resetDefaultForOwner(7L);
        ArgumentCaptor<EmbeddingConfig> updateCaptor = ArgumentCaptor.forClass(EmbeddingConfig.class);
        verify(embeddingConfigMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(11L);
        assertThat(updateCaptor.getValue().getIsDefault()).isTrue();
    }

    @Test
    void updateWithoutApiKeyKeepsExistingEncryptedKey() {
        when(currentUserService.requireUserId()).thenReturn(7L);
        EmbeddingConfig existing = ownedConfig(11L, 7L);
        existing.setApiKeyEncrypted("old-encrypted-key");
        when(embeddingConfigMapper.selectOne(any())).thenReturn(existing);

        newService().updateConfig(
            11L,
            new EmbeddingConfigUpdateRequest(
                "改名",
                "OPENAI_EMBEDDINGS",
                "https://api.example.com/v1",
                "",
                "text-embedding-3-small",
                1536,
                45,
                false
            )
        );

        verify(apiKeyEncryptionService, never()).encrypt(any());
        ArgumentCaptor<EmbeddingConfig> updateCaptor = ArgumentCaptor.forClass(EmbeddingConfig.class);
        verify(embeddingConfigMapper).updateById(updateCaptor.capture());
        EmbeddingConfig update = updateCaptor.getValue();
        assertThat(update.getApiKeyEncrypted()).isEqualTo("old-encrypted-key");
        assertThat(update.getConfigName()).isEqualTo("改名");
        assertThat(update.getModelName()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void rejectsDimensionsOtherThan1536() {
        when(currentUserService.requireUserId()).thenReturn(7L);

        assertThatThrownBy(() -> newService().createConfig(
            new EmbeddingConfigCreateRequest(
                "bad",
                "OPENAI_EMBEDDINGS",
                "https://api.example.com/v1",
                "sk-test",
                "text-embedding-3-large",
                1024,
                30,
                false
            )
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("1536");

        verify(embeddingConfigMapper, never()).insert(any(EmbeddingConfig.class));
    }

    @Test
    void loadOwnedConfigRejectsAnotherUser() {
        when(currentUserService.requireUserId()).thenReturn(7L);
        when(embeddingConfigMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> newService().getConfig(99L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Embedding 配置不存在");
    }

    private EmbeddingConfig ownedConfig(Long id, Long ownerUserId) {
        EmbeddingConfig config = new EmbeddingConfig();
        config.setId(id);
        config.setOwnerUserId(ownerUserId);
        config.setConfigName("配置");
        config.setProviderType(EmbeddingProviderType.OPENAI_EMBEDDINGS);
        config.setBaseUrl("https://api.example.com/v1");
        config.setApiKeyEncrypted("encrypted");
        config.setModelName("text-embedding-3-large");
        config.setDimensions(1536);
        config.setTimeoutSeconds(30);
        config.setIsDefault(false);
        return config;
    }

    private EmbeddingConfigService newService() {
        return new EmbeddingConfigService(
            embeddingConfigMapper,
            currentUserService,
            apiKeyEncryptionService,
            embeddingClient
        );
    }
}
