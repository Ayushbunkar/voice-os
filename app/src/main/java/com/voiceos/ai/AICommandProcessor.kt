package com.voiceos.ai

import com.voiceos.commands.CommandParser
import com.voiceos.memory.ContextManager
import com.voiceos.model.Command
import com.voiceos.utils.AppLogger

/**
 * AICommandProcessor — Natural language → structured [Command] converter.
 *
 * Implements a three-layer approach:
 *   1. Exact/regex pattern matching for high-confidence intents
 *   2. Keyword-based heuristic scoring
 *   3. [parseWithAI] stub for future LLM (OpenAI / Gemini) integration
 *
 * The processor also handles context-aware commands using [ContextManager]:
 *   • "scroll more"  →  repeats last scroll direction
 *   • "send another" →  resends last message to last contact
 *   • "open it again"→  reopens last used app
 *
 * Multi-step commands ("open WhatsApp and send hi to Riya then scroll down")
 * are split by conjunctions and each part is parsed independently.
 */
object AICommandProcessor {

    private const val TAG = "AICommandProcessor"

    /**
     * Main entry point. Converts free-form [input] to a [Command].
     * Uses [context] for memory-based resolution.
     */
    fun process(input: String, context: ContextManager): Command {
        val lower = input.trim().lowercase()
        AppLogger.d(TAG, "AI processing: \"$lower\"")

        // ── 1. Context-aware shortcuts ─────────────────────────────────
        contextAwareCommand(lower, context)?.let { return it }

        // ── 2. Multi-step detection ("X and Y then Z") ─────────────────
        if (isMultiStep(lower)) {
            val steps = parseMultiStep(lower, context)
            if (steps.size > 1) {
                AppLogger.i(TAG, "Multi-step command with ${steps.size} parts")
                return Command.MultiStep(steps)
            }
        }

        // ── 3. Send message / WhatsApp intent ──────────────────────────
        parseSendMessage(lower)?.let { return it }

        // ── 4. Macro intent ────────────────────────────────────────────
        parseMacroIntent(lower)?.let { return it }

        // ── 5. Fallback to rule-based parser ──────────────────────────
        val fallback = CommandParser.parse(input)
        AppLogger.d(TAG, "AI fell through to rule parser: $fallback")
        return fallback
    }

    // ── Context-Aware Resolution ──────────────────────────────────────

    private fun contextAwareCommand(lower: String, ctx: ContextManager): Command? {
        // "scroll more" / "keep scrolling" / "continue scrolling"
        if (lower.contains("scroll more") || lower.contains("keep scrolling") ||
            lower.contains("more") && lower.length < 10) {
            val dir = ctx.lastScrollDirection ?: Command.ScrollDirection.DOWN
            AppLogger.i(TAG, "Context: ScrollMore → $dir")
            return Command.Scroll(dir)
        }

        // "send another message" / "send again" / "repeat"
        if (lower.contains("another message") || lower.contains("send again") ||
            lower == "repeat" || lower.contains("same message")) {
            val contact = ctx.lastContact
            val message = ctx.lastMessage
            if (contact != null && message != null) {
                AppLogger.i(TAG, "Context: resend to $contact")
                return Command.SendMessage(contact, message)
            }
        }

        // "open it again" / "reopen"
        if (lower.contains("open it again") || lower.contains("reopen") ||
            lower.contains("open again")) {
            val app = ctx.lastApp
            if (app != null) {
                AppLogger.i(TAG, "Context: reopen $app")
                return Command.OpenApp(app)
            }
        }

        return null
    }

    // ── Send Message Parser ───────────────────────────────────────────

    private fun parseSendMessage(lower: String): Command? {
        // Pattern 1: "send [message] to [contact]"
        val p1 = Regex("""(?:send|tell|text)\s+(.+?)\s+to\s+(\w[\w\s]{0,30}?)(?:\s+on\s+\w+)?$""")
        p1.find(lower)?.let { m ->
            val msg = m.groupValues[1].trim()
            val contact = m.groupValues[2].trim().capitalise()
            AppLogger.i(TAG, "Parsed SendMessage: to=$contact msg=$msg")
            return Command.SendMessage(contact, msg)
        }

        // Pattern 2: "message [contact] [text]"
        val p2 = Regex("""(?:message|whatsapp|msg)\s+(\w+)\s+(.+)$""")
        p2.find(lower)?.let { m ->
            val contact = m.groupValues[1].capitalise()
            val msg = m.groupValues[2].trim()
            AppLogger.i(TAG, "Parsed SendMessage (p2): to=$contact msg=$msg")
            return Command.SendMessage(contact, msg)
        }

        // Pattern 3: "[contact] को [message] भेजो" — Hindi support stub
        // (extend here for multi-language)

        return null
    }

    // ── Multi-Step Parser ─────────────────────────────────────────────

    private fun isMultiStep(lower: String): Boolean {
        val conjunctions = listOf(" and ", " then ", " after that ", " next ", " also ")
        return conjunctions.any { lower.contains(it) }
    }

    fun parseMultiStep(lower: String, context: ContextManager): List<Command> {
        val splitRegex = Regex("""\s+(?:and|then|after that|next|also)\s+""")
        val parts = splitRegex.split(lower).filter { it.isNotBlank() }
        AppLogger.d(TAG, "Multi-step parts: $parts")
        return parts.mapNotNull { part ->
            val cmd = process(part, context)
            if (cmd is Command.Unknown) null else cmd
        }
    }

    // ── Macro Intent ──────────────────────────────────────────────────

    private fun parseMacroIntent(lower: String): Command.RunMacro? {
        val macroKeywords = listOf(
            "routine", "macro", "sequence", "mode", "workflow",
            "morning", "evening", "lunch", "good morning", "good night"
        )
        if (macroKeywords.any { lower.contains(it) }) {
            AppLogger.i(TAG, "Detected macro intent: \"$lower\"")
            return Command.RunMacro(lower)
        }
        return null
    }

    // ── Complexity Heuristic ──────────────────────────────────────────

    /**
     * Returns a complexity score 0–10.
     * Score >= 4 means the command should be sent to the AI processor
     * rather than the simple rule-based parser.
     */
    fun complexityScore(input: String): Int {
        val lower = input.lowercase()
        var score = 0
        if (lower.split(" ").size > 5) score += 2           // long input
        if (lower.contains(" to ")) score += 2              // relational phrasing
        if (lower.contains(" and ") || lower.contains(" then ")) score += 3
        if (lower.contains("send") || lower.contains("message")) score += 2
        if (lower.contains("routine") || lower.contains("macro")) score += 3
        return score.coerceAtMost(10)
    }

    // ── Future LLM integration ────────────────────────────────────────

    /**
     * Placeholder for OpenAI / Gemini Flash API call.
     *
     * To activate:
     *   1. Add OkHttp to build.gradle.kts
     *   2. Replace TODO body with your HTTP request
     *   3. Parse JSON response into a Command
     *
     * The function is suspend so it can be called from a coroutine without
     * blocking the main thread.
     */
    suspend fun processWithAI(prompt: String, apiKey: String): Command {
        AppLogger.d(TAG, "processWithAI called (stub) with prompt: $prompt")
        // TODO: Replace with real API call
        // val response = openAiClient.chat(
        //     systemPrompt = SYSTEM_PROMPT,
        //     userPrompt = prompt,
        //     apiKey = apiKey
        // )
        // return parseAiJsonResponse(response)
        return Command.Unknown(prompt) // remove this line after integration
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun String.capitalise() =
        split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}
