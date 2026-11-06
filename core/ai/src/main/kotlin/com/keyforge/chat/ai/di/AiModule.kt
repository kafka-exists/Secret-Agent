package com.keyforge.chat.ai.di

import com.keyforge.chat.ai.AiProvider
import com.keyforge.chat.ai.ProviderType
import com.keyforge.chat.ai.keys.KeyPool
import com.keyforge.chat.ai.providers.GeminiProvider
import com.keyforge.chat.ai.providers.GroqProvider
import com.keyforge.chat.ai.providers.OpenRouterProvider
import com.keyforge.chat.network.createHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = createHttpClient()

    @Provides
    @Singleton
    fun provideProviders(client: HttpClient): Map<ProviderType, @JvmSuppressWildcards AiProvider> = mapOf(
        ProviderType.GEMINI to GeminiProvider(client),
        ProviderType.OPENROUTER to OpenRouterProvider(client),
        ProviderType.GROQ to GroqProvider(client),
    )

    @Provides
    @Singleton
    fun provideKeyPool(): KeyPool = KeyPool()
}
