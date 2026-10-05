package com.duarf.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alerts",
    indices = [Index(value = ["fingerprint"], unique = true)]
)
data class AlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fingerprint: String,
    val createdAt: Long,
    val level: String,
    val score: Double,
    val category: String,
    val sourceKind: String,
    val app: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val senderDisplayEncrypted: ByteArray?,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val textEncrypted: ByteArray,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val reasonsJsonEncrypted: ByteArray,
    val highlightsJson: String,
    val engineVersion: String,
    val userFeedback: String? = null,
    val feedbackAt: Long? = null,
    val dismissed: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AlertEntity
        return id == other.id && fingerprint == other.fingerprint
    }

    override fun hashCode(): Int = fingerprint.hashCode()
}

@Entity(tableName = "conversation_stats")
data class ConversationStatsEntity(
    @PrimaryKey
    val conversationKey: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val messageCount: Int,
    val trusted: Boolean = false
)

@Entity(tableName = "suppressed_fingerprints")
data class SuppressedFingerprintEntity(
    @PrimaryKey
    val fingerprint: String,
    val createdAt: Long
)

@Entity(tableName = "daily_counters")
data class DailyCounterEntity(
    @PrimaryKey
    val day: String, // YYYY-MM-DD
    val messagesChecked: Int,
    val cautions: Int,
    val dangers: Int
)
