package fuck.blockstorelinks.droidvendorssuck

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
            onEditRedirectRules = { policy ->
                showRedirectRulesEditor(policy)
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
        val addAppRule = findViewById<MaterialButton>(R.id.addAppRule)
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
        addAppRule.setOnClickListener {
            showAddAppRuleDialog()
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

    private fun showAddAppRuleDialog() {
        val apps = getLaunchableApps()
        val labels = mutableListOf<String>()
        val packages = mutableListOf<String?>()

        apps.forEach { info ->
            labels.add(info.loadLabel(packageManager).toString())
            packages.add(info.activityInfo.packageName)
        }

        labels.add(getString(R.string.enter_package))
        packages.add(MANUAL_ENTRY)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_app_rule_title))
            .setItems(labels.toTypedArray()) { _, which ->
                val selectedPackage = packages[which]
                if (selectedPackage == MANUAL_ENTRY) {
                    showPackageEntryDialog(
                        title = getString(R.string.enter_package_title),
                        initialValue = null,
                        onConfirmed = { manualPackage ->
                            addRuleForPackage(manualPackage)
                        }
                    )
                } else {
                    addRuleForPackage(selectedPackage)
                }
            }
            .show()
    }

    private fun addRuleForPackage(packageName: String?) {
        val trimmed = packageName?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        if (!isValidPackage(trimmed)) {
            Toast.makeText(this, getString(R.string.add_app_rule_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        val existing = store.getPolicy(trimmed)
        if (existing != null) {
            Toast.makeText(this, getString(R.string.add_app_rule_exists), Toast.LENGTH_SHORT).show()
            return
        }
        store.setLinkAction(trimmed, LinkAction.ASK)
        refreshList()
        Toast.makeText(this, getString(R.string.add_app_rule_added), Toast.LENGTH_SHORT).show()
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
            .setNeutralButton(getString(R.string.allowlist_add_redirect), null)
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
            val addRedirect = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            addRedirect.setOnClickListener {
                showPatternRedirectDialog(getString(R.string.allowlist_add_redirect_title)) { entry ->
                    val existing = input.text.toString().trim()
                    val updated = if (existing.isEmpty()) entry else "$existing\n$entry"
                    input.setText(updated)
                    input.setSelection(updated.length)
                }
            }
        }
        dialog.show()
    }

    private fun showRedirectRulesEditor(policy: AppPolicy) {
        val input = EditText(this).apply {
            hint = getString(R.string.redirect_rules_hint)
            setText(policy.redirectRules.joinToString(separator = "\n"))
            minLines = 4
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.redirect_rules))
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(getString(R.string.redirect_rules_add), null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val patterns = input.text.toString()
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val error = validateRedirectRules(patterns)
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                store.setRedirectRules(policy.packageName, patterns)
                refreshList()
                dialog.dismiss()
            }
            val addRedirect = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            addRedirect.setOnClickListener {
                showPatternRedirectDialog(getString(R.string.redirect_rules_add_title)) { entry ->
                    val existing = input.text.toString().trim()
                    val updated = if (existing.isEmpty()) entry else "$existing\n$entry"
                    input.setText(updated)
                    input.setSelection(updated.length)
                }
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

    private fun validateRedirectRules(patterns: List<String>): String? {
        var regexCount = 0
        patterns.forEachIndexed { index, raw ->
            val entry = parseRedirectRuleEntry(raw)
                ?: return getString(R.string.redirect_rules_error_format, index + 1)
            val target = entry.redirectPackage
                ?: return getString(R.string.redirect_rules_error_format, index + 1)
            if (!isValidPackage(target)) {
                return getString(R.string.redirect_rules_error_package, index + 1)
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

    private fun parseRedirectRuleEntry(raw: String): AllowlistEntry? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(REDIRECT_DELIMITER, limit = 2)
        if (parts.size < 2) return null
        val pattern = parts[0].trim()
        val redirectPackage = parts[1].trim()
        if (pattern.isEmpty() || redirectPackage.isEmpty()) return null
        return AllowlistEntry(pattern, redirectPackage)
    }

    private fun showPatternRedirectDialog(title: String, onAdd: (String) -> Unit) {
        val patternInput = EditText(this).apply {
            hint = getString(R.string.redirect_rules_pattern_hint)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val selectedLabel = TextView(this).apply {
            text = getString(R.string.redirect_rules_pick_placeholder)
        }
        var selectedPackage: String? = null
        val pickButton = MaterialButton(this).apply {
            text = getString(R.string.redirect_rules_pick_app)
            setOnClickListener {
                showRedirectPackagePicker { picked ->
                    if (picked.isNullOrBlank()) return@showRedirectPackagePicker
                    selectedPackage = picked
                    selectedLabel.text = picked
                }
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(pickButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(patternInput)
            addView(selectedLabel)
            addView(row)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setPositiveButton(getString(R.string.redirect_rules_add), null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            addButton.setOnClickListener {
                val pattern = patternInput.text.toString().trim()
                val target = selectedPackage?.trim().orEmpty()
                when {
                    pattern.isEmpty() -> Toast.makeText(
                        this,
                        getString(R.string.redirect_rules_error_missing_pattern),
                        Toast.LENGTH_SHORT
                    ).show()
                    target.isEmpty() -> Toast.makeText(
                        this,
                        getString(R.string.redirect_rules_error_missing_package),
                        Toast.LENGTH_SHORT
                    ).show()
                    !isValidPackage(target) -> Toast.makeText(
                        this,
                        getString(R.string.redirect_rules_error_package_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                    else -> {
                        onAdd("$pattern $REDIRECT_DELIMITER $target")
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showRedirectPackagePicker(onSelected: (String?) -> Unit) {
        val browsers = getBrowserTargets()
        val labels = mutableListOf<String>()
        val packages = mutableListOf<String?>()

        browsers.forEach { info ->
            labels.add(info.loadLabel(packageManager).toString())
            packages.add(info.activityInfo.packageName)
        }

        labels.add(getString(R.string.enter_package))
        packages.add(MANUAL_ENTRY)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.redirect_rules_pick_title))
            .setItems(labels.toTypedArray()) { _, which ->
                val selectedPackage = packages[which]
                if (selectedPackage == MANUAL_ENTRY) {
                    showPackageEntryDialog(
                        title = getString(R.string.enter_package_title),
                        initialValue = null,
                        onConfirmed = onSelected
                    )
                } else {
                    onSelected(selectedPackage)
                }
            }
            .show()
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
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.history_title, getAppLabel(policy.packageName)))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(getString(R.string.set_label)) { _, _ ->
                showLabelDialog(policy)
            }
        if (history.isEmpty()) {
            builder.setMessage(getString(R.string.history_empty))
        } else {
            val items = history.map { formatAttemptCompact(it) }
            builder.setItems(items.toTypedArray(), null)
        }
        builder.show()
    }

    private fun formatAttemptCompact(attempt: LinkAttempt): String {
        val date = dateFormat.format(Date(attempt.timestamp))
        val outcome = when (attempt.outcome) {
            LinkOutcome.ALLOWED -> getString(R.string.outcome_allowed)
            LinkOutcome.DENIED -> getString(R.string.outcome_denied)
            LinkOutcome.REDIRECTED -> getString(R.string.outcome_redirected)
            LinkOutcome.ASKED -> getString(R.string.outcome_asked)
            LinkOutcome.ALLOWLIST_BLOCKED -> getString(R.string.outcome_allowlist_blocked)
        }
        val url = attempt.url.trim()
        val compactUrl = if (url.length > MAX_HISTORY_URL_CHARS) {
            url.take(MAX_HISTORY_URL_CHARS).trimEnd() + "…"
        } else {
            url
        }
        val hint = attempt.sourceHint?.trim()
        return if (hint.isNullOrEmpty()) {
            getString(R.string.history_entry_compact, date, outcome, compactUrl)
        } else {
            val hintLine = getString(R.string.history_hint, hint)
            getString(R.string.history_entry_compact_hint, date, outcome, compactUrl, hintLine)
        }
    }

    private fun getBrowserTargets(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val resolved = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
        return resolved.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun getLaunchableApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
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
        store.getLabelOverride(packageName)?.let { return it }
        if (packageName == UNKNOWN_PACKAGE) {
            return getString(R.string.unknown_app_no_referrer)
        }
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (ex: Exception) {
            packageName
        }
    }

    private fun showLabelDialog(policy: AppPolicy) {
        val input = EditText(this).apply {
            hint = getString(R.string.set_label_hint)
            setText(store.getLabelOverride(policy.packageName).orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_label_title))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text.toString().trim().ifEmpty { null }
                store.setLabelOverride(policy.packageName, value)
                labelCache.remove(policy.packageName)
                refreshList()
                val message = if (value == null) {
                    getString(R.string.label_cleared)
                } else {
                    getString(R.string.label_saved)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        private const val UNKNOWN_PACKAGE = "unknown"
        private val PACKAGE_REGEX = Regex("^[a-zA-Z0-9_.]+$")
        private const val MAX_HISTORY_URL_CHARS = 80
    }
}
