package com.voiceos.commands

import com.voiceos.model.Command
import com.voiceos.utils.AppLogger

/**
 * CommandParser — Converts raw speech text into a typed [Command].
 *
 * Rule-based parsing handles the most common phrases. Natural language
 * input falls through to the [AiCommandParser] for more flexible matching.
 *
 * Supported phrases (examples):
 *   "click 3"  /  "tap 5"  /  "press 1"
 *   "scroll down"  /  "scroll up"
 *   "go back"  /  "back"
 *   "open youtube"  /  "launch whatsapp"
 */
object CommandParser {

    private const val TAG = "CommandParser"

    fun parse(rawText: String): Command {
        val text = rawText.trim().lowercase()
        AppLogger.d(TAG, "Parsing: \"$text\"")

        // ── Click / Tap / Press ──────────────────────────────────────────
        val clickRegex = Regex("""^(click|tap|press)\s+(\d+)$""")
        clickRegex.matchEntire(text)?.let { match ->
            val index = match.groupValues[2].toIntOrNull() ?: return Command.Unknown(rawText)
            AppLogger.i(TAG, "Matched Click($index)")
            return Command.Click(index)
        }

        // ── Scroll ────────────────────────────────────────────────────────
        when {
            text.contains("scroll down") || text == "down" ->
                return Command.Scroll(Command.ScrollDirection.DOWN).also {
                    AppLogger.i(TAG, "Matched Scroll(DOWN)")
                }
            text.contains("scroll up") || text == "up" ->
                return Command.Scroll(Command.ScrollDirection.UP).also {
                    AppLogger.i(TAG, "Matched Scroll(UP)")
                }
        }

        // ── Go Back ───────────────────────────────────────────────────────
        if (text == "go back" || text == "back" || text == "navigate back") {
            AppLogger.i(TAG, "Matched GoBack")
            return Command.GoBack
        }

        // ── Open / Launch App ─────────────────────────────────────────────
        val openRegex = Regex("""^(open|launch|start)\s+(.+)$""")
        openRegex.matchEntire(text)?.let { match ->
            val appName = match.groupValues[2].trim()
            AppLogger.i(TAG, "Matched OpenApp($appName)")
            return Command.OpenApp(appName)
        }

        // ── Delegate to AI parser for natural language ────────────────────
        AppLogger.d(TAG, "Rule-based parse failed — delegating to AiCommandParser")
        return AiCommandParser.parseNaturalLanguage(rawText)
    }
}
