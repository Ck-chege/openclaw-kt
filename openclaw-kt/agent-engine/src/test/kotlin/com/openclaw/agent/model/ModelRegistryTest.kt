package com.openclaw.agent.model

import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import com.openclaw.protocol.identifiers.ModelId
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ModelRegistryTest {

    private fun registryWithKeys(vararg keys: Pair<String, String>): ModelRegistry {
        val keyMap = keys.toMap()
        // Disable Claude CLI credential discovery in tests — use only the provided key map
        return ModelRegistry(
            apiKeyResolver = { keyMap[it] },
            claudeCliCredentialReader = { null }
        )
    }

    private fun registryWithAllKeys(): ModelRegistry =
        registryWithKeys(
            "ANTHROPIC_API_KEY" to "test-anthropic-key",
            "OPENAI_API_KEY" to "test-openai-key",
            "GEMINI_API_KEY" to "test-google-key",
            "DEEPSEEK_API_KEY" to "test-deepseek-key",
            "OPENROUTER_API_KEY" to "test-openrouter-key",
            "MISTRAL_API_KEY" to "test-mistral-key"
        )

    @Nested
    inner class Resolve {

        @Test
        fun `resolve known Anthropic model returns correct definition`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("anthropic:claude-sonnet-4-5"))
            def.provider shouldBe "anthropic"
            def.modelName shouldBe "claude-sonnet-4-5"
            def.displayName shouldBe "Claude Sonnet 4.5"
            def.maxContextTokens shouldBe 200_000
        }

        @Test
        fun `resolve known OpenAI model returns correct definition`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("openai:gpt-4o"))
            def.provider shouldBe "openai"
            def.modelName shouldBe "gpt-4o"
            def.displayName shouldBe "GPT-4o"
            def.maxContextTokens shouldBe 128_000
        }

        @Test
        fun `resolve known Google model returns correct definition`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("google:gemini-2.5-pro"))
            def.provider shouldBe "google"
            def.modelName shouldBe "gemini-2.5-pro"
            def.displayName shouldBe "Gemini 2.5 Pro"
            def.maxContextTokens shouldBe 1_048_576
        }

        @Test
        fun `resolve known DeepSeek model returns correct definition`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("deepseek:deepseek-chat"))
            def.provider shouldBe "deepseek"
            def.modelName shouldBe "deepseek-chat"
            def.displayName shouldBe "DeepSeek Chat"
            def.maxContextTokens shouldBe 64_000
        }

        @Test
        fun `resolve latest Anthropic model returns correct definition`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("anthropic:claude-sonnet-4-6"))
            def.provider shouldBe "anthropic"
            def.modelName shouldBe "claude-sonnet-4-6"
            def.displayName shouldBe "Claude Sonnet 4.6"
            def.maxContextTokens shouldBe 200_000
        }

        @Test
        fun `resolve latest OpenAI model returns correct definition`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("openai:gpt-5.4"))
            def.provider shouldBe "openai"
            def.modelName shouldBe "gpt-5.4"
            def.displayName shouldBe "GPT-5.4"
            def.maxContextTokens shouldBe 1_050_000
        }

        @Test
        fun `resolve unknown model throws UnknownModel`() {
            val registry = registryWithAllKeys()
            val error = assertThrows<ModelRegistryError.UnknownModel> {
                registry.resolve(ModelId("anthropic:claude-unknown"))
            }
            error.modelId shouldBe ModelId("anthropic:claude-unknown")
        }
    }

    @Nested
    inner class ResolveExecutor {

        @Test
        fun `resolveExecutor for Anthropic model succeeds with API key`() {
            val registry = registryWithAllKeys()
            val executor = registry.resolveExecutor(ModelId("anthropic:claude-sonnet-4-5"))
            executor shouldNotBe null
        }

        @Test
        fun `resolveExecutor for OpenAI model succeeds with API key`() {
            val registry = registryWithAllKeys()
            val executor = registry.resolveExecutor(ModelId("openai:gpt-4o"))
            executor shouldNotBe null
        }

        @Test
        fun `resolveExecutor backs OpenAI with Koog retrying client`() {
            val registry = registryWithAllKeys()
            val executor = registry.resolveExecutor(ModelId("openai:gpt-4o"))
                .shouldBeInstanceOf<SingleLLMPromptExecutor>()

            val clientField = executor::class.java.getDeclaredField("llmClient")
            clientField.isAccessible = true
            clientField.get(executor).shouldBeInstanceOf<RetryingLLMClient>()
        }

        @Test
        fun `resolveExecutor caches same executor for same provider`() {
            val registry = registryWithAllKeys()
            val executor1 = registry.resolveExecutor(ModelId("anthropic:claude-sonnet-4-5"))
            val executor2 = registry.resolveExecutor(ModelId("anthropic:claude-opus-4-5"))
            executor1 shouldBeSameInstanceAs executor2
        }

        @Test
        fun `resolveExecutor recreates provider executor when API key changes`() {
            val keys = mutableMapOf(
                "ANTHROPIC_API_KEY" to "key-v1",
                "OPENAI_API_KEY" to "openai-key"
            )
            val registry = ModelRegistry(apiKeyResolver = { keys[it] })

            val first = registry.resolveExecutor(ModelId("anthropic:claude-sonnet-4-5"))
            keys["ANTHROPIC_API_KEY"] = "key-v2"
            val second = registry.resolveExecutor(ModelId("anthropic:claude-sonnet-4-5"))

            (first === second) shouldBe false
        }

        @Test
        fun `resolveExecutor missing API key throws MissingApiKey`() {
            val registry = registryWithKeys() // no keys at all
            val error = assertThrows<ModelRegistryError.MissingApiKey> {
                registry.resolveExecutor(ModelId("anthropic:claude-sonnet-4-5"))
            }
            error.provider shouldBe "anthropic"
            error.envVar shouldBe "ANTHROPIC_API_KEY"
        }

        @Test
        fun `resolveExecutor unknown model throws UnknownModel`() {
            val registry = registryWithAllKeys()
            assertThrows<ModelRegistryError.UnknownModel> {
                registry.resolveExecutor(ModelId("anthropic:does-not-exist"))
            }
        }

        @Test
        fun `availability reports missing key with reason`() {
            val registry = registryWithKeys()
            val availability = registry.availability(ModelId("google:gemini-2.5-pro"))
            availability.ready shouldBe false
            availability.unavailableReason shouldBe "Missing GEMINI_API_KEY"
        }

        @Test
        fun `availability reports ready when provider is configured`() {
            val registry = registryWithAllKeys()
            val availability = registry.availability(ModelId("deepseek:deepseek-chat"))
            availability.ready shouldBe true
            availability.unavailableReason shouldBe null
        }
    }

    @Nested
    inner class ResolveKoogModel {

        @Test
        fun `resolveKoogModel for Anthropic model returns LLModel`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("anthropic:claude-sonnet-4-5"))
            val llModel = registry.resolveKoogModel(def)
            llModel shouldNotBe null
        }

        @Test
        fun `resolveKoogModel for OpenAI model returns LLModel`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("openai:gpt-4o"))
            val llModel = registry.resolveKoogModel(def)
            llModel shouldNotBe null
        }

        @Test
        fun `resolveKoogModel for Google model returns LLModel`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("google:gemini-2.5-pro"))
            val llModel = registry.resolveKoogModel(def)
            llModel.id shouldBe "gemini-2.5-pro"
            llModel.provider.id shouldBe "google"
        }

        @Test
        fun `resolveKoogModel for DeepSeek model returns LLModel`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("deepseek:deepseek-chat"))
            val llModel = registry.resolveKoogModel(def)
            llModel.id shouldBe "deepseek-chat"
            llModel.provider.id shouldBe "deepseek"
        }

        @Test
        fun `resolveKoogModel for Mistral model returns LLModel`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("mistral:mistral-medium-latest"))
            val llModel = registry.resolveKoogModel(def)
            llModel.id shouldBe "mistral-medium-latest"
            llModel.provider.id shouldBe "mistralai"
        }

        @Test
        fun `resolveKoogModel for OpenRouter model returns LLModel`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("openrouter:openai/gpt-5"))
            val llModel = registry.resolveKoogModel(def)
            llModel.id shouldBe "openai/gpt-5"
            llModel.provider.id shouldBe "openrouter"
        }

        @Test
        fun `resolveKoogModel for latest Anthropic custom model returns LLModel`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("anthropic:claude-sonnet-4-6"))
            val llModel = registry.resolveKoogModel(def)
            llModel.id shouldBe "claude-sonnet-4-6"
            llModel.provider.id shouldBe "anthropic"
            llModel.contextLength shouldBe 200_000L
        }

        @Test
        fun `resolveKoogModel for latest OpenAI custom model includes OpenAI endpoint capabilities`() {
            val registry = registryWithAllKeys()
            val def = registry.resolve(ModelId("openai:gpt-5.4"))
            val llModel = registry.resolveKoogModel(def)
            llModel.id shouldBe "gpt-5.4"
            llModel.provider.id shouldBe "openai"
            llModel.contextLength shouldBe 1_050_000L
            llModel.supports(ai.koog.prompt.llm.LLMCapability.OpenAIEndpoint.Completions) shouldBe true
            llModel.supports(ai.koog.prompt.llm.LLMCapability.OpenAIEndpoint.Responses) shouldBe true
        }

        @Test
        fun `resolveKoogModel for OpenAI compatible custom provider uses OpenAI transport model`() {
            val registry = registryWithAllKeys()
            val custom = ModelDefinition(
                id = ModelId("groq:llama-3.3-70b-versatile"),
                provider = "groq",
                modelName = "llama-3.3-70b-versatile",
                displayName = "Groq Llama 3.3 70B",
                maxContextTokens = 131_072
            )
            val llModel = registry.resolveKoogModel(custom)
            llModel.id shouldBe "llama-3.3-70b-versatile"
            llModel.provider.id shouldBe "openai"
            llModel.contextLength shouldBe 131_072L
        }

        @Test
        fun `resolveKoogModel for Ollama custom model returns Ollama LLModel`() {
            val registry = registryWithAllKeys()
            val custom = ModelDefinition(
                id = ModelId("ollama:qwen3"),
                provider = "ollama",
                modelName = "qwen3",
                displayName = "Qwen3",
                maxContextTokens = 131_072
            )
            val llModel = registry.resolveKoogModel(custom)
            llModel.id shouldBe "qwen3"
            llModel.provider.id shouldBe "ollama"
            llModel.contextLength shouldBe 131_072L
        }

        @Test
        fun `resolveKoogModel for unknown provider throws UnknownProvider`() {
            val registry = registryWithAllKeys()
            val fakeDef = ModelDefinition(
                id = ModelId("cohere:command-r"),
                provider = "cohere",
                modelName = "command-r",
                displayName = "Command R"
            )
            assertThrows<ModelRegistryError.UnknownProvider> {
                registry.resolveKoogModel(fakeDef)
            }
        }
    }

    @Nested
    inner class ListAndRegister {

        @Test
        fun `list returns all built-in models`() {
            val registry = registryWithAllKeys()
            val models = registry.list()
            models shouldHaveAtLeastSize 28
            models.map { it.id.raw } shouldContain "anthropic:claude-sonnet-4-5"
            models.map { it.id.raw } shouldContain "anthropic:claude-sonnet-4-6"
            models.map { it.id.raw } shouldContain "openai:gpt-4o"
            models.map { it.id.raw } shouldContain "openai:gpt-5.4"
            models.map { it.id.raw } shouldContain "google:gemini-2.5-pro"
            models.map { it.id.raw } shouldContain "deepseek:deepseek-chat"
            models.map { it.id.raw } shouldContain "mistral:mistral-medium-latest"
            models.map { it.id.raw } shouldContain "openrouter:openai/gpt-5"
        }

        @Test
        fun `register custom model adds to catalog`() {
            val registry = registryWithAllKeys()
            val custom = ModelDefinition(
                id = ModelId("anthropic:custom-model"),
                provider = "anthropic",
                modelName = "custom-model",
                displayName = "Custom Model",
                maxContextTokens = 50_000
            )
            registry.register(custom)

            val resolved = registry.resolve(ModelId("anthropic:custom-model"))
            resolved.displayName shouldBe "Custom Model"
            resolved.maxContextTokens shouldBe 50_000
        }
    }

    @Nested
    inner class ModelIdParsing {

        @Test
        fun `ModelId provider extracts prefix correctly`() {
            ModelId("anthropic:claude-sonnet-4-5").provider shouldBe "anthropic"
            ModelId("openai:gpt-4o").provider shouldBe "openai"
        }

        @Test
        fun `ModelId model extracts name correctly`() {
            ModelId("anthropic:claude-sonnet-4-5").model shouldBe "claude-sonnet-4-5"
            ModelId("openai:gpt-4o").model shouldBe "gpt-4o"
        }
    }

    @Nested
    inner class ProviderFactoryTests {

        @Test
        fun `LLMProvider fromPrefix resolves anthropic`() {
            LLMProvider.fromPrefix("anthropic") shouldBe LLMProvider.ANTHROPIC
            LLMProvider.fromPrefix("ANTHROPIC") shouldBe LLMProvider.ANTHROPIC
        }

        @Test
        fun `LLMProvider fromPrefix resolves openai`() {
            LLMProvider.fromPrefix("openai") shouldBe LLMProvider.OPENAI
        }

        @Test
        fun `LLMProvider fromPrefix returns null for unknown`() {
            LLMProvider.fromPrefix("cohere") shouldBe null
        }
    }

    @Nested
    inner class ErrorTypes {

        @Test
        fun `UnknownModel is a ModelRegistryError`() {
            val error = ModelRegistryError.UnknownModel(ModelId("test:model"))
            error.shouldBeInstanceOf<ModelRegistryError>()
            error.message shouldBe "Unknown model: test:model"
        }

        @Test
        fun `UnknownProvider is a ModelRegistryError`() {
            val error = ModelRegistryError.UnknownProvider("cohere")
            error.shouldBeInstanceOf<ModelRegistryError>()
            error.message shouldBe "Unknown provider: cohere"
        }

        @Test
        fun `MissingApiKey is a ModelRegistryError`() {
            val error = ModelRegistryError.MissingApiKey("anthropic", "ANTHROPIC_API_KEY")
            error.shouldBeInstanceOf<ModelRegistryError>()
            error.message shouldBe "Missing API key for provider 'anthropic': set environment variable ANTHROPIC_API_KEY"
        }
    }
}
