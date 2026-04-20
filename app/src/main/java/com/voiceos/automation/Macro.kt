package com.voiceos.automation

import com.voiceos.model.Command

/**
 * Macro — Represents a saved automation sequence.
 *
 * A macro is a named list of [Command] steps executed in order.
 * Stored and loaded by [AutomationEngine] via JSON in SharedPreferences.
 *
 * @param id          Unique identifier (UUID or slug)
 * @param name        Human-readable trigger name (e.g. "good morning routine")
 * @param description Short description shown in the macros list UI
 * @param steps       Ordered list of commands to execute
 * @param delayMs     Pause between each step (default 1500ms to let UIs load)
 * @param isDefault   True for built-in macros that cannot be deleted
 */
data class Macro(
    val id: String,
    val name: String,
    val description: String,
    val steps: List<MacroStep>,
    val delayMs: Long = 1500L,
    val isDefault: Boolean = false
)

/**
 * MacroStep — A single serialisable step inside a [Macro].
 *
 * We use a flat structure (type + optional fields) rather than the sealed
 * [Command] hierarchy so it can easily be serialised to/from JSON without
 * an extra library.
 */
data class MacroStep(
    val type: String,          // "CLICK" | "SCROLL" | "OPEN_APP" | "SEND_MESSAGE" | "GO_BACK" | "TYPE_TEXT"
    val index: Int? = null,
    val direction: String? = null,   // "UP" | "DOWN"
    val appName: String? = null,
    val contact: String? = null,
    val message: String? = null,
    val text: String? = null
) {
    /** Convert this step to a typed [Command]. */
    fun toCommand(): Command = when (type.uppercase()) {
        "CLICK"        -> Command.Click(index ?: 1)
        "SCROLL"       -> Command.Scroll(
            if (direction?.uppercase() == "UP") Command.ScrollDirection.UP
            else Command.ScrollDirection.DOWN
        )
        "GO_BACK"      -> Command.GoBack
        "OPEN_APP"     -> Command.OpenApp(appName ?: "")
        "SEND_MESSAGE" -> Command.SendMessage(
            contact = contact ?: "",
            message = message ?: ""
        )
        "TYPE_TEXT"    -> Command.TypeText(text ?: "")
        else           -> Command.Unknown(type)
    }

    companion object {
        fun click(index: Int) = MacroStep("CLICK", index = index)
        fun scroll(direction: Command.ScrollDirection) =
            MacroStep("SCROLL", direction = direction.name)
        fun openApp(name: String) = MacroStep("OPEN_APP", appName = name)
        fun sendMessage(contact: String, message: String) =
            MacroStep("SEND_MESSAGE", contact = contact, message = message)
        fun goBack() = MacroStep("GO_BACK")
        fun typeText(text: String) = MacroStep("TYPE_TEXT", text = text)
    }
}
