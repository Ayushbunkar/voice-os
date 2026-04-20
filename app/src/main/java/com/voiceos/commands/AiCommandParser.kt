package com.voiceos.commands

import com.voiceos.model.Command
import com.voiceos.utils.AppLogger

/**
 * AiCommandParser — Placeholder for AI/NLP-based intent parsing.
 *
 * Currently implements a best-effort heuristic parser. When you are
 * ready to integrate an LLM (e.g., OpenAI GPT or Gemini), replace the
 * body of [parseNaturalLanguage] with an API call and keep the signature.
 *
 * Future integration steps:
 *   1. Add OkHttp / Retrofit dependency.
 *   2. POST rawText to your LLM endpoint.
 *   3. Deserialise the JSON intent response into a [Command].
 */
object AiCommandParser {

    private const val TAG = "AiCommandParser"

    /**
     * Parses a free-form natural language command into a [Command].
     *
     * Current implementation — heuristic keyword scanning.
     * Replace with an actual API call for production NLP quality.
     */
    fun parseNaturalLanguage(rawText: String): Command {
        val text = rawText.trim().lowercase()
        AppLogger.d(TAG, "AI parsing: \"$text\"")

        // ── Heuristic: contains a number + action verb ────────────────────
        val numberInText = Regex("""\b(\d+)\b""").find(text)?.groupValues?.get(1)?.toIntOrNull()

        val hasClickIntent = listOf("click", "tap", "press", "select", "choose", "hit")
            .any { text.contains(it) }
        if (hasClickIntent && numberInText != null) {
            AppLogger.i(TAG, "AI inferred Click($numberInText)")
            return Command.Click(numberInText)
        }

        // ── Heuristic: scroll intent ──────────────────────────────────────
        if (text.contains("scroll") || text.contains("swipe")) {
            val dir = if (text.contains("up") || text.contains("top"))
                Command.ScrollDirection.UP else Command.ScrollDirection.DOWN
            AppLogger.i(TAG, "AI inferred Scroll($dir)")
            return Command.Scroll(dir)
        }

        // ── Heuristic: back navigation ────────────────────────────────────
        if (text.contains("back") || text.contains("previous") || text.contains("return")) {
            AppLogger.i(TAG, "AI inferred GoBack")
            return Command.GoBack
        }

        // ── Heuristic: app open intent ────────────────────────────────────
        val openKeywords = listOf("open", "launch", "start", "go to", "navigate to", "show")
        val openKeyword = openKeywords.firstOrNull { text.contains(it) }
        if (openKeyword != null) {
            val appName = text.substringAfter(openKeyword).trim()
            if (appName.isNotEmpty()) {
                AppLogger.i(TAG, "AI inferred OpenApp($appName)")
                return Command.OpenApp(appName)
            }
        }

        // ── Unknown ───────────────────────────────────────────────────────
        AppLogger.w(TAG, "AI could not parse: \"$rawText\"")
        return Command.Unknown(rawText)
    }

    /*
     * ─── TODO: Real API integration ──────────────────────────────────────
     *
     * suspend fun parseWithOpenAI(rawText: String, apiKey: String): Command {
     *     val client = OkHttpClient()
     *     val prompt = """
     *         You are a voice command parser. Convert the user's speech into JSON:
     *         {"action":"click|scroll|goBack|openApp","index":N,"direction":"up|down","appName":"..."}
     *         Speech: "$rawText"
     *     """.trimIndent()
     *     // ... build request, call API, parse JSON ...
     * }
     */
}
