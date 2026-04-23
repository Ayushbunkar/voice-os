package com.voiceos.automation

import android.content.Context
import android.content.SharedPreferences
import com.voiceos.model.Command
import com.voiceos.utils.AppLogger
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * AutomationEngine — Loads, saves, and executes named [Macro] sequences.
 *
 * Macros are stored in SharedPreferences as JSON arrays so no extra
 * database dependency is required.
 *
 * Built-in default macros are always present and cannot be deleted.
 *
 * Usage:
 *   val engine = AutomationEngine.getInstance(context)
 *   engine.executeMacro("good morning") { command -> handler.execute(command) }
 */
class AutomationEngine private constructor(context: Context) {

    private val TAG = "AutomationEngine"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("voiceos_macros", Context.MODE_PRIVATE)
    private val cacheLock = Any()

    @Volatile
    private var cachedUserMacros: List<Macro>? = null

    companion object {
        private const val KEY_MACROS = "user_macros"

        @Volatile
        private var INSTANCE: AutomationEngine? = null

        fun getInstance(context: Context): AutomationEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutomationEngine(context.applicationContext).also { INSTANCE = it }
            }

        // ── Default built-in macros ─────────────────────────────────────
        val DEFAULT_MACROS = listOf(
            Macro(
                id = "morning_routine",
                name = "good morning routine",
                description = "Opens WhatsApp and sends GM to saved contacts",
                steps = listOf(
                    MacroStep.openApp("whatsapp"),
                    MacroStep.sendMessage("Boss", "Good morning! On my way."),
                    MacroStep.goBack(),
                    MacroStep.openApp("youtube")
                ),
                delayMs = 2000L,
                isDefault = true
            ),
            Macro(
                id = "message_riya",
                name = "message riya",
                description = "Quickly send 'Hey' to Riya on WhatsApp",
                steps = listOf(
                    MacroStep.sendMessage("Riya", "Hey, what's up?")
                ),
                delayMs = 1500L,
                isDefault = true
            ),
            Macro(
                id = "quick_share",
                name = "quick share",
                description = "Opens WhatsApp to share something quickly",
                steps = listOf(
                    MacroStep.openApp("whatsapp")
                ),
                delayMs = 1500L,
                isDefault = true
            ),
            Macro(
                id = "go_home",
                name = "go home",
                description = "Goes back to the home screen",
                steps = listOf(
                    MacroStep.goBack(),
                    MacroStep.goBack(),
                    MacroStep.openApp("launcher")
                ),
                delayMs = 500L,
                isDefault = true
            )
        )
    }

    // ── Public API ────────────────────────────────────────────────────

    /** Returns all macros: defaults + user-saved. */
    fun getAllMacros(): List<Macro> = DEFAULT_MACROS + loadUserMacros()

    /**
     * Find a macro whose name fuzzy-matches [triggerName].
     * Returns null if no match found.
     */
    fun findMacro(triggerName: String): Macro? {
        val lower = triggerName.lowercase().trim()
        return getAllMacros().firstOrNull { macro ->
            macro.name.lowercase().contains(lower) ||
                    lower.contains(macro.name.lowercase()) ||
                    macro.name.lowercase().split(" ").any { lower.contains(it) }
        }
    }

    /**
     * Execute a macro by trigger name.
     * Each step is executed via [commandExecutor] with [macro.delayMs] pause between steps.
     *
     * @return true if a matching macro was found and started
     */
    suspend fun executeMacro(
        triggerName: String,
        commandExecutor: suspend (Command) -> Unit
    ): Boolean {
        val macro = findMacro(triggerName) ?: run {
            AppLogger.w(TAG, "No macro found for: \"$triggerName\"")
            return false
        }
        AppLogger.i(TAG, "Executing macro: \"${macro.name}\" (${macro.steps.size} steps)")

        macro.steps.forEachIndexed { index, step ->
            AppLogger.d(TAG, "Step ${index + 1}/${macro.steps.size}: $step")
            try {
                commandExecutor(step.toCommand())
            } catch (e: Exception) {
                AppLogger.e(TAG, "Step ${index + 1} failed: ${e.message}", e)
            }
            // Wait for UI to settle before next step
            if (index < macro.steps.size - 1) delay(macro.delayMs)
        }
        AppLogger.i(TAG, "Macro \"${macro.name}\" completed")
        return true
    }

    /** Save a new user-created macro. */
    fun saveMacro(macro: Macro) {
        val existing = loadUserMacros().toMutableList()
        existing.removeAll { it.id == macro.id } // replace if same id
        existing.add(macro)
        saveUserMacros(existing)
        AppLogger.i(TAG, "Saved macro: ${macro.name}")
    }

    /** Delete a user-created macro by id. Built-in macros are silently ignored. */
    fun deleteMacro(id: String) {
        val existing = loadUserMacros().toMutableList()
        val removed = existing.removeAll { it.id == id && !it.isDefault }
        if (removed) {
            saveUserMacros(existing)
            AppLogger.i(TAG, "Deleted macro: $id")
        }
    }

    /**
     * Create a new macro from a list of simple step descriptors and save it.
     *
     * Example:
     *   engine.createMacro("lunch break", listOf(
     *     MacroStep.openApp("spotify"),
     *     MacroStep.sendMessage("Mom", "Having lunch")
     *   ))
     */
    fun createMacro(name: String, description: String, steps: List<MacroStep>): Macro {
        val macro = Macro(
            id = UUID.randomUUID().toString(),
            name = name.lowercase(),
            description = description,
            steps = steps,
            delayMs = 1500L,
            isDefault = false
        )
        saveMacro(macro)
        return macro
    }

    // ── JSON serialisation ────────────────────────────────────────────

    private fun loadUserMacros(): List<Macro> {
        cachedUserMacros?.let { return it }

        return synchronized(cacheLock) {
            cachedUserMacros?.let { return@synchronized it }

            val json = prefs.getString(KEY_MACROS, "[]") ?: "[]"
            val loaded = runCatching { parseMacrosJson(json) }.getOrElse {
                AppLogger.e(TAG, "Failed to parse macros JSON", it)
                emptyList()
            }
            cachedUserMacros = loaded
            loaded
        }
    }

    private fun saveUserMacros(macros: List<Macro>) {
        val json = macrosToJson(macros)
        prefs.edit().putString(KEY_MACROS, json).apply()
        cachedUserMacros = macros
    }

    private fun parseMacrosJson(json: String): List<Macro> {
        val array = JSONArray(json)
        val result = ArrayList<Macro>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val stepsArray = obj.getJSONArray("steps")
            val steps = (0 until stepsArray.length()).map { j ->
                val s = stepsArray.getJSONObject(j)
                MacroStep(
                    type = s.getString("type"),
                    index = s.optInt("index", -1).takeIf { it >= 0 },
                    direction = s.optString("direction").ifEmpty { null },
                    appName = s.optString("appName").ifEmpty { null },
                    contact = s.optString("contact").ifEmpty { null },
                    message = s.optString("message").ifEmpty { null },
                    text = s.optString("text").ifEmpty { null }
                )
            }
            result.add(
                Macro(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    description = obj.optString("description"),
                    steps = steps,
                    delayMs = obj.optLong("delayMs", 1500L),
                    isDefault = false
                )
            )
        }
        return result
    }

    private fun macrosToJson(macros: List<Macro>): String {
        val array = JSONArray()
        macros.forEach { macro ->
            val obj = JSONObject().apply {
                put("id", macro.id)
                put("name", macro.name)
                put("description", macro.description)
                put("delayMs", macro.delayMs)
                val stepsArray = JSONArray()
                macro.steps.forEach { step ->
                    val s = JSONObject().apply {
                        put("type", step.type)
                        step.index?.let { put("index", it) }
                        step.direction?.let { put("direction", it) }
                        step.appName?.let { put("appName", it) }
                        step.contact?.let { put("contact", it) }
                        step.message?.let { put("message", it) }
                        step.text?.let { put("text", it) }
                    }
                    stepsArray.put(s)
                }
                put("steps", stepsArray)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
