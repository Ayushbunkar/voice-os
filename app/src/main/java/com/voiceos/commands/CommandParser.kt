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
 *   "scroll down"  /  "scroll up"  /  "slide left"  /  "slide right"
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

        // ── Scroll / Swipe / Slide ───────────────────────────────────────
        parseScrollDirection(text)?.let { direction ->
            return Command.Scroll(direction).also {
                AppLogger.i(TAG, "Matched Scroll($direction)")
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

    private fun parseScrollDirection(text: String): Command.ScrollDirection? {
        return when {
            matchesDirectionalScroll(text, "down") -> Command.ScrollDirection.DOWN
            matchesDirectionalScroll(text, "up") -> Command.ScrollDirection.UP
            matchesDirectionalScroll(text, "left") -> Command.ScrollDirection.LEFT
            matchesDirectionalScroll(text, "right") -> Command.ScrollDirection.RIGHT
            else -> null
        }
    }

    private fun matchesDirectionalScroll(text: String, direction: String): Boolean {
        if (text == direction) return true
        val regex = Regex("""\b(scroll|swipe|slide)\s+$direction\b""")
        return regex.containsMatchIn(text)
    }
}
