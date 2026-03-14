package com.duda.blockstorelinks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
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
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(Intent.createChooser(intent, getString(R.string.pick_browser)))
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_REASON = "extra_reason"
    }
}
