// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.di

import android.content.Context
import com.duarf.app.pack.AssetPackSource
import com.duarf.data.crypto.KeyStoreCrypto
import com.duarf.data.db.*
import com.duarf.data.prefs.DuarfPreferences
import com.duarf.data.repo.AlertRepository
import com.duarf.data.repo.StatsRepository
import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.model.ScamEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DuarfDatabase =
        DuarfDatabase.getInstance(context)

    @Provides
    fun provideAlertDao(database: DuarfDatabase): AlertDao =
        database.alertDao()

    @Provides
    fun provideConversationStatsDao(database: DuarfDatabase): ConversationStatsDao =
        database.conversationStatsDao()

    @Provides
    fun provideSuppressedFingerprintDao(database: DuarfDatabase): SuppressedFingerprintDao =
        database.suppressedFingerprintDao()

    @Provides
    fun provideDailyCounterDao(database: DuarfDatabase): DailyCounterDao =
        database.dailyCounterDao()

    @Provides
    @Singleton
    fun provideKeyStoreCrypto(): KeyStoreCrypto =
        KeyStoreCrypto()

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DuarfPreferences =
        DuarfPreferences(context)

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(preferences: DuarfPreferences): com.duarf.data.prefs.UserPreferencesRepository =
        preferences

    @Provides
    @Singleton
    fun provideAlertDispatcher(dispatcher: com.duarf.app.notification.NotificationDispatcher): com.duarf.app.notification.AlertDispatcher =
        dispatcher

    @Provides
    @Singleton
    fun provideAlertRepository(
        alertDao: AlertDao,
        suppressedDao: SuppressedFingerprintDao,
        preferences: com.duarf.data.prefs.UserPreferencesRepository,
        crypto: KeyStoreCrypto
    ): AlertRepository =
        AlertRepository(alertDao, suppressedDao, preferences, crypto)

    @Provides
    @Singleton
    fun provideStatsRepository(
        conversationStatsDao: ConversationStatsDao,
        dailyCounterDao: DailyCounterDao
    ): StatsRepository =
        StatsRepository(conversationStatsDao, dailyCounterDao)

    @Provides
    @Singleton
    fun provideFamilyContactRepository(
        preferences: com.duarf.data.prefs.UserPreferencesRepository,
        crypto: KeyStoreCrypto
    ): com.duarf.data.repo.FamilyContactRepository =
        com.duarf.data.repo.FamilyContactRepository(preferences, crypto)

    @Provides
    @Singleton
    fun provideScamEngine(@ApplicationContext context: Context): ScamEngine =
        com.duarf.app.engine.LazyScamEngine(context)
}
