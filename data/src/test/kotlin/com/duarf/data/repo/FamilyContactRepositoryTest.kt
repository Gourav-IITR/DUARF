// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.data.repo

import com.duarf.data.crypto.SoftwareCryptoEngine
import com.duarf.data.fakes.FakeUserPreferencesRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class FamilyContactRepositoryTest {

    private val preferences = FakeUserPreferencesRepository()
    private val repository = FamilyContactRepository(preferences, SoftwareCryptoEngine())

    @Test
    fun `contact round-trips and is never stored in clear`() = runBlocking {
        assertThat(repository.save("Son", "+91 98765 43210")).isTrue()

        assertThat(repository.current()).isEqualTo(FamilyContact("Son", "+919876543210"))
        val stored = preferences.userPreferencesFlow.first().familyContactCipher!!
        assertThat(stored).doesNotContain("98765")
        assertThat(stored).doesNotContain("Son")
    }

    @Test
    fun `invalid numbers are rejected`() = runBlocking {
        assertThat(repository.save("Son", "12345")).isFalse()
        assertThat(repository.current()).isNull()
    }

    @Test
    fun `clear removes the contact and unreadable data is ignored`() = runBlocking {
        repository.save("Son", "9876543210")
        repository.clear()
        assertThat(repository.current()).isNull()

        preferences.updateFamilyContactCipher("bm90LWEtY2lwaGVydGV4dA==")
        assertThat(repository.current()).isNull()
    }
}
