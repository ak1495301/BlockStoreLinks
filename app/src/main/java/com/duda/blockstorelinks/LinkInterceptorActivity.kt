package com.duda.blockstorelinks

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
        store.recordAttempt(sourcePackage)

        val policy = store.getPolicy(sourcePackage)
        val redirectPackage = policy?.redirectPackage
        val isBlocked = policy?.blocked ?: false
        val allowlist = policy?.allowlistPatterns ?: emptyList()

        if (allowlist.isNotEmpty() && !matchesAllowlist(link.toString(), allowlist)) {
            val blockedIntent = Intent(this, BlockedActivity::class.java).apply {
                putExtra(BlockedActivity.EXTRA_URL, link.toString())
                putExtra(BlockedActivity.EXTRA_REASON, getString(R.string.allowlist_blocked_message))
            }
            startActivity(blockedIntent)
            finish()
            return
        }

        if (redirectPackage != null) {
            if (forwardToPackage(link, redirectPackage)) {
                finish()
                return
            }
        }

        if (isBlocked) {
            val blockedIntent = Intent(this, BlockedActivity::class.java).apply {
                putExtra(BlockedActivity.EXTRA_URL, link.toString())
            }
            startActivity(blockedIntent)
            finish()
            return
        }

        val browserIntent = Intent(this, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_URL, link.toString())
        }
        startActivity(browserIntent)
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
        val referrer = getParcelableUri(intent, Intent.EXTRA_REFERRER)
        if (referrer != null && referrer.scheme == "android-app") {
            return referrer.host
        }
        val referrerName = intent.getStringExtra(EXTRA_REFERRER_NAME)
        if (referrerName != null && referrerName.startsWith("android-app://")) {
            return Uri.parse(referrerName).host
        }
        return intent.`package`
    }

    private fun getParcelableUri(intent: Intent, key: String): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    }

    private fun matchesAllowlist(url: String, allowlist: List<String>): Boolean {
        val normalized = url.lowercase()
        var regexCount = 0
        return allowlist.any { raw ->
            val value = raw.trim()
            if (value.startsWith(REGEX_PREFIX)) {
                if (regexCount >= MAX_REGEX_PATTERNS) return@any false
                regexCount += 1
                val patternBody = value.removePrefix(REGEX_PREFIX)
                if (patternBody.length > MAX_REGEX_LENGTH) return@any false
                try {
                    val pattern = Pattern.compile(patternBody, Pattern.CASE_INSENSITIVE)
                    pattern.matcher(url).find()
                } catch (ex: PatternSyntaxException) {
                    false
                }
            } else {
                normalized.contains(value.lowercase())
            }
        }
    }

    companion object {
        private const val EXTRA_REFERRER_NAME = "android.intent.extra.REFERRER_NAME"
        private const val REGEX_PREFIX = "re:"
        private const val MAX_REGEX_LENGTH = 200
        private const val MAX_REGEX_PATTERNS = 20
    }
}
