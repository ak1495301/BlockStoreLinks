package com.duda.blockstorelinks

import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class BlockedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        val url = intent.getStringExtra(EXTRA_URL)
        val reason = intent.getStringExtra(EXTRA_REASON)
        if (reason != null) {
            findViewById<android.widget.TextView>(R.id.blockedMessage).text = reason
        }
        val openButton = findViewById<Button>(R.id.openInBrowser)
        openButton.setOnClickListener {
            if (url != null) {
                openInExternalBrowser(url)
            }
            finish()
        }
    }

    private fun openInExternalBrowser(url: String) {
        val uri = Uri.parse(url)
        val baseIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val choices = packageManager.queryIntentActivities(baseIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .map { info -> toTargetedIntent(uri, info) }

        if (choices.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_external_browser), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, BrowserActivity::class.java).apply {
                putExtra(BrowserActivity.EXTRA_URL, url)
            })
            return
        }

        val chooser = Intent.createChooser(choices.first(), getString(R.string.pick_browser)).apply {
            val extras = choices.drop(1).toTypedArray()
            if (extras.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, extras)
            }
        }
        startActivity(chooser)
    }

    private fun toTargetedIntent(uri: Uri, info: ResolveInfo): Intent {
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(info.activityInfo.packageName)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_REASON = "extra_reason"
    }
}
