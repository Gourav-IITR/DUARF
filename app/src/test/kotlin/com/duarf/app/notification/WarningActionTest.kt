// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.notification

import com.duarf.engine.model.Reason
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WarningActionTest {

    private fun reasons(vararg ids: String) = ids.map { Reason(it, "t_$it", "d_$it") }

    @Test
    fun `the first reason with an action decides`() {
        assertThat(WarningAction.forReasons(reasons("L03", "P02", "P01"))).isEqualTo(WarningAction.LINK)
        assertThat(WarningAction.forReasons(reasons("P02", "A01", "L05"))).isEqualTo(WarningAction.CODE)
        assertThat(WarningAction.forReasons(reasons("L01", "S01"))).isEqualTo(WarningAction.FILE)
        assertThat(WarningAction.forReasons(reasons("A04"))).isEqualTo(WarningAction.CODE)
        assertThat(WarningAction.forReasons(reasons("P03", "A03"))).isEqualTo(WarningAction.MONEY)
    }

    @Test
    fun `pressure-only or model-only reasons fall back to the generic action`() {
        assertThat(WarningAction.forReasons(reasons("P03", "P01", "S01"))).isEqualTo(WarningAction.GENERIC)
        assertThat(WarningAction.forReasons(emptyList())).isEqualTo(WarningAction.GENERIC)
    }
}
