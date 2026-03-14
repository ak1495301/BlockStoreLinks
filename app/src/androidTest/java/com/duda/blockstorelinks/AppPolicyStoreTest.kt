package com.duda.blockstorelinks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPolicyStoreTest {
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
    }

    @Test
    fun recordAttempt_createsPolicy() {
        store.recordAttempt("com.example.app", "https://example.com", 1000L, LinkOutcome.ALLOWED)
        val policy = store.getPolicy("com.example.app")
        assertNotNull(policy)
        assertEquals(1, policy?.attemptCount)
        assertEquals(LinkAction.ALLOW, policy?.linkAction)
    }

    @Test
    fun exportAndReplace_roundTrip() {
        store.recordAttempt("com.example.app", "https://example.com", 1000L, LinkOutcome.ALLOWED)
        store.setLinkAction("com.example.app", LinkAction.DENY)
        store.setRedirect("com.example.app", "com.example.browser")
        store.setAllowlist("com.example.app", listOf("example.com", "re:^https://"))

        val exported = store.exportPoliciesJson()

        context.getSharedPreferences("block_store_links", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        store.replacePoliciesFromJson(exported)
        val policy = store.getPolicy("com.example.app")
        assertNotNull(policy)
        assertEquals(1, policy?.attemptCount)
        assertEquals(LinkAction.DENY, policy?.linkAction)
        assertEquals("com.example.browser", policy?.redirectPackage)
        assertEquals(2, policy?.allowlistPatterns?.size)
    }

    @Test
    fun mergePolicies_combinesCountsAndOverridesSettings() {
        store.recordAttempt("com.example.app", "https://example.com", 1000L, LinkOutcome.ALLOWED)
        store.setLinkAction("com.example.app", LinkAction.ALLOW)

        val array = JSONArray()
        val obj = JSONObject()
        obj.put("packageName", "com.example.app")
        obj.put("attemptCount", 2)
        obj.put("linkAction", "DENY")
        obj.put("redirectPackage", "com.example.browser")
        obj.put("allowlist", JSONArray(listOf("example.com")))
        array.put(obj)

        store.mergePoliciesFromJson(array.toString())

        val policy = store.getPolicy("com.example.app")
        assertEquals(3, policy?.attemptCount)
        assertEquals(LinkAction.DENY, policy?.linkAction)
        assertEquals("com.example.browser", policy?.redirectPackage)
        assertEquals(1, policy?.allowlistPatterns?.size)
    }

    @Test
    fun resetAttempts_clearsCounts() {
        store.recordAttempt("com.example.app", "https://example.com", 1000L, LinkOutcome.ALLOWED)
        store.recordAttempt("com.example.app", "https://example.com", 1001L, LinkOutcome.ALLOWED)
        store.resetAttempts("com.example.app")
        assertEquals(0, store.getPolicy("com.example.app")?.attemptCount)

        store.recordAttempt("com.example.app", "https://example.com", 1002L, LinkOutcome.ALLOWED)
        store.recordAttempt("com.other.app", "https://example.com", 1003L, LinkOutcome.ALLOWED)
        store.resetAllAttempts()
        assertEquals(0, store.getPolicy("com.example.app")?.attemptCount)
        assertEquals(0, store.getPolicy("com.other.app")?.attemptCount)
    }
}
