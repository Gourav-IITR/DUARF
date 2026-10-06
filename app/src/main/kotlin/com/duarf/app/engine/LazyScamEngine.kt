package com.duarf.app.engine

import android.content.Context
import com.duarf.app.pack.AssetPackSource
import com.duarf.data.log.SafeLog
import com.duarf.engine.DefaultScamEngine
import com.duarf.engine.model.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe, lazy wrapper around [ScamEngine].
 *
 * Guarantees:
 * 1. Background Initialization: Asset loading and engine construction occur strictly off the
 *    main thread on [Dispatchers.IO], keeping app launch and [android.app.Application.onCreate]
 *    fast and non-blocking.
 * 2. Graceful Degradation: Any initialization failure (e.g. corrupt pack, I/O failure)
 *    is logged safely via [SafeLog] and degrades to a non-crashing fallback engine returning [AlertLevel.NONE].
 */
@Singleton
class LazyScamEngine @Inject constructor(
    private val context: Context
) : ScamEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engineRef = AtomicReference<ScamEngine?>(null)

    private val initJob: Job = scope.launch {
        val instance = try {
            val packSource = AssetPackSource(context)
            val engine = DefaultScamEngine.fromPackSource(packSource)
            if (engine.isModelLoaded) {
                SafeLog.event(SafeLog.EventCode.MODEL_LOADED, (engine.modelVersion ?: 1).toLong())
            } else {
                SafeLog.event(SafeLog.EventCode.MODEL_NOT_LOADED_RULES_ONLY)
            }
            engine
        } catch (_: Throwable) {
            SafeLog.event(SafeLog.EventCode.ERROR_ENGINE_INIT)
            SafeLog.event(SafeLog.EventCode.MODEL_NOT_LOADED_RULES_ONLY)
            createDegradedFallbackEngine()
        }
        engineRef.set(instance)
    }

    override val isModelLoaded: Boolean
        get() = getOrAwaitEngine().isModelLoaded

    override val modelVersion: Int?
        get() = getOrAwaitEngine().modelVersion

    override fun analyze(
        message: IncomingMessage,
        context: List<IncomingMessage>,
        sensitivity: Sensitivity
    ): Verdict {
        val engine = getOrAwaitEngine()
        return try {
            engine.analyze(message, context, sensitivity)
        } catch (_: Throwable) {
            SafeLog.event(SafeLog.EventCode.ERROR_ENGINE_ANALYSIS)
            createFallbackVerdict()
        }
    }

    private fun getOrAwaitEngine(): ScamEngine {
        val existing = engineRef.get()
        if (existing != null) return existing

        return runBlocking(Dispatchers.IO) {
            initJob.join()
            engineRef.get() ?: createDegradedFallbackEngine()
        }
    }

    private fun createDegradedFallbackEngine(): ScamEngine {
        return object : ScamEngine {
            override fun analyze(
                message: IncomingMessage,
                context: List<IncomingMessage>,
                sensitivity: Sensitivity
            ): Verdict = createFallbackVerdict()
        }
    }

    private fun createFallbackVerdict(): Verdict {
        return Verdict(
            level = AlertLevel.NONE,
            score = 0.0,
            ruleScore = 0.0,
            modelProbability = null,
            category = ScamCategory.OTHER_SUSPICIOUS,
            reasons = emptyList(),
            highlights = emptyList(),
            engineVersion = "1.0.0-fallback"
        )
    }
}
