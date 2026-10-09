// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrandDisplayNameTest {

    @Test
    fun `acronyms are capitalised and names keep their own casing`() {
        assertThat(brandDisplayName("sbi")).isEqualTo("SBI")
        assertThat(brandDisplayName("msedcl")).isEqualTo("MSEDCL")
        assertThat(brandDisplayName("phonepe")).isEqualTo("PhonePe")
        assertThat(brandDisplayName("bank of baroda")).isEqualTo("Bank of Baroda")
        assertThat(brandDisplayName("google pay")).isEqualTo("Google Pay")
        assertThat(brandDisplayName("एसबीआई")).isEqualTo("एसबीआई")
    }
}
