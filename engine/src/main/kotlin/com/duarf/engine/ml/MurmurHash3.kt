// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.engine.ml

import java.nio.charset.StandardCharsets

object MurmurHash3 {

    private const val C1 = 0xcc9e2d51.toInt()
    private const val C2 = 0x1b873593.toInt()

    fun hash32(data: ByteArray, seed: Int = 0): Int {
        var h = seed
        val length = data.size
        val nblocks = length / 4

        for (i in 0 until nblocks) {
            val i4 = i * 4
            var k = (data[i4].toInt() and 0xFF) or
                    ((data[i4 + 1].toInt() and 0xFF) shl 8) or
                    ((data[i4 + 2].toInt() and 0xFF) shl 16) or
                    ((data[i4 + 3].toInt() and 0xFF) shl 24)

            k *= C1
            k = Integer.rotateLeft(k, 15)
            k *= C2

            h = h xor k
            h = Integer.rotateLeft(h, 13)
            h = h * 5 + 0xe6546b64.toInt()
        }

        val tailStart = nblocks * 4
        var k1 = 0
        val remaining = length - tailStart

        if (remaining == 3) {
            k1 = k1 xor ((data[tailStart + 2].toInt() and 0xFF) shl 16)
        }
        if (remaining >= 2) {
            k1 = k1 xor ((data[tailStart + 1].toInt() and 0xFF) shl 8)
        }
        if (remaining >= 1) {
            k1 = k1 xor (data[tailStart].toInt() and 0xFF)
            k1 *= C1
            k1 = Integer.rotateLeft(k1, 15)
            k1 *= C2
            h = h xor k1
        }

        h = h xor length
        h = h xor (h ushr 16)
        h *= 0x85ebca6b.toInt()
        h = h xor (h ushr 13)
        h *= 0xc2b2ae35.toInt()
        h = h xor (h ushr 16)

        return h
    }

    fun hash32(str: String, seed: Int = 0): Int {
        return hash32(str.toByteArray(StandardCharsets.UTF_8), seed)
    }
}
