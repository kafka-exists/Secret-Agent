package com.keyforge.chat.data.di

import android.content.Context
import androidx.room.Room
import com.keyforge.chat.data.crypto.KeystoreCrypto
import com.keyforge.chat.data.datastore.SettingsStore
import com.keyforge.chat.data.db.ApiKeyDao
import com.keyforge.chat.data.db.ConversationDao
import com.keyforge.chat.data.db.GeneratedFileDao
import com.keyforge.chat.data.db.KeyForgeDatabase
import com.keyforge.chat.data.db.MessageDao
import com.keyforge.chat.data.db.ModelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KeyForgeDatabase =
        Room.databaseBuilder(context, KeyForgeDatabase::class.java, KeyForgeDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideConversationDao(db: KeyForgeDatabase): ConversationDao = db.conversations()
    @Provides fun provideMessageDao(db: KeyForgeDatabase): MessageDao = db.messages()
    @Provides fun provideGeneratedFileDao(db: KeyForgeDatabase): GeneratedFileDao = db.generatedFiles()
    @Provides fun provideApiKeyDao(db: KeyForgeDatabase): ApiKeyDao = db.apiKeys()
    @Provides fun provideModelDao(db: KeyForgeDatabase): ModelDao = db.models()

    @Provides
    @Singleton
    fun provideKeystoreCrypto(): KeystoreCrypto = KeystoreCrypto()

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore = SettingsStore(context)
}
