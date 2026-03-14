package com.duda.blockstorelinks

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasPackage
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkInterceptorActivityTest {
    private lateinit var context: Context
    private lateinit var store: AppPolicyStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("block_store_links", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = AppPolicyStore(context)
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun blockedPolicy_opensBlockedActivity() {
        store.setBlocked("com.example.app", true)

        val intent = interceptorIntent("com.example.app")

        ActivityScenario.launch<LinkInterceptorActivity>(intent).use {
            Intents.intended(hasComponent(BlockedActivity::class.java.name))
        }
    }

    @Test
    fun allowlistMismatch_opensBlockedActivityWithReason() {
        store.setAllowlist("com.example.app", listOf("allowed.com"))

        val intent = interceptorIntent("com.example.app", "https://example.com")

        ActivityScenario.launch<LinkInterceptorActivity>(intent).use {
            Intents.intended(
                allOf(
                    hasComponent(BlockedActivity::class.java.name),
                    hasExtra(
                        BlockedActivity.EXTRA_REASON,
                        context.getString(R.string.allowlist_blocked_message)
                    )
                )
            )
        }
    }

    @Test
    fun redirectPolicy_forwardsToPackage() {
        store.setRedirect("com.example.app", "com.example.browser")

        val intent = interceptorIntent("com.example.app")

        Intents.intending(hasPackage("com.example.browser"))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()))

        ActivityScenario.launch<LinkInterceptorActivity>(intent).use {
            Intents.intended(
                allOf(
                    hasAction(Intent.ACTION_VIEW),
                    hasPackage("com.example.browser")
                )
            )
        }
    }

    @Test
    fun defaultPolicy_opensInAppBrowser() {
        val intent = interceptorIntent("com.example.app")

        ActivityScenario.launch<LinkInterceptorActivity>(intent).use {
            Intents.intended(hasComponent(BrowserActivity::class.java.name))
        }
    }

    private fun interceptorIntent(sourcePackage: String, url: String = "https://example.com"): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://$sourcePackage"))
        }
    }
}
