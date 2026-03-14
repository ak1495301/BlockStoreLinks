package com.duda.blockstorelinks

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
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
        val chooser = Intent.createChooser(baseIntent, getString(R.string.pick_browser)).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val exclude = arrayOf(ComponentName(this@BlockedActivity, LinkInterceptorActivity::class.java))
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, exclude)
            }
        }
        try {
            startActivity(chooser)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.no_external_browser), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, BrowserActivity::class.java).apply {
                putExtra(BrowserActivity.EXTRA_URL, url)
            })
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_REASON = "extra_reason"
    }
}
