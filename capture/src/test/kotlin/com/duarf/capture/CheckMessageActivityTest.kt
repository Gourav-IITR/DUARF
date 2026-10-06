package com.duarf.capture.share

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.InputStream

class CheckMessageActivityTest {

    private class FakeSharedIntent(
        override val action: String?,
        override val type: String?,
        val text: String? = null,
        val uri: Uri? = null
    ) : CheckMessageActivity.SharedIntentData {
        override fun getStringExtra(name: String): String? = if (name == Intent.EXTRA_TEXT) text else null
        override fun getCharSequenceExtra(name: String): CharSequence? = if (name == Intent.EXTRA_PROCESS_TEXT) text else null
        override fun getStreamUri(): Uri? = uri
    }

    /**
     * Fake ContentInspector whose openInputStream throws, to prove that
     * the file content is NEVER opened or read.
     */
    private class FakeThrowingContentInspector(
        val displayName: String = "RTO E challan.apk",
        val mimeType: String = "application/vnd.android.package-archive"
    ) : CheckMessageActivity.ContentInspector {
        var queryCalled = false
        var openInputStreamCalled = false

        override fun queryDisplayName(uri: Uri?): String? {
            queryCalled = true
            return displayName
        }

        override fun getType(uri: Uri?): String? = mimeType

        override fun openInputStream(uri: Uri?): InputStream? {
            openInputStreamCalled = true
            throw UnsupportedOperationException("CRITICAL Invariant: openInputStream must NEVER be called!")
        }
    }

    @Test
    fun `shared plain text extracts text with null attachmentHint`() {
        val fakeIntent = FakeSharedIntent(
            action = Intent.ACTION_SEND,
            type = "text/plain",
            text = "Hello world"
        )

        val inspector = FakeThrowingContentInspector()
        val payload = CheckMessageActivity.extractSharePayload(fakeIntent, inspector)

        assertThat(payload).isNotNull()
        assertThat(payload?.text).isEqualTo("Hello world")
        assertThat(payload?.attachmentHint).isNull()
        assertThat(inspector.queryCalled).isFalse()
        assertThat(inspector.openInputStreamCalled).isFalse()
    }

    @Test
    fun `shared apk file inspects name and mime without opening stream`() {
        val uri = Uri.parse("content://com.android.providers.downloads.documents/document/1234")
        val fakeIntent = FakeSharedIntent(
            action = Intent.ACTION_SEND,
            type = "application/vnd.android.package-archive",
            uri = uri
        )

        val inspector = FakeThrowingContentInspector()
        val payload = CheckMessageActivity.extractSharePayload(fakeIntent, inspector)

        assertThat(payload).isNotNull()
        // Text is strictly the file name (§5.3)
        assertThat(payload?.text).isEqualTo("RTO E challan.apk")
        // MIME type is strictly in attachmentHint, never in text (§5.3)
        assertThat(payload?.attachmentHint).isEqualTo("application/vnd.android.package-archive")

        assertThat(inspector.queryCalled).isTrue()
        assertThat(inspector.openInputStreamCalled).isFalse()
    }

    @Test
    fun `shared octet-stream file inspects name and mime without opening stream`() {
        val uri = Uri.parse("content://com.android.providers.downloads.documents/document/5678")
        val fakeIntent = FakeSharedIntent(
            action = Intent.ACTION_SEND,
            type = "application/octet-stream",
            uri = uri
        )

        val inspector = FakeThrowingContentInspector(
            displayName = "RTO E challan.apk",
            mimeType = "application/octet-stream"
        )
        val payload = CheckMessageActivity.extractSharePayload(fakeIntent, inspector)

        assertThat(payload).isNotNull()
        assertThat(payload?.text).isEqualTo("RTO E challan.apk")
        assertThat(payload?.attachmentHint).isEqualTo("application/octet-stream")

        assertThat(inspector.queryCalled).isTrue()
        assertThat(inspector.openInputStreamCalled).isFalse()
    }

    @Test
    fun `unsupported mime type is rejected`() {
        val uri = Uri.parse("content://media/images/1")
        val fakeIntent = FakeSharedIntent(
            action = Intent.ACTION_SEND,
            type = "image/png",
            uri = uri
        )

        val inspector = FakeThrowingContentInspector()
        val payload = CheckMessageActivity.extractSharePayload(fakeIntent, inspector)

        assertThat(payload).isNull()
        assertThat(inspector.queryCalled).isFalse()
        assertThat(inspector.openInputStreamCalled).isFalse()
    }

    @Test
    fun `wildcard mime type is rejected`() {
        val uri = Uri.parse("content://media/files/1")
        val fakeIntent = FakeSharedIntent(
            action = Intent.ACTION_SEND,
            type = "*/*",
            uri = uri
        )

        val inspector = FakeThrowingContentInspector()
        val payload = CheckMessageActivity.extractSharePayload(fakeIntent, inspector)

        assertThat(payload).isNull()
        assertThat(inspector.queryCalled).isFalse()
        assertThat(inspector.openInputStreamCalled).isFalse()
    }

    @Test
    fun `process text action extracts selected text`() {
        val fakeIntent = FakeSharedIntent(
            action = Intent.ACTION_PROCESS_TEXT,
            type = "text/plain",
            text = "Selected suspicious text"
        )

        val inspector = FakeThrowingContentInspector()
        val payload = CheckMessageActivity.extractSharePayload(fakeIntent, inspector)

        assertThat(payload).isNotNull()
        assertThat(payload?.text).isEqualTo("Selected suspicious text")
        assertThat(payload?.attachmentHint).isNull()
        assertThat(inspector.openInputStreamCalled).isFalse()
    }
}
