package com.duda.blockstorelinks

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class LinkInterceptorActivity : AppCompatActivity() {
    private lateinit var store: AppPolicyStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AppPolicyStore(this)

        val link = intent?.data
        if (link == null) {
            finish()
            return
        }

        val sourcePackage = resolveSourcePackage(intent) ?: UNKNOWN_PACKAGE
        val sourceHint = buildSourceHint(intent)
        val policy = store.getPolicy(sourcePackage)
        val allowlist = policy?.allowlistPatterns ?: emptyList()
        val redirectRules = policy?.redirectRules ?: emptyList()
        val redirectRuleMatch = if (redirectRules.isNotEmpty()) {
            findRedirectRuleMatch(link.toString(), redirectRules)
        } else {
            null
        }
        val allowlistMatch = if (allowlist.isNotEmpty()) {
            findAllowlistMatch(link.toString(), allowlist)
        } else {
            AllowlistMatch(null)
        }
        val resolvedRedirect = redirectRuleMatch?.redirectPackage
            ?: allowlistMatch?.redirectPackage
            ?: policy?.redirectPackage
            ?: store.getGlobalRedirect()
        val action = policy?.linkAction ?: LinkAction.ALLOW
        val timestamp = System.currentTimeMillis()

        if (allowlist.isNotEmpty() && allowlistMatch == null && redirectRuleMatch == null) {
            store.recordAttempt(
                sourcePackage,
                link.toString(),
                timestamp,
                LinkOutcome.ALLOWLIST_BLOCKED,
                sourceHint
            )
            showBlockedToast(getString(R.string.allowlist_blocked_message))
            return
        }

        when (action) {
            LinkAction.DENY -> {
                store.recordAttempt(sourcePackage, link.toString(), timestamp, LinkOutcome.DENIED, sourceHint)
                showBlockedToast(getString(R.string.denied_message))
            }
            LinkAction.ASK -> {
                store.recordAttempt(sourcePackage, link.toString(), timestamp, LinkOutcome.ASKED, sourceHint)
                showAskDialog(sourcePackage, link.toString(), timestamp, resolvedRedirect)
            }
            LinkAction.ALLOW -> {
                handleAllowedLink(sourcePackage, link, timestamp, resolvedRedirect, sourceHint)
            }
        }
    }

    private fun handleAllowedLink(
        sourcePackage: String,
        link: Uri,
        timestamp: Long,
        redirectPackage: String?,
        sourceHint: String?
    ) {
        if (redirectPackage != null) {
            if (forwardToPackage(link, redirectPackage)) {
                store.recordAttempt(
                    sourcePackage,
                    link.toString(),
                    timestamp,
                    LinkOutcome.REDIRECTED,
                    sourceHint
                )
                finish()
                return
            }
        }
        store.recordAttempt(sourcePackage, link.toString(), timestamp, LinkOutcome.ALLOWED, sourceHint)
        val browserIntent = Intent(this, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_URL, link.toString())
        }
        startActivity(browserIntent)
        finish()
    }

    private fun showAskDialog(
        sourcePackage: String,
        url: String,
        timestamp: Long,
        redirectPackage: String?
    ) {
        val appLabel = resolveAppLabel(sourcePackage)
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.ask_title))
            .create()

        val density = resources.displayMetrics.density
        val padding = (20 * density).toInt()
        val spacing = (12 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val message = TextView(this).apply {
            text = getString(R.string.ask_message, appLabel, url)
        }

        val rowOne = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val rowTwo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val buttonParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val buttonParamsEnd = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = spacing
        }

        val allowOnce = MaterialButton(this).apply {
            text = getString(R.string.allow_action)
            setOnClickListener {
                dialog.dismiss()
                allowLink(sourcePackage, url, timestamp, redirectPackage, null)
            }
        }
        val denyOnce = MaterialButton(this).apply {
            text = getString(R.string.deny_action)
            setOnClickListener {
                dialog.dismiss()
                denyLink(sourcePackage, url, timestamp, null)
            }
        }
        val allowAlways = MaterialButton(this).apply {
            text = getString(R.string.always_allow_action)
            setOnClickListener {
                dialog.dismiss()
                allowLink(sourcePackage, url, timestamp, redirectPackage, LinkAction.ALLOW)
            }
        }
        val denyAlways = MaterialButton(this).apply {
            text = getString(R.string.always_deny_action)
            setOnClickListener {
                dialog.dismiss()
                denyLink(sourcePackage, url, timestamp, LinkAction.DENY)
            }
        }

        rowOne.addView(allowOnce, buttonParams)
        rowOne.addView(denyOnce, buttonParamsEnd)
        rowTwo.addView(allowAlways, buttonParams)
        rowTwo.addView(denyAlways, buttonParamsEnd)

        val rowTwoParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = spacing
        }

        container.addView(message)
        container.addView(rowOne, rowTwoParams)
        container.addView(rowTwo, rowTwoParams)

        dialog.setView(container)
        dialog.setOnCancelListener {
            denyLink(sourcePackage, url, timestamp, null)
        }
        dialog.show()
    }

    private fun allowLink(
        sourcePackage: String,
        url: String,
        timestamp: Long,
        redirectPackage: String?,
        persistAction: LinkAction?
    ) {
        if (persistAction != null) {
            store.setLinkAction(sourcePackage, persistAction)
        }
        val link = Uri.parse(url)
        if (redirectPackage != null && forwardToPackage(link, redirectPackage)) {
            store.updateAttemptOutcome(sourcePackage, timestamp, LinkOutcome.REDIRECTED)
            finish()
            return
        }
        store.updateAttemptOutcome(sourcePackage, timestamp, LinkOutcome.ALLOWED)
        val browserIntent = Intent(this, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_URL, url)
        }
        startActivity(browserIntent)
        finish()
    }

    private fun denyLink(
        sourcePackage: String,
        url: String,
        timestamp: Long,
        persistAction: LinkAction?
    ) {
        if (persistAction != null) {
            store.setLinkAction(sourcePackage, persistAction)
        }
        store.updateAttemptOutcome(sourcePackage, timestamp, LinkOutcome.DENIED)
        showBlockedToast(getString(R.string.denied_message))
    }

    private fun forwardToPackage(uri: Uri, targetPackage: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(targetPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            true
        } catch (ex: ActivityNotFoundException) {
            false
        }
    }

    private fun showBlockedToast(reason: String) {
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun resolveSourcePackage(intent: Intent): String? {
        val activityReferrer = referrer
        if (activityReferrer != null) {
            extractPackageFromReferrer(activityReferrer)?.let { return it }
        }
        val referrer = getParcelableUri(intent, Intent.EXTRA_REFERRER)
        if (referrer != null) {
            extractPackageFromReferrer(referrer)?.let { return it }
        }
        val referrerName = intent.getStringExtra(EXTRA_REFERRER_NAME)
        if (!referrerName.isNullOrBlank()) {
            extractPackageFromReferrerName(referrerName)?.let { return it }
        }
        val browserAppId = intent.getStringExtra(Browser.EXTRA_APPLICATION_ID)
        if (!browserAppId.isNullOrBlank()) {
            extractPackageFromReferrerName(browserAppId)?.let { return it }
        }
        val extraPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
        if (!extraPackageName.isNullOrBlank()) {
            extractPackageFromReferrerName(extraPackageName)?.let { return it }
        }
        val packageName = intent.`package`
        if (!packageName.isNullOrBlank()) {
            return packageName
        }
        return null
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

    private fun getParcelableUri(intent: Intent, key: String): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    }

    private fun extractPackageFromReferrer(referrer: Uri): String? {
        return when (referrer.scheme) {
            "android-app" -> referrer.host
            "package" -> referrer.schemeSpecificPart?.takeIf { it.matches(PACKAGE_REGEX) }
            else -> extractPackageFromReferrerName(referrer.toString())
        }
    }

    private fun extractPackageFromReferrerName(referrerName: String): String? {
        val trimmed = referrerName.trim()
        if (trimmed.startsWith("android-app://")) {
            return Uri.parse(trimmed).host
        }
        if (trimmed.startsWith("package:")) {
            return trimmed.removePrefix("package:").takeIf { it.matches(PACKAGE_REGEX) }
        }
        return trimmed.takeIf { it.matches(PACKAGE_REGEX) }
    }

    private fun buildSourceHint(intent: Intent): String? {
        val parts = mutableListOf<String>()
        val action = intent.action
        if (!action.isNullOrBlank()) {
            parts.add("action=$action")
        }
        val categories = intent.categories?.sorted()?.joinToString(",")
        if (!categories.isNullOrBlank()) {
            parts.add("categories=$categories")
        }
        val data = intent.data
        if (data != null) {
            data.scheme?.let { parts.add("data_scheme=$it") }
            data.host?.let { parts.add("data_host=$it") }
        }
        val activityReferrer = referrer
        if (activityReferrer != null) {
            parts.add("referrer=$activityReferrer")
        }
        val extraReferrer = getParcelableUri(intent, Intent.EXTRA_REFERRER)
        if (extraReferrer != null && extraReferrer != activityReferrer) {
            parts.add("extra_referrer=$extraReferrer")
        }
        val referrerName = intent.getStringExtra(EXTRA_REFERRER_NAME)
        if (!referrerName.isNullOrBlank()) {
            parts.add("referrer_name=$referrerName")
        }
        val browserAppId = intent.getStringExtra(Browser.EXTRA_APPLICATION_ID)
        if (!browserAppId.isNullOrBlank()) {
            parts.add("browser_app_id=$browserAppId")
        }
        val extraPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
        if (!extraPackageName.isNullOrBlank()) {
            parts.add("extra_package=$extraPackageName")
        }
        val intentPackage = intent.`package`
        if (!intentPackage.isNullOrBlank()) {
            parts.add("intent_package=$intentPackage")
        }
        val extras = intent.extras?.keySet()?.sorted()?.joinToString(",")
        if (!extras.isNullOrBlank()) {
            parts.add("extras=$extras")
        }
        if (parts.isEmpty()) return null
        return parts.joinToString(" | ").take(MAX_HINT_LENGTH)
    }

    private fun findAllowlistMatch(url: String, allowlist: List<String>): AllowlistMatch? {
        val normalized = url.lowercase()
        var regexCount = 0
        allowlist.forEach { raw ->
            val entry = parseAllowlistEntry(raw) ?: return@forEach
            val value = entry.pattern
            if (value.startsWith(REGEX_PREFIX)) {
                if (regexCount >= MAX_REGEX_PATTERNS) return@forEach
                regexCount += 1
                val patternBody = value.removePrefix(REGEX_PREFIX)
                if (patternBody.length > MAX_REGEX_LENGTH) return@forEach
                try {
                    val pattern = Pattern.compile(patternBody, Pattern.CASE_INSENSITIVE)
                    if (pattern.matcher(url).find()) {
                        return AllowlistMatch(entry.redirectPackage)
                    }
                } catch (ex: PatternSyntaxException) {
                    return@forEach
                }
            } else if (normalized.contains(value.lowercase())) {
                return AllowlistMatch(entry.redirectPackage)
            }
        }
        return null
    }

    private fun findRedirectRuleMatch(url: String, rules: List<String>): AllowlistMatch? {
        val normalized = url.lowercase()
        var regexCount = 0
        rules.forEach { raw ->
            val entry = parseRedirectRuleEntry(raw) ?: return@forEach
            val value = entry.pattern
            if (value.startsWith(REGEX_PREFIX)) {
                if (regexCount >= MAX_REGEX_PATTERNS) return@forEach
                regexCount += 1
                val patternBody = value.removePrefix(REGEX_PREFIX)
                if (patternBody.length > MAX_REGEX_LENGTH) return@forEach
                try {
                    val pattern = Pattern.compile(patternBody, Pattern.CASE_INSENSITIVE)
                    if (pattern.matcher(url).find()) {
                        return AllowlistMatch(entry.redirectPackage)
                    }
                } catch (ex: PatternSyntaxException) {
                    return@forEach
                }
            } else if (normalized.contains(value.lowercase())) {
                return AllowlistMatch(entry.redirectPackage)
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

    private data class AllowlistEntry(val pattern: String, val redirectPackage: String?)
    private data class AllowlistMatch(val redirectPackage: String?)

    companion object {
        private const val UNKNOWN_PACKAGE = "unknown"
        private const val EXTRA_REFERRER_NAME = "android.intent.extra.REFERRER_NAME"
        private const val REGEX_PREFIX = "re:"
        private const val REDIRECT_DELIMITER = "=>"
        private const val MAX_REGEX_LENGTH = 200
        private const val MAX_REGEX_PATTERNS = 20
        private val PACKAGE_REGEX = Regex("^[a-zA-Z0-9_.]+$")
        private const val MAX_HINT_LENGTH = 200
    }
}
