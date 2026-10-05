package com.duarf.engine.ml

import com.duarf.engine.model.TextSpan
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.exp

data class ModelPrediction(
    val probability: Double,
    val mPrime: Double,
    val highlights: List<TextSpan>
)

class LinearClassifier(
    val formatVersion: Int,
    val featurizerVersion: Int,
    val log2Buckets: Int,
    val scale: Float,
    val bias: Float,
    val calibA: Float,
    val calibB: Float,
    private val weights: ByteArray
) {
    fun predict(featurized: FeaturizedMessage): ModelPrediction {
        // Raw linear logit: z = bias + scale * sum(w[i] * x[i])
        var weightSum = 0.0
        for (idx in featurized.activeIndices) {
            if (idx in weights.indices) {
                weightSum += weights[idx].toDouble()
            }
        }

        val z = bias.toDouble() + (scale.toDouble() * featurized.l2Value * weightSum)

        // Platt calibration: p = 1 / (1 + exp(-(A * z + B)))
        val exponent = -(calibA.toDouble() * z + calibB.toDouble())
        val p = 1.0 / (1.0 + exp(exponent))

        // m' formula (§10): m' = 0.8 * clamp((p - 0.5) / 0.5, 0, 1)
        val clamped = ((p - 0.5) / 0.5).coerceIn(0.0, 1.0)
        val mPrime = 0.8 * clamped

        // Attributions (§11 item 4): at most 5 token spans with highest positive attribution only when m' > 0.2
        val highlights = ArrayList<TextSpan>()
        if (mPrime > 0.2 && featurized.tokenAttributions.isNotEmpty()) {
            val scoredTokens = ArrayList<Pair<TokenFeatureMapping, Double>>()
            for (mapping in featurized.tokenAttributions) {
                var tokenScore = 0.0
                for (bIdx in mapping.bucketIndices) {
                    if (bIdx in weights.indices) {
                        tokenScore += weights[bIdx].toDouble()
                    }
                }
                if (tokenScore > 0.0 && mapping.span.start < mapping.span.end) {
                    scoredTokens.add(Pair(mapping, tokenScore))
                }
            }

            scoredTokens.sortByDescending { it.second }
            val top5 = scoredTokens.take(5)
            for ((tok, _) in top5) {
                highlights.add(tok.span)
            }
        }

        return ModelPrediction(
            probability = p,
            mPrime = mPrime,
            highlights = highlights
        )
    }

    companion object {
        const val HEADER_BASE_SIZE = 28 // 4 + 2 + 2 + 1 + 3 + 4 + 4 + 4 + 4
        const val CRC_SIZE = 4
        private val MAGIC = byteArrayOf('P'.code.toByte(), 'H'.code.toByte(), 'R'.code.toByte(), 'D'.code.toByte())

        fun fromBytes(bytes: ByteArray, expectedFeaturizerVersion: Int = 1): LinearClassifier {
            if (bytes.size < HEADER_BASE_SIZE + CRC_SIZE) {
                throw IllegalArgumentException("Model file too small: ${bytes.size} bytes")
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Magic check
            val magic = ByteArray(4)
            buffer.get(magic)
            if (!magic.contentEquals(MAGIC)) {
                throw IllegalArgumentException("Invalid magic bytes in model file")
            }

            val formatVer = buffer.short.toInt() and 0xFFFF
            val featurizerVer = buffer.short.toInt() and 0xFFFF
            if (featurizerVer != expectedFeaturizerVersion) {
                throw IllegalArgumentException("Featurizer version mismatch: expected $expectedFeaturizerVersion but got $featurizerVer")
            }

            val log2Buckets = buffer.get().toInt() and 0xFF
            // 3 reserved bytes
            buffer.get()
            buffer.get()
            buffer.get()

            val scale = buffer.float
            val bias = buffer.float
            val calibA = buffer.float
            val calibB = buffer.float

            // Expected size derived from header
            val expectedWeightsCount = 1 shl log2Buckets
            val expectedTotalSize = HEADER_BASE_SIZE + expectedWeightsCount + CRC_SIZE
            if (bytes.size != expectedTotalSize) {
                throw IllegalArgumentException("Model file size mismatch: derived from header=$expectedTotalSize, actual=${bytes.size}")
            }

            val weights = ByteArray(expectedWeightsCount)
            buffer.get(weights)

            val storedCrc = buffer.int.toLong() and 0xFFFFFFFFL

            // Verify CRC32
            val crc = CRC32()
            crc.update(bytes, 0, HEADER_BASE_SIZE + expectedWeightsCount)
            val computedCrc = crc.value

            if (storedCrc != computedCrc) {
                throw IllegalArgumentException("CRC32 mismatch: expected $storedCrc but got $computedCrc")
            }

            return LinearClassifier(
                formatVersion = formatVer,
                featurizerVersion = featurizerVer,
                log2Buckets = log2Buckets,
                scale = scale,
                bias = bias,
                calibA = calibA,
                calibB = calibB,
                weights = weights
            )
        }
    }
}
