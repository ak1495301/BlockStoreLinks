package com.duda.blockstorelinks

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

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
        val referrer = intent.getParcelableExtra<Uri>(Intent.EXTRA_REFERRER)
        if (referrer != null && referrer.scheme == "android-app") {
            return referrer.host
        }
        val referrerName = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
        if (referrerName != null && referrerName.startsWith("android-app://")) {
            return Uri.parse(referrerName).host
        }
        return intent.`package`
    }
}
