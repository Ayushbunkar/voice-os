package com.voiceos.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Rolling latency metric for lightweight in-app performance tracking. */
data class PerfStat(
    val lastMs: Long = 0L,
    val avgMs: Long = 0L,
    val maxMs: Long = 0L,
    val count: Long = 0L
)

/**
 * Snapshot used by the debug performance panel.
 * All values are kept intentionally simple to avoid extra processing overhead.
 */
data class PerfSnapshot(
    val route: PerfStat = PerfStat(),
    val execute: PerfStat = PerfStat(),
    val whatsAppTotal: PerfStat = PerfStat(),
    val whatsAppStep: PerfStat = PerfStat(),
    val lastRouteInput: String = "",
    val lastRoutedCommand: String = "",
    val lastExecuteCommand: String = "",
    val lastExecuteStatus: String = "",
    val lastWhatsAppStep: String = "",
    val lastWhatsAppStepStatus: String = "",
    val accessibilityDebounceMs: Long = 0L,
    val whatsAppPollMs: Long = 0L,
    val updatedAtMs: Long = 0L
)

object PerfMetrics {

    private val TAG = "PerfMetrics"
    private val _snapshot = MutableStateFlow(PerfSnapshot())
    val snapshot: StateFlow<PerfSnapshot> = _snapshot.asStateFlow()

    fun recordRouteLatency(input: String, commandName: String, durationMs: Long) {
        _snapshot.update { current ->
            current.copy(
                route = current.route.push(durationMs),
                lastRouteInput = input.summarize(),
                lastRoutedCommand = commandName,
                updatedAtMs = System.currentTimeMillis()
            )
        }
        AppLogger.d(TAG, "route latency=${durationMs}ms command=$commandName")
    }

    fun recordExecuteLatency(commandName: String, durationMs: Long, status: String) {
        _snapshot.update { current ->
            current.copy(
                execute = current.execute.push(durationMs),
                lastExecuteCommand = commandName,
                lastExecuteStatus = status,
                updatedAtMs = System.currentTimeMillis()
            )
        }
        AppLogger.d(TAG, "execute latency=${durationMs}ms command=$commandName status=$status")
    }

    fun recordWhatsAppStep(stepName: String, durationMs: Long, success: Boolean) {
        _snapshot.update { current ->
            current.copy(
                whatsAppStep = current.whatsAppStep.push(durationMs),
                lastWhatsAppStep = stepName,
                lastWhatsAppStepStatus = if (success) "ok" else "fail",
                updatedAtMs = System.currentTimeMillis()
            )
        }
        AppLogger.d(TAG, "whatsapp step=$stepName latency=${durationMs}ms success=$success")
    }

    fun recordWhatsAppTotal(durationMs: Long, success: Boolean) {
        _snapshot.update { current ->
            current.copy(
                whatsAppTotal = current.whatsAppTotal.push(durationMs),
                lastWhatsAppStepStatus = if (success) "ok" else "fail",
                updatedAtMs = System.currentTimeMillis()
            )
        }
        AppLogger.d(TAG, "whatsapp total latency=${durationMs}ms success=$success")
    }

    fun recordTuning(accessibilityDebounceMs: Long? = null, whatsAppPollMs: Long? = null) {
        _snapshot.update { current ->
            current.copy(
                accessibilityDebounceMs = accessibilityDebounceMs ?: current.accessibilityDebounceMs,
                whatsAppPollMs = whatsAppPollMs ?: current.whatsAppPollMs,
                updatedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun PerfStat.push(sampleMs: Long): PerfStat {
        val sample = sampleMs.coerceAtLeast(0L)
        val nextCount = count + 1
        val nextAvg = if (count == 0L) sample else ((avgMs * count) + sample) / nextCount
        return copy(
            lastMs = sample,
            avgMs = nextAvg,
            maxMs = maxOf(maxMs, sample),
            count = nextCount
        )
    }

    private fun String.summarize(maxLen: Int = 42): String {
        val normalized = trim().replace("\n", " ")
        return if (normalized.length <= maxLen) normalized else normalized.take(maxLen - 3) + "..."
    }
}
