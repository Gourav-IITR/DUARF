package com.duarf.capture

import com.duarf.capture.dedup.Deduplicator
import com.duarf.engine.model.IncomingMessage
import com.duarf.engine.model.SenderKind
import com.duarf.engine.model.SourceApp
import com.duarf.engine.model.SourceKind
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class DeduplicatorTest {

    private lateinit var deduplicator: Deduplicator

    @Before
    fun setUp() {
        deduplicator = Deduplicator(maxLruSize = 100)
    }

    private fun createMessage(
        conversationKey: String = "conv-1",
        sender: String = "+919876543210",
        text: String = "Test message",
        timestamp: Long = 1000L
    ): IncomingMessage {
        val fp = Deduplicator.computeFingerprint(conversationKey, sender, text, timestamp)
        return IncomingMessage(
            fingerprint = fp,
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = conversationKey,
            senderDisplay = sender,
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = timestamp
        )
    }

    @Test
    fun `detects identical message as duplicate`() {
        val msg = createMessage(text = "Hello world", timestamp = 5000L)

        // First time seen -> not duplicate
        val firstCheck = deduplicator.isDuplicate(msg.fingerprint)
        assertThat(firstCheck).isFalse()

        // Second time seen (re-posted notification) -> duplicate!
        val secondCheck = deduplicator.isDuplicate(msg.fingerprint)
        assertThat(secondCheck).isTrue()
    }

    @Test
    fun `different texts produce different fingerprints and are not duplicates`() {
        val msg1 = createMessage(text = "Hello")
        val msg2 = createMessage(text = "World")

        assertThat(msg1.fingerprint).isNotEqualTo(msg2.fingerprint)
        assertThat(deduplicator.isDuplicate(msg1.fingerprint)).isFalse()
        assertThat(deduplicator.isDuplicate(msg2.fingerprint)).isFalse()
    }

    @Test
    fun `context buffer stores and returns previous messages from same sender`() {
        val now = System.currentTimeMillis()
        val msg1 = createMessage(text = "Part 1: Dear customer", timestamp = now - 60_000)
        val msg2 = createMessage(text = "Part 2: Click http://sbi.xyz", timestamp = now)

        val contextForMsg1 = deduplicator.getContextAndRecord(msg1)
        assertThat(contextForMsg1).isEmpty()

        val contextForMsg2 = deduplicator.getContextAndRecord(msg2)
        assertThat(contextForMsg2).hasSize(1)
        assertThat(contextForMsg2.first().text).isEqualTo(msg1.text)
    }

    @Test
    fun `context buffer limits to max messages and respects window`() {
        val now = System.currentTimeMillis()
        // Message from 6 minutes ago (outside 5 min window)
        val oldMsg = createMessage(text = "Old message", timestamp = now - 6 * 60 * 1000)
        val recentMsg1 = createMessage(text = "Recent 1", timestamp = now - 2 * 60 * 1000)
        val currentMsg = createMessage(text = "Current", timestamp = now)

        deduplicator.getContextAndRecord(oldMsg)
        deduplicator.getContextAndRecord(recentMsg1)
        val ctx = deduplicator.getContextAndRecord(currentMsg)

        // Only recentMsg1 should be in context
        assertThat(ctx).hasSize(1)
        assertThat(ctx.first().text).isEqualTo("Recent 1")
    }
}
