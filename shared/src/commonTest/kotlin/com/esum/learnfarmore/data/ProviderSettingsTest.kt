package com.esum.learnfarmore.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderSettingsTest {

    @Test
    fun hostedProvidersAlwaysRequireAKey() {
        assertTrue(ProviderSettings(ProviderKind.ANTHROPIC, "claude-sonnet-4-5", "http://localhost:9999").requiresApiKey())
        assertTrue(ProviderSettings(ProviderKind.GROK, "grok-4.7", "http://localhost:9999").requiresApiKey())
        assertTrue(ProviderSettings(ProviderKind.GROK, "grok-4.7", "").requiresApiKey())
    }

    @Test
    fun onlyACustomOpenAiEndpointMayRunWithoutAKey() {
        assertTrue(ProviderSettings(ProviderKind.OPENAI_COMPATIBLE, "gpt-5", "").requiresApiKey())
        assertFalse(ProviderSettings(ProviderKind.OPENAI_COMPATIBLE, "llama", "http://127.0.0.1:8080/v1").requiresApiKey())
    }
}
