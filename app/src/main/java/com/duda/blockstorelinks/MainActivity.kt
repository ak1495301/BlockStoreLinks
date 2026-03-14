package com.duda.blockstorelinks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class MainActivity : AppCompatActivity() {
    private lateinit var store: AppPolicyStore
    private lateinit var adapter: AppPolicyAdapter
    private lateinit var emptyState: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var globalRedirectTarget: TextView
    private var filterText: String = ""
    private var sortMode: SortMode = SortMode.ATTEMPTS
    private val labelCache = mutableMapOf<String, String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = AppPolicyStore(this)
        emptyState = findViewById(R.id.emptyState)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        globalRedirectTarget = findViewById(R.id.globalRedirectTarget)

        val recyclerView = findViewById<RecyclerView>(R.id.appList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AppPolicyAdapter(
            context = this,
            onSelectAction = { policy, action ->
                store.setLinkAction(policy.packageName, action)
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
            },
            onShowHistory = { policy ->
                showHistoryDialog(policy)
            },
            resolveRedirectLabel = { policy ->
                resolveRedirectLabel(policy)
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
        val copyPolicies = findViewById<MaterialButton>(R.id.copyPolicies)
        val resetAllCounts = findViewById<MaterialButton>(R.id.resetAllCounts)
        val globalRedirect = findViewById<MaterialButton>(R.id.globalRedirect)
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
        copyPolicies.setOnClickListener {
            val json = store.exportPoliciesJson()
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.export_policies), json)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.copy_done), Toast.LENGTH_SHORT).show()
        }
        resetAllCounts.setOnClickListener {
            showResetAllDialog()
        }
        globalRedirect.setOnClickListener {
            showGlobalRedirectPicker()
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
        updateGlobalRedirectLabel()
    }

    private fun updateGlobalRedirectLabel() {
        val globalPackage = store.getGlobalRedirect()
        globalRedirectTarget.text = if (globalPackage == null) {
            getString(R.string.global_redirect_none)
        } else {
            getString(R.string.global_redirect_value, resolveAppLabel(globalPackage))
        }
    }

    private fun resolveRedirectLabel(policy: AppPolicy): String {
        return policy.redirectPackage?.let { resolveAppLabel(it) }
            ?: store.getGlobalRedirect()?.let { getString(R.string.redirect_global_label, resolveAppLabel(it)) }
            ?: getString(R.string.redirect_none)
    }

    private fun showRedirectPicker(policy: AppPolicy) {
        val browsers = getBrowserTargets()
        val labels = mutableListOf(
            if (store.getGlobalRedirect() == null) {
                getString(R.string.clear_redirect)
            } else {
                getString(R.string.redirect_follow_global)
            }
        )
        val packages = mutableListOf<String?>(null)

        browsers.forEach { info ->
            labels.add(info.loadLabel(packageManager).toString())
            packages.add(info.activityInfo.packageName)
        }

        labels.add(getString(R.string.enter_package))
        packages.add(MANUAL_ENTRY)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pick_browser))
            .setItems(labels.toTypedArray()) { _, which ->
                val selectedPackage = packages[which]
                if (selectedPackage == MANUAL_ENTRY) {
                    showPackageEntryDialog(
                        title = getString(R.string.enter_package_title),
                        initialValue = policy.redirectPackage,
                        onConfirmed = { manualPackage ->
                            store.setRedirect(policy.packageName, manualPackage)
                            refreshList()
                        }
                    )
                } else {
                    store.setRedirect(policy.packageName, selectedPackage)
                    refreshList()
                }
            }
            .show()
    }

    private fun showGlobalRedirectPicker() {
        val browsers = getBrowserTargets()
        val labels = mutableListOf(getString(R.string.clear_global_redirect))
        val packages = mutableListOf<String?>(null)

        browsers.forEach { info ->
            labels.add(info.loadLabel(packageManager).toString())
            packages.add(info.activityInfo.packageName)
        }

        labels.add(getString(R.string.enter_package))
        packages.add(MANUAL_ENTRY)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.global_redirect_title))
            .setItems(labels.toTypedArray()) { _, which ->
                val selectedPackage = packages[which]
                if (selectedPackage == MANUAL_ENTRY) {
                    showPackageEntryDialog(
                        title = getString(R.string.enter_package_title),
                        initialValue = store.getGlobalRedirect(),
                        onConfirmed = { manualPackage ->
                            store.setGlobalRedirect(manualPackage)
                            refreshList()
                        }
                    )
                } else {
                    store.setGlobalRedirect(selectedPackage)
                    refreshList()
                }
            }
            .show()
    }

    private fun showPackageEntryDialog(
        title: String,
        initialValue: String?,
        onConfirmed: (String?) -> Unit
    ) {
        val input = EditText(this).apply {
            hint = getString(R.string.enter_package_hint)
            setText(initialValue.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text.toString().trim().ifEmpty { null }
                onConfirmed(value)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAllowlistEditor(policy: AppPolicy) {
        val input = EditText(this).apply {
            hint = getString(R.string.allowlist_hint)
            setText(policy.allowlistPatterns.joinToString(separator = "\n"))
            minLines = 4
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.allowlist))
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val patterns = input.text.toString()
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val error = validateAllowlist(patterns)
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                store.setAllowlist(policy.packageName, patterns)
                refreshList()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showImportDialog() {
        val preview = TextView(this)
        val input = EditText(this).apply {
            hint = getString(R.string.import_hint)
            minLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            val previewParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val inputParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
            }
            addView(preview, previewParams)
            addView(input, inputParams)
        }
        val updatePreview = {
            val raw = input.text.toString().trim()
            val payload = if (raw.isEmpty()) "[]" else raw
            try {
                val count = store.countPoliciesFromJson(payload)
                preview.text = getString(R.string.import_preview_count, count)
            } catch (ex: Exception) {
                preview.text = getString(R.string.import_preview_invalid)
            }
        }
        input.addTextChangedListener { updatePreview() }
        updatePreview()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_policies))
            .setView(content)
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

    private fun validateAllowlist(patterns: List<String>): String? {
        var regexCount = 0
        patterns.forEachIndexed { index, raw ->
            val entry = parseAllowlistEntry(raw)
                ?: return getString(R.string.allowlist_error_empty_pattern, index + 1)
            if (entry.redirectPackage != null && !isValidPackage(entry.redirectPackage)) {
                return getString(R.string.allowlist_error_package, index + 1)
            }
            val value = entry.pattern
            if (value.startsWith(REGEX_PREFIX)) {
                regexCount += 1
                if (regexCount > MAX_REGEX_PATTERNS) {
                    return resources.getQuantityString(
                        R.plurals.allowlist_error_max_regex,
                        MAX_REGEX_PATTERNS,
                        MAX_REGEX_PATTERNS
                    )
                }
                val body = value.removePrefix(REGEX_PREFIX)
                if (body.length > MAX_REGEX_LENGTH) {
                    return resources.getQuantityString(
                        R.plurals.allowlist_error_regex_length,
                        MAX_REGEX_LENGTH,
                        index + 1,
                        MAX_REGEX_LENGTH
                    )
                }
                try {
                    Pattern.compile(body)
                } catch (ex: PatternSyntaxException) {
                    return getString(R.string.allowlist_error_regex_invalid, index + 1)
                }
            }
        }
        return null
    }

    private fun parseAllowlistEntry(raw: String): AllowlistEntry? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(REDIRECT_DELIMITER, limit = 2)
        val pattern = parts[0].trim()
        if (pattern.isEmpty()) return null
        val redirectPackage = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        return AllowlistEntry(pattern, redirectPackage)
    }

    private fun isValidPackage(value: String): Boolean {
        return PACKAGE_REGEX.matches(value) && value.contains('.')
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

    private fun showHistoryDialog(policy: AppPolicy) {
        val history = store.getAttemptHistory(policy.packageName)
        if (history.isEmpty()) {
            Toast.makeText(this, getString(R.string.history_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val items = history.map { formatAttempt(it) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.history_title, getAppLabel(policy.packageName)))
            .setItems(items.toTypedArray(), null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun formatAttempt(attempt: LinkAttempt): String {
        val date = dateFormat.format(Date(attempt.timestamp))
        val outcome = when (attempt.outcome) {
            LinkOutcome.ALLOWED -> getString(R.string.outcome_allowed)
            LinkOutcome.DENIED -> getString(R.string.outcome_denied)
            LinkOutcome.REDIRECTED -> getString(R.string.outcome_redirected)
            LinkOutcome.ASKED -> getString(R.string.outcome_asked)
            LinkOutcome.ALLOWLIST_BLOCKED -> getString(R.string.outcome_allowlist_blocked)
        }
        return getString(R.string.history_entry, date, outcome, attempt.url)
    }

    private fun getBrowserTargets(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
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
            resolveAppLabel(packageName)
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (ex: Exception) {
            getString(R.string.unknown_app)
        }
    }

    private enum class SortMode {
        ATTEMPTS,
        NAME
    }

    private data class AllowlistEntry(val pattern: String, val redirectPackage: String?)

    companion object {
        private const val REGEX_PREFIX = "re:"
        private const val REDIRECT_DELIMITER = "=>"
        private const val MAX_REGEX_LENGTH = 200
        private const val MAX_REGEX_PATTERNS = 20
        private const val MANUAL_ENTRY = "__manual_entry__"
        private val PACKAGE_REGEX = Regex("^[a-zA-Z0-9_.]+$")
    }
}
