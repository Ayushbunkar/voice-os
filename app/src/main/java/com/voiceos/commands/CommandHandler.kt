package com.voiceos.commands

import android.content.Context
import android.os.SystemClock
import com.voiceos.automation.AutomationEngine
import com.voiceos.automation.WhatsAppAutomation
import com.voiceos.memory.ContextManager
import com.voiceos.model.Command
import com.voiceos.service.VoiceAccessibilityService
import com.voiceos.utils.AppLogger
import com.voiceos.utils.AppUtils
import com.voiceos.utils.PerfMetrics
import com.voiceos.utils.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * CommandHandler — Phase 2 execution layer.
 *
 * Routes every typed [Command] to the correct subsystem and provides
 * text-to-speech feedback after each action.
 *
 * New in Phase 2:
 *   • SendMessage  → WhatsAppAutomation pipeline
 *   • RunMacro     → AutomationEngine
 *   • MultiStep    → executes each sub-command sequentially
 *   • ScrollMore   → resolves from ContextManager
 *   • TypeText     → types into focused accessibility node
 *   • TTS feedback on every action
 */
class CommandHandler(private val context: Context) {

    companion object {
        private const val TAG = "CommandHandler"
    }

    private val tts = TtsManager.getInstance(context)
    private val automationEngine = AutomationEngine.getInstance(context)
    private val contextManager = ContextManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Execute [command].
     * Long-running operations (WhatsApp automation, macros) are launched
     * on a background coroutine; simple actions run inline.
     */
    fun execute(command: Command) {
        val startedAt = SystemClock.elapsedRealtime()
        val commandName = command::class.simpleName ?: "Unknown"

        AppLogger.i(TAG, "Executing: $command")

        val service = VoiceAccessibilityService.instance

        when (command) {

            // ── Simple accessibility actions ──────────────────────────
            is Command.Click -> {
                if (service == null) {
                    warnService()
                    recordExecutionOutcome(commandName, startedAt, "service_missing")
                    return
                }
                val success = service.clickElementAtIndex(command.index)
                if (success) {
                    tts.speak("Clicking button ${command.index}")
                    recordExecutionOutcome(commandName, startedAt, "ok")
                } else {
                    tts.speak("Element ${command.index} not found")
                    AppUtils.showToast(context, "No element #${command.index}")
                    recordExecutionOutcome(commandName, startedAt, "not_found")
                }
            }

            is Command.Scroll -> {
                if (service == null) {
                    warnService()
                    recordExecutionOutcome(commandName, startedAt, "service_missing")
                    return
                }
                service.performScroll(command.direction)
                contextManager.rememberScroll(command.direction)
                val dirText = if (command.direction == Command.ScrollDirection.DOWN) "down" else "up"
                tts.speak("Scrolling $dirText")
                recordExecutionOutcome(commandName, startedAt, "ok")
            }

            is Command.GoBack -> {
                if (service == null) {
                    warnService()
                    recordExecutionOutcome(commandName, startedAt, "service_missing")
                    return
                }
                service.performBack()
                tts.speak("Going back")
                recordExecutionOutcome(commandName, startedAt, "ok")
            }

            is Command.OpenApp -> {
                val launched = AppUtils.launchApp(context, command.appName)
                if (launched) {
                    contextManager.rememberApp(command.appName)
                    tts.speak("Opening ${command.appName}")
                    recordExecutionOutcome(commandName, startedAt, "ok")
                } else {
                    tts.speak("Could not find ${command.appName}")
                    recordExecutionOutcome(commandName, startedAt, "launch_failed")
                }
            }

            // ── Phase 2: WhatsApp / Send Message ─────────────────────
            is Command.SendMessage -> {
                tts.speak("Sending message to ${command.contact}")
                scope.launch(Dispatchers.IO) {
                    val success = WhatsAppAutomation.sendMessage(
                        contactName = command.contact,
                        message = command.message,
                        context = context
                    )
                    launch(Dispatchers.Main) {
                        if (success) {
                            contextManager.rememberMessage(command.contact, command.message, command.app)
                            tts.speak("Message sent to ${command.contact}")
                            recordExecutionOutcome(commandName, startedAt, "ok")
                        } else {
                            tts.speak("Failed to send message. Please try again.")
                            AppUtils.showToast(context, "Message send failed")
                            recordExecutionOutcome(commandName, startedAt, "send_failed")
                        }
                    }
                }
            }

            // ── Phase 2: Macro execution ──────────────────────────────
            is Command.RunMacro -> {
                tts.speak("Running ${command.macroName}")
                scope.launch {
                    val found = automationEngine.executeMacro(command.macroName) { step ->
                        // Execute each macro step through this handler recursively
                        execute(step)
                        // Small extra pause between macro steps to avoid racing
                        kotlinx.coroutines.delay(200L)
                    }
                    if (!found) {
                        launch(Dispatchers.Main) {
                            tts.speak("Macro not found: ${command.macroName}")
                            AppUtils.showToast(context, "No macro: \"${command.macroName}\"")
                            recordExecutionOutcome(commandName, startedAt, "macro_not_found")
                        }
                    } else {
                        launch(Dispatchers.Main) {
                            tts.speak("Automation complete")
                            recordExecutionOutcome(commandName, startedAt, "ok")
                        }
                    }
                }
            }

            // ── Phase 2: Multi-step compound command ──────────────────
            is Command.MultiStep -> {
                tts.speak("Running ${command.steps.size} steps")
                scope.launch {
                    command.steps.forEachIndexed { i, step ->
                        AppLogger.d(TAG, "MultiStep ${i + 1}/${command.steps.size}: $step")
                        execute(step)
                        if (i < command.steps.lastIndex) {
                            kotlinx.coroutines.delay(350L)
                        }
                    }
                    tts.speak("All steps complete")
                    recordExecutionOutcome(commandName, startedAt, "ok")
                }
            }

            // ── Phase 2: Context-aware scroll more ────────────────────
            is Command.ScrollMore -> {
                val dir = contextManager.lastScrollDirection ?: Command.ScrollDirection.DOWN
                execute(Command.Scroll(dir))
                recordExecutionOutcome(commandName, startedAt, "ok")
            }

            // ── Phase 2: Type text ────────────────────────────────────
            is Command.TypeText -> {
                if (service == null) {
                    warnService()
                    recordExecutionOutcome(commandName, startedAt, "service_missing")
                    return
                }
                val typed = service.typeTextIntoFocused(command.text)
                if (typed) {
                    tts.speak("Typed: ${command.text}")
                    recordExecutionOutcome(commandName, startedAt, "ok")
                } else {
                    tts.speak("Could not type text")
                    recordExecutionOutcome(commandName, startedAt, "type_failed")
                }
            }

            // ── Unknown ───────────────────────────────────────────────
            is Command.Unknown -> {
                AppLogger.w(TAG, "Unknown command: ${command.rawText}")
                tts.speak("Sorry, I didn't understand that")
                AppUtils.showToast(context, "Unknown: \"${command.rawText}\"")
                recordExecutionOutcome(commandName, startedAt, "unknown")
            }
        }
    }

    private fun recordExecutionOutcome(commandName: String, startedAt: Long, status: String) {
        val latencyMs = SystemClock.elapsedRealtime() - startedAt
        PerfMetrics.recordExecuteLatency(commandName, latencyMs, status)
        AppLogger.i(TAG, "Execution latency=${latencyMs}ms command=$commandName status=$status")
    }

    private fun warnService() {
        AppLogger.e(TAG, "AccessibilityService is not running!")
        tts.speak("Please enable VoiceOS in Accessibility Settings")
        AppUtils.showToast(context, "Enable VoiceOS in Accessibility Settings first")
    }
}
