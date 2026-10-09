// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.ml

import kotlinx.serialization.Serializable

@Serializable
data class GoldenVectorEntry(
    val id: String,
    val text: String,
    val senderKind: String,
    val isGroup: Boolean,
    val activeIndices: List<Int>,
    val l2Value: Double,
    val expectedLevel: String,
    val expectedScore: Double
)
