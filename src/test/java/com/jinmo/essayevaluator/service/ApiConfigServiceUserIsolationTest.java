package com.jinmo.essayevaluator.service;

import com.jinmo.essayevaluator.ai.provider.ProviderConfigInvalidationService;
import com.jinmo.essayevaluator.ai.provider.ProviderEndpointResolver;
import com.jinmo.essayevaluator.ai.provider.ProviderType;
import com.jinmo.essayevaluator.domain.dto.ApiConfigCreateRequest;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import com.jinmo.essayevaluator.mapper.ApiConfigMapper;
import com.jinmo.essayevaluator.security.ApiKeyEncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiConfigServiceUserIsolationTest {

    @Mock
    private ApiConfigMapper apiConfigMapper;
    @Mock
    private ApiKeyEncryptionService apiKeyEncryptionService;
    @Mock
    private ProviderEndpointResolver endpointResolver;
    @Mock
    private ProviderConfigInvalidationService invalidationService;
    @Mock
    private CurrentUserService currentUserService;

    @Test
    void createConfigStoresPrivateOwnerAndResetsOnlyCurrentUsersDefault() {
        ApiConfigService service = new ApiConfigService(
            apiConfigMapper,
            apiKeyEncryptionService,
            endpointResolver,
            invalidationService,
            currentUserService
        );
        when(currentUserService.requireUserId()).thenReturn(7L);
        when(endpointResolver.normalizeBaseUrl(ProviderType.OPENAI_CHAT_COMPLETIONS, "https://api.example.com"))
            .thenReturn("https://api.example.com");
        when(apiKeyEncryptionService.encrypt("sk-test")).thenReturn("encrypted");

        service.createConfig(new ApiConfigCreateRequest(
            "OpenAI",
            ProviderType.OPENAI_CHAT_COMPLETIONS,
            "OpenAI",
            "https://api.example.com",
            "sk-test",
            "gpt-4.1-mini",
            0.3,
            1024,
            60,
            null,
            null,
            null,
            null,
            true
        ));

        verify(apiConfigMapper).resetDefaultForOwner(7L);
        ArgumentCaptor<ApiConfig> configCaptor = ArgumentCaptor.forClass(ApiConfig.class);
        verify(apiConfigMapper).insert(configCaptor.capture());
        ApiConfig config = configCaptor.getValue();
        assertEquals(7L, config.getOwnerUserId());
        assertEquals("PRIVATE", config.getVisibility());
        assertFalse(config.getAllowPublicUse());
        assertEquals("encrypted", config.getApiKeyEncrypted());
    }
}
