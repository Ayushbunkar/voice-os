package com.voiceos.commands

import android.content.Context
import com.voiceos.ai.AICommandProcessor
import com.voiceos.api.CloudSyncManager
import com.voiceos.automation.AutomationEngine
import com.voiceos.memory.ContextManager
import com.voiceos.model.Command
import com.voiceos.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * CommandRouter — The single entry point for ALL voice input in Phase 2.
 *
 * Routing logic:
 *   Score 0–3  → Simple rule-based [CommandParser]  (fast, no AI overhead)
 *   Score 4+   → [AICommandProcessor]               (NLP + multi-step)
 *   RunMacro   → [AutomationEngine]                 (macro execution)
 *
 * The router also updates [ContextManager] after each successful parse
 * so subsequent context-aware commands work correctly.
 */
class CommandRouter(private val context: Context) {

    companion object {
        private const val TAG = "CommandRouter"
        private const val AI_THRESHOLD = 4
    }

    private val contextManager = ContextManager.getInstance(context)
    private val cloudScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Route raw speech [input] to the appropriate parser and return
     * a fully resolved [Command].
     *
     * This is the ONLY function external callers (FloatingWidgetService) need.
     */
    fun route(input: String): Command {
        val trimmed = input.trim()
        AppLogger.i(TAG, "Routing: \"$trimmed\"")

        val score = AICommandProcessor.complexityScore(trimmed)
        AppLogger.d(TAG, "Complexity score: $score (threshold=$AI_THRESHOLD)")

        val command = if (score >= AI_THRESHOLD) {
            AppLogger.d(TAG, "→ Sending to AI processor")
            AICommandProcessor.process(trimmed, contextManager)
        } else {
            AppLogger.d(TAG, "→ Sending to rule-based parser")
            CommandParser.parse(trimmed)
        }

        AppLogger.i(TAG, "Routed to: $command")
        updateContext(command)
        syncToCloud(trimmed)
        return command
    }

    /** Update memory after routing so subsequent commands have context. */
    private fun updateContext(command: Command) {
        when (command) {
            is Command.Scroll      -> contextManager.rememberScroll(command.direction)
            is Command.OpenApp     -> contextManager.rememberApp(command.appName)
            is Command.SendMessage -> contextManager.rememberMessage(
                command.contact, command.message, command.app
            )
            else -> { /* no context update needed */ }
        }
    }

    private fun syncToCloud(input: String) {
        cloudScope.launch {
            val hints = mutableMapOf<String, String>()
            contextManager.lastApp?.let { hints["lastApp"] = it }
            contextManager.lastContact?.let { hints["lastContact"] = it }

            runCatching {
                CloudSyncManager.sendTextCommand(
                    input = input,
                    contextHints = if (hints.isEmpty()) null else hints
                )
            }.onFailure {
                AppLogger.w(TAG, "Cloud sync failed: ${it.message}")
            }
        }
    }
}
