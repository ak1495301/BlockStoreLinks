package com.duda.blockstorelinks

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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

        val sourcePackage = resolveSourcePackage(intent) ?: "unknown"
        val policy = store.getPolicy(sourcePackage)
        val allowlist = policy?.allowlistPatterns ?: emptyList()
        val allowlistMatch = if (allowlist.isNotEmpty()) {
            findAllowlistMatch(link.toString(), allowlist)
        } else {
            AllowlistMatch(null)
        }
        val resolvedRedirect = allowlistMatch?.redirectPackage
            ?: policy?.redirectPackage
            ?: store.getGlobalRedirect()
        val action = policy?.linkAction ?: LinkAction.ALLOW
        val timestamp = System.currentTimeMillis()

        if (allowlist.isNotEmpty() && allowlistMatch == null) {
            store.recordAttempt(sourcePackage, link.toString(), timestamp, LinkOutcome.ALLOWLIST_BLOCKED)
            showBlocked(link.toString(), getString(R.string.allowlist_blocked_message))
            return
        }

        when (action) {
            LinkAction.DENY -> {
                store.recordAttempt(sourcePackage, link.toString(), timestamp, LinkOutcome.DENIED)
                showBlocked(link.toString(), getString(R.string.denied_message))
            }
            LinkAction.ASK -> {
                store.recordAttempt(sourcePackage, link.toString(), timestamp, LinkOutcome.ASKED)
                showAskDialog(sourcePackage, link.toString(), timestamp, resolvedRedirect)
            }
            LinkAction.ALLOW -> {
                handleAllowedLink(sourcePackage, link, timestamp, resolvedRedirect)
            }
        }
    }

    private fun handleAllowedLink(
        sourcePackage: String,
        link: Uri,
        timestamp: Long,
        redirectPackage: String?
    ) {
        if (redirectPackage != null) {
            if (forwardToPackage(link, redirectPackage)) {
                store.recordAttempt(sourcePackage, link.toString(), timestamp, LinkOutcome.REDIRECTED)
                finish()
                return
            }
        }
        store.recordAttempt(sourcePackage, link.toString(), timestamp, LinkOutcome.ALLOWED)
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
        showBlocked(url, getString(R.string.denied_message))
    }

    private fun showBlocked(url: String, reason: String) {
        val blockedIntent = Intent(this, BlockedActivity::class.java).apply {
            putExtra(BlockedActivity.EXTRA_URL, url)
            putExtra(BlockedActivity.EXTRA_REASON, reason)
        }
        startActivity(blockedIntent)
        finish()
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

    private fun resolveSourcePackage(intent: Intent): String? {
        val activityReferrer = referrer
        if (activityReferrer != null && activityReferrer.scheme == "android-app") {
            return activityReferrer.host
        }
        val referrer = getParcelableUri(intent, Intent.EXTRA_REFERRER)
        if (referrer != null && referrer.scheme == "android-app") {
            return referrer.host
        }
        val referrerName = intent.getStringExtra(EXTRA_REFERRER_NAME)
        if (referrerName != null && referrerName.startsWith("android-app://")) {
            return Uri.parse(referrerName).host
        }
        val packageName = intent.`package`
        if (!packageName.isNullOrBlank()) {
            return packageName
        }
        return null
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (ex: Exception) {
            getString(R.string.unknown_app)
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

    private fun parseAllowlistEntry(raw: String): AllowlistEntry? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(REDIRECT_DELIMITER, limit = 2)
        val pattern = parts[0].trim()
        if (pattern.isEmpty()) return null
        val redirectPackage = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        return AllowlistEntry(pattern, redirectPackage)
    }

    private data class AllowlistEntry(val pattern: String, val redirectPackage: String?)
    private data class AllowlistMatch(val redirectPackage: String?)

    companion object {
        private const val EXTRA_REFERRER_NAME = "android.intent.extra.REFERRER_NAME"
        private const val REGEX_PREFIX = "re:"
        private const val REDIRECT_DELIMITER = "=>"
        private const val MAX_REGEX_LENGTH = 200
        private const val MAX_REGEX_PATTERNS = 20
    }
}
