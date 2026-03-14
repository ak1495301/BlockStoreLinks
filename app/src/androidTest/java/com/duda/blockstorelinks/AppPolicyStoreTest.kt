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
        store.recordAttempt("com.example.app")
        val policy = store.getPolicy("com.example.app")
        assertNotNull(policy)
        assertEquals(1, policy?.attemptCount)
        assertEquals(false, policy?.blocked)
    }

    @Test
    fun exportAndReplace_roundTrip() {
        store.recordAttempt("com.example.app")
        store.setBlocked("com.example.app", true)
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
        assertEquals(true, policy?.blocked)
        assertEquals("com.example.browser", policy?.redirectPackage)
        assertEquals(2, policy?.allowlistPatterns?.size)
    }

    @Test
    fun mergePolicies_combinesCountsAndOverridesSettings() {
        store.recordAttempt("com.example.app")
        store.setBlocked("com.example.app", false)

        val array = JSONArray()
        val obj = JSONObject()
        obj.put("packageName", "com.example.app")
        obj.put("attemptCount", 2)
        obj.put("blocked", true)
        obj.put("redirectPackage", "com.example.browser")
        obj.put("allowlist", JSONArray(listOf("example.com")))
        array.put(obj)

        store.mergePoliciesFromJson(array.toString())

        val policy = store.getPolicy("com.example.app")
        assertEquals(3, policy?.attemptCount)
        assertEquals(true, policy?.blocked)
        assertEquals("com.example.browser", policy?.redirectPackage)
        assertEquals(1, policy?.allowlistPatterns?.size)
    }

    @Test
    fun resetAttempts_clearsCounts() {
        store.recordAttempt("com.example.app")
        store.recordAttempt("com.example.app")
        store.resetAttempts("com.example.app")
        assertEquals(0, store.getPolicy("com.example.app")?.attemptCount)

        store.recordAttempt("com.example.app")
        store.recordAttempt("com.other.app")
        store.resetAllAttempts()
        assertEquals(0, store.getPolicy("com.example.app")?.attemptCount)
        assertEquals(0, store.getPolicy("com.other.app")?.attemptCount)
    }
}
