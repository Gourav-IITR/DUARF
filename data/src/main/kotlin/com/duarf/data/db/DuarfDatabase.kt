// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AlertEntity::class,
        ConversationStatsEntity::class,
        SuppressedFingerprintEntity::class,
        DailyCounterEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DuarfDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun conversationStatsDao(): ConversationStatsDao
    abstract fun suppressedFingerprintDao(): SuppressedFingerprintDao
    abstract fun dailyCounterDao(): DailyCounterDao

    companion object {
        private const val DB_NAME = "duarf_database.db"

        @Volatile
        private var INSTANCE: DuarfDatabase? = null

        fun getInstance(context: Context): DuarfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DuarfDatabase::class.java,
                    DB_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
