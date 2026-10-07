package com.duarf.engine

import com.duarf.engine.model.*
import com.duarf.engine.normalize.LanguageScriptDetector
import com.duarf.engine.pack.FilePackSource
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class MarathiHindiDiscriminatorTest {

    private lateinit var engine: DefaultScamEngine
    private val loggedEvents = ArrayList<Int>()

    @Before
    fun setUp() {
        val rootDir = File("../packs").let { if (it.exists()) it else File("packs") }
        loggedEvents.clear()
        engine = DefaultScamEngine.fromPackSource(
            packSource = FilePackSource(rootDir),
            eventSink = { loggedEvents.add(it) }
        )
    }

    private fun createMessage(text: String): IncomingMessage {
        return IncomingMessage(
            fingerprint = "test-fp",
            source = SourceKind.NOTIFICATION,
            app = SourceApp.WHATSAPP,
            conversationKey = "conv-1",
            senderDisplay = "+919876543210",
            senderKind = SenderKind.NUMBER_ONLY,
            senderCountryCode = "+91",
            isGroup = false,
            text = text,
            attachmentHint = null,
            receivedAtMillis = System.currentTimeMillis()
        )
    }

    @Test
    fun `Marathi sentences are identified as Marathi`() {
        val s1 = "तुमचे वीज बिल थकबाकी आहे आणि आज रात्री वीज पुरवठा खंडित केला जाईल. कृपया त्वरित संपर्क करा."
        val s2 = "प्रिय ग्राहक, तुमचा ओटीपी कोणालाही सांगू नका, नाही तर तुमचे खाते बंद होईल."
        val s3 = "आपले पार्सल कस्टम कार्यालयात अडकले आहे. सोडवण्यासाठी दंड भरा."

        assertThat(LanguageScriptDetector.isMarathi(s1)).isTrue()
        assertThat(LanguageScriptDetector.isMarathi(s2)).isTrue()
        assertThat(LanguageScriptDetector.isMarathi(s3)).isTrue()
    }

    @Test
    fun `Hindi sentences are not identified as Marathi`() {
        val s1 = "आपका बिजली बिल बकाया है और आज रात बिजली काट दी जाएगी। कृपया संपर्क करें।"
        val s2 = "प्रिय ग्राहक, अपना ओटीपी किसी के साथ साझा न करें, नहीं तो आपका खाता बंद हो जाएगा।"
        val s3 = "आपका पार्सल कस्टम में रुका हुआ है। रिलीज के लिए जुर्माना भरें।"

        assertThat(LanguageScriptDetector.isMarathi(s1)).isFalse()
        assertThat(LanguageScriptDetector.isMarathi(s2)).isFalse()
        assertThat(LanguageScriptDetector.isMarathi(s3)).isFalse()
    }

    @Test
    fun `Marathi message gates model off and emits event 304 in engine`() {
        val msg = createMessage("तुमचे वीज बिल थकबाकी आहे आणि आज रात्री वीज पुरवठा खंडित केला जाईल. त्वरित संपर्क करा.")
        val verdict = engine.analyze(msg)

        assertThat(verdict.modelProbability).isNull()
        assertThat(loggedEvents).contains(304)
    }

    @Test
    fun `Hindi message does not gate model off and does not emit event 304`() {
        val msg = createMessage("आपका बिजली बिल बकाया है और आज रात बिजली काट दी जाएगी। कृपया संपर्क करें।")
        val verdict = engine.analyze(msg)

        assertThat(loggedEvents).doesNotContain(304)
        if (engine.isModelLoaded) {
            assertThat(verdict.modelProbability).isNotNull()
        }
    }
}
