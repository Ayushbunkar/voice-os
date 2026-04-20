package com.voiceos.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voiceos.R
import com.voiceos.api.CloudSyncManager
import com.voiceos.automation.AutomationEngine
import com.voiceos.automation.Macro
import com.voiceos.automation.MacroStep
import com.voiceos.databinding.ActivityMacrosBinding
import com.voiceos.utils.AppLogger
import com.voiceos.utils.AppUtils
import kotlinx.coroutines.launch

/**
 * MacrosActivity — Displays saved macros and allows creating new ones.
 *
 * Features:
 *   • RecyclerView list of all macros (built-in + user-created)
 *   • Long-press to delete user macros
 *   • "Run" button to execute any macro immediately
 *   • FAB (+) to add a new quick macro via dialog
 */
class MacrosActivity : AppCompatActivity() {

    private val TAG = "MacrosActivity"
    private lateinit var binding: ActivityMacrosBinding
    private lateinit var engine: AutomationEngine
    private lateinit var adapter: MacroAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMacrosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        engine = AutomationEngine.getInstance(this)

        setupToolbar()
        setupRecyclerView()
        setupFab()

        AppLogger.i(TAG, "MacrosActivity opened")
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching { CloudSyncManager.fetchCloudMacros() }
                .onSuccess { cloudMacros ->
                    cloudMacros.forEach { remote ->
                        val localMacro = Macro(
                            id = remote.id,
                            name = remote.name,
                            description = remote.description ?: "",
                            steps = remote.steps.map { step ->
                                MacroStep(
                                    type = step.action,
                                    index = step.index,
                                    direction = step.direction,
                                    appName = step.app,
                                    contact = step.target,
                                    message = step.message,
                                    text = step.text
                                )
                            },
                            delayMs = remote.delay_ms,
                            isDefault = false
                        )
                        engine.saveMacro(localMacro)
                    }
                }
                .onFailure {
                    AppLogger.w(TAG, "Cloud macro sync failed: ${it.message}")
                }

            refreshList()
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Automation Macros"
        }
    }

    private fun setupRecyclerView() {
        adapter = MacroAdapter(
            macros = engine.getAllMacros(),
            onRun = { macro -> runMacro(macro) },
            onDelete = { macro -> confirmDelete(macro) }
        )
        binding.rvMacros.layoutManager = LinearLayoutManager(this)
        binding.rvMacros.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddMacro.setOnClickListener { showAddMacroDialog() }
    }

    private fun refreshList() {
        adapter.update(engine.getAllMacros())
    }

    // ── Macro actions ─────────────────────────────────────────────────

    private fun runMacro(macro: Macro) {
        AppUtils.showToast(this, "Running: \"${macro.name}\"")
        val runIntent = Intent(this, FloatingWidgetService::class.java)
        startForegroundService(runIntent)
        // The macro will be triggered from the floating service; here we just
        // confirm to the user the service is alive
        AppUtils.showToast(this, "Say \"${macro.name}\" to the mic bubble!")
    }

    private fun confirmDelete(macro: Macro) {
        if (macro.isDefault) {
            AppUtils.showToast(this, "Built-in macros cannot be deleted")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete Macro")
            .setMessage("Delete \"${macro.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                engine.deleteMacro(macro.id)
                refreshList()
                AppUtils.showToast(this, "Macro deleted")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddMacroDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_macro, null)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMacroName)
        val etDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMacroDescription)
        val etApp = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMacroApp)
        val etContact = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMacroContact)
        val etMessage = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMacroMessage)

        AlertDialog.Builder(this)
            .setTitle("Create New Macro")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etName?.text?.toString()?.trim() ?: ""
                val desc = etDesc?.text?.toString()?.trim() ?: ""
                val app = etApp?.text?.toString()?.trim() ?: ""
                val contact = etContact?.text?.toString()?.trim() ?: ""
                val message = etMessage?.text?.toString()?.trim() ?: ""

                if (name.isEmpty()) {
                    AppUtils.showToast(this, "Macro name is required")
                    return@setPositiveButton
                }

                val steps = mutableListOf<MacroStep>()
                if (app.isNotEmpty()) steps.add(MacroStep.openApp(app))
                if (contact.isNotEmpty() && message.isNotEmpty()) {
                    steps.add(MacroStep.sendMessage(contact, message))
                }
                if (steps.isEmpty()) {
                    AppUtils.showToast(this, "Add at least one action")
                    return@setPositiveButton
                }

                engine.createMacro(name, desc, steps)
                refreshList()
                AppUtils.showToast(this, "Macro \"$name\" created!")
                AppLogger.i(TAG, "Created macro: $name")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ── RecyclerView Adapter ──────────────────────────────────────────

    inner class MacroAdapter(
        private var macros: List<Macro>,
        private val onRun: (Macro) -> Unit,
        private val onDelete: (Macro) -> Unit
    ) : RecyclerView.Adapter<MacroAdapter.MacroViewHolder>() {

        fun update(newList: List<Macro>) {
            macros = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MacroViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_macro, parent, false)
            return MacroViewHolder(v)
        }

        override fun onBindViewHolder(holder: MacroViewHolder, position: Int) {
            holder.bind(macros[position])
        }

        override fun getItemCount() = macros.size

        inner class MacroViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvName: TextView = view.findViewById(R.id.tvMacroName)
            private val tvDesc: TextView = view.findViewById(R.id.tvMacroDesc)
            private val tvSteps: TextView = view.findViewById(R.id.tvMacroSteps)
            private val tvBadge: TextView = view.findViewById(R.id.tvMacroBadge)
            private val btnRun: Button = view.findViewById(R.id.btnRunMacro)

            fun bind(macro: Macro) {
                tvName.text = macro.name.replaceFirstChar(Char::uppercaseChar)
                tvDesc.text = macro.description
                tvSteps.text = "${macro.steps.size} step${if (macro.steps.size != 1) "s" else ""}"
                tvBadge.visibility = if (macro.isDefault) View.VISIBLE else View.GONE
                btnRun.setOnClickListener { onRun(macro) }
                itemView.setOnLongClickListener { onDelete(macro); true }
            }
        }
    }
}
