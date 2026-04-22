package com.voiceos.model

/**
 * Command — Expanded sealed class for Phase 2.
 *
 * New additions:
 *   • SendMessage  — AI-parsed WhatsApp/SMS send instruction
 *   • RunMacro     — Trigger a saved automation sequence
 *   • MultiStep    — Compound command split into ordered sub-commands
 *   • ScrollMore   — Context-aware "scroll more" (uses last remembered direction)
 *   • TypeText     — Type arbitrary text into focused field
 *   • PressKey     — Simulate keyboard special key
 */
sealed class Command {

    // ── Simple actions ──────────────────────────────────────────────────
    /** Tap clickable element with overlay number [index]. */
    data class Click(val index: Int) : Command()

    /** Scroll the current view in [direction]. */
    data class Scroll(val direction: ScrollDirection) : Command()

    /** Navigate backwards (system back). */
    object GoBack : Command()

    /** Launch an installed app by fuzzy name match. */
    data class OpenApp(val appName: String) : Command()

    // ── AI / complex actions ────────────────────────────────────────────
    /**
     * Send a message to [contact] via [app].
     * Drives the WhatsApp automation pipeline.
     */
    data class SendMessage(
        val contact: String,
        val message: String,
        val app: String = "whatsapp"
    ) : Command()

    /** Execute the macro whose name matches [macroName]. */
    data class RunMacro(val macroName: String) : Command()

    /**
     * Chain of commands to execute sequentially.
     * Built by [com.voiceos.ai.AICommandProcessor.parseMultiStep].
     */
    data class MultiStep(val steps: List<Command>) : Command()

    /** Context-aware scroll — uses [com.voiceos.memory.ContextManager.lastScrollDirection]. */
    object ScrollMore : Command()

    /** Type [text] into the currently focused input field. */
    data class TypeText(val text: String) : Command()

    /** Command could not be resolved. Raw text preserved for debugging. */
    data class Unknown(val rawText: String) : Command()

    // ── Enums ───────────────────────────────────────────────────────────
    enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
}
