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
     */
    fun parseNaturalLanguage(rawText: String): Command {
        val text = rawText.trim().lowercase()
        AppLogger.d(TAG, "AI parsing: \"$text\"")

        // ── 1. Smart "Open X and Click Y" ─────────────────────────────
        val openAndClickRegex = Regex("""^(?:open|launch|start)\s+(.+?)\s+(?:and|then)\s+(?:click|tap|press)\s+(?:the\s+)?(?:button\s+)?(?:number\s+)?(\d+)$""")
        openAndClickRegex.find(text)?.let { m ->
            val app = m.groupValues[1].trim()
            val index = m.groupValues[2].toIntOrNull() ?: 1
            AppLogger.i(TAG, "AI inferred MultiStep: Open($app) -> Click($index)")
            return Command.MultiStep(listOf(
                Command.OpenApp(app),
                Command.Click(index)
            ))
        }

        // ── 2. Click specific numbers directly ────────────────────────
        val directClickRegex = Regex("""^(?:click|tap|press|select)\s+(?:number\s+)?(\d+)$""")
        directClickRegex.find(text)?.let { m ->
            val index = m.groupValues[1].toIntOrNull() ?: 1
            AppLogger.i(TAG, "AI inferred Click($index)")
            return Command.Click(index)
        }

        // ── 3. Open apps directly ─────────────────────────────────────
        val openKeywords = listOf("open", "launch", "start", "go to", "navigate to", "show")
        val openKeyword = openKeywords.firstOrNull { text.startsWith(it) }
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

    private fun inferScrollDirection(text: String): Command.ScrollDirection {
        return when {
            hasWord(text, "left") -> Command.ScrollDirection.LEFT
            hasWord(text, "right") -> Command.ScrollDirection.RIGHT
            hasWord(text, "up") || hasWord(text, "top") -> Command.ScrollDirection.UP
            else -> Command.ScrollDirection.DOWN
        }
    }

    private fun hasWord(text: String, word: String): Boolean {
        return Regex("""\b$word\b""").containsMatchIn(text)
    }

    /*
     * ─── TODO: Real API integration ──────────────────────────────────────
     *
     * suspend fun parseWithOpenAI(rawText: String, apiKey: String): Command {
     *     val client = OkHttpClient()
     *     val prompt = """
     *         You are a voice command parser. Convert the user's speech into JSON:
    *         {"action":"click|scroll|goBack|openApp","index":N,"direction":"up|down|left|right","appName":"..."}
     *         Speech: "$rawText"
     *     """.trimIndent()
     *     // ... build request, call API, parse JSON ...
     * }
     */
}
