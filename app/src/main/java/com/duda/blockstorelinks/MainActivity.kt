package com.duda.blockstorelinks

import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import android.widget.EditText
import android.text.InputType
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    private lateinit var store: AppPolicyStore
    private lateinit var adapter: AppPolicyAdapter
    private lateinit var emptyState: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var filterText: String = ""
    private var sortMode: SortMode = SortMode.ATTEMPTS
    private val labelCache = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = AppPolicyStore(this)
        emptyState = findViewById(R.id.emptyState)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        val recyclerView = findViewById<RecyclerView>(R.id.appList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AppPolicyAdapter(
            context = this,
            onToggleBlocked = { policy, blocked ->
                store.setBlocked(policy.packageName, blocked)
                refreshList()
            },
            onPickRedirect = { policy ->
                showRedirectPicker(policy)
            },
            onEditAllowlist = { policy ->
                showAllowlistEditor(policy)
            },
            onResetAttempts = { policy ->
                store.resetAttempts(policy.packageName)
                refreshList()
            }
        )
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            refreshList()
        }

        val filterInput = findViewById<TextInputEditText>(R.id.filterInput)
        filterInput.addTextChangedListener { text ->
            filterText = text?.toString().orEmpty()
            refreshList()
        }

        val sortToggle = findViewById<MaterialButtonToggleGroup>(R.id.sortToggle)
        val sortAttempts = findViewById<MaterialButton>(R.id.sortAttempts)
        val sortName = findViewById<MaterialButton>(R.id.sortName)
        sortToggle.check(sortAttempts.id)
        sortToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            sortMode = if (checkedId == sortName.id) SortMode.NAME else SortMode.ATTEMPTS
            refreshList()
        }

        val exportPolicies = findViewById<MaterialButton>(R.id.exportPolicies)
        val importPolicies = findViewById<MaterialButton>(R.id.importPolicies)
        val resetAllCounts = findViewById<MaterialButton>(R.id.resetAllCounts)
        exportPolicies.setOnClickListener {
            val json = store.exportPoliciesJson()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, json)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export_policies)))
        }
        importPolicies.setOnClickListener {
            showImportDialog()
        }
        resetAllCounts.setOnClickListener {
            showResetAllDialog()
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val policies = store.getPolicies()
        val filtered = policies.filter { matchesFilter(it) }
        val sorted = when (sortMode) {
            SortMode.ATTEMPTS -> filtered.sortedByDescending { it.attemptCount }
            SortMode.NAME -> filtered.sortedBy { getAppLabel(it.packageName).lowercase() }
        }
        adapter.submitList(sorted)
        emptyState.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        swipeRefresh.isRefreshing = false
    }

    private fun showRedirectPicker(policy: AppPolicy) {
        val browsers = getBrowserTargets()
        val labels = mutableListOf(getString(R.string.clear_redirect))
        val packages = mutableListOf<String?>(null)

        browsers.forEach { info ->
            labels.add(info.loadLabel(packageManager).toString())
            packages.add(info.activityInfo.packageName)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pick_browser))
            .setItems(labels.toTypedArray()) { _, which ->
                val selectedPackage = packages[which]
                store.setRedirect(policy.packageName, selectedPackage)
                refreshList()
            }
            .show()
    }

    private fun showAllowlistEditor(policy: AppPolicy) {
        val input = EditText(this).apply {
            hint = getString(R.string.allowlist_hint)
            setText(policy.allowlistPatterns.joinToString(separator = "\n"))
            minLines = 4
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.allowlist))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val patterns = input.text.toString()
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                store.setAllowlist(policy.packageName, patterns)
                refreshList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showImportDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.import_hint)
            minLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_policies))
            .setView(input)
            .setPositiveButton(getString(R.string.import_replace)) { _, _ ->
                handleImport(input.text.toString(), merge = false)
            }
            .setNeutralButton(getString(R.string.import_merge)) { _, _ ->
                handleImport(input.text.toString(), merge = true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun handleImport(rawInput: String, merge: Boolean) {
        val raw = rawInput.trim()
        val payload = if (raw.isEmpty()) "[]" else raw
        try {
            if (merge) {
                store.mergePoliciesFromJson(payload)
                Toast.makeText(this, getString(R.string.import_success_merge), Toast.LENGTH_SHORT).show()
            } else {
                store.replacePoliciesFromJson(payload)
                Toast.makeText(this, getString(R.string.import_success), Toast.LENGTH_SHORT).show()
            }
            refreshList()
        } catch (ex: Exception) {
            Toast.makeText(this, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResetAllDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.reset_confirm_title))
            .setMessage(getString(R.string.reset_confirm_message))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                store.resetAllAttempts()
                refreshList()
                Toast.makeText(this, getString(R.string.reset_done), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun getBrowserTargets(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolved = packageManager.queryIntentActivities(intent, 0)
        return resolved.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun matchesFilter(policy: AppPolicy): Boolean {
        val query = filterText.trim().lowercase()
        if (query.isEmpty()) return true
        val label = getAppLabel(policy.packageName).lowercase()
        return policy.packageName.lowercase().contains(query) || label.contains(query)
    }

    private fun getAppLabel(packageName: String): String {
        return labelCache.getOrPut(packageName) {
            try {
                val info = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (ex: Exception) {
                getString(R.string.unknown_app)
            }
        }
    }

    private enum class SortMode {
        ATTEMPTS,
        NAME
    }
}
