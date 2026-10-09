// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.locale

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class AppLanguageTest {

    private val resDir = File("src/main/res").let { if (it.exists()) it else File("app/src/main/res") }

    @Test
    fun `English is the default and listed first`() {
        assertThat(AppLanguage.DEFAULT_TAG).isEqualTo("en")
        assertThat(AppLanguage.SUPPORTED.first().tag).isEqualTo("en")
        assertThat(AppLanguage.find("not-a-language").tag).isEqualTo("en")
    }

    @Test
    fun `regional and unknown tags map onto supported languages`() {
        assertThat(AppLanguage.normalize("hi-IN")).isEqualTo("hi")
        assertThat(AppLanguage.normalize("hi-Latn-IN")).isEqualTo("hi-Latn")
        assertThat(AppLanguage.normalize("pa-Guru-IN")).isEqualTo("pa")
        assertThat(AppLanguage.normalize("en-US")).isEqualTo("en")
        assertThat(AppLanguage.normalize("fr-FR")).isEqualTo("en")
    }

    @Test
    fun `every supported language has strings and is declared for Android 13+`() {
        val folders = resDir.listFiles()!!
            .filter { it.isDirectory && File(it, "strings.xml").exists() }
            .map { it.name }
            .toSet()
        val expectedFolders = AppLanguage.SUPPORTED.map { lang ->
            when {
                lang.tag == "en" -> "values"
                lang.tag.contains('-') -> "values-b+" + lang.tag.replace('-', '+')
                else -> "values-${lang.tag}"
            }
        }.toSet()
        assertThat(folders).isEqualTo(expectedFolders)

        val declared = Regex("""android:name="([^"]+)"""")
            .findAll(File(resDir, "xml/locales_config.xml").readText())
            .map { it.groupValues[1] }
            .toList()
        assertThat(declared).containsExactlyElementsIn(AppLanguage.SUPPORTED.map { it.tag })
    }
}
