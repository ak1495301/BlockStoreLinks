package com.duda.blockstorelinks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AppPolicyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordAttempt(sourcePackage: String) {
        val policies = getPolicies().toMutableList()
        val policy = policies.find { it.packageName == sourcePackage }
        if (policy == null) {
            policies.add(
                AppPolicy(
                    packageName = sourcePackage,
                    attemptCount = 1,
                    blocked = false,
                    redirectPackage = null
                )
            )
        } else {
            policy.attemptCount += 1
        }
        savePolicies(policies)
    }

    fun getPolicies(): List<AppPolicy> {
        val raw = prefs.getString(KEY_POLICIES, null) ?: return emptyList()
        val array = JSONArray(raw)
        val policies = mutableListOf<AppPolicy>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            policies.add(
                AppPolicy(
                    packageName = obj.getString("packageName"),
                    attemptCount = obj.optInt("attemptCount", 0),
                    blocked = obj.optBoolean("blocked", false),
                    redirectPackage = obj.optString("redirectPackage", null).takeIf { it.isNotBlank() }
                )
            )
        }
        return policies.sortedByDescending { it.attemptCount }
    }

    fun getPolicy(packageName: String): AppPolicy? {
        return getPolicies().find { it.packageName == packageName }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        val policies = getPolicies().toMutableList()
        val policy = policies.find { it.packageName == packageName }
            ?: AppPolicy(packageName, 0, blocked, null).also { policies.add(it) }
        policy.blocked = blocked
        savePolicies(policies)
    }

    fun setRedirect(packageName: String, redirectPackage: String?) {
        val policies = getPolicies().toMutableList()
        val policy = policies.find { it.packageName == packageName }
            ?: AppPolicy(packageName, 0, false, redirectPackage).also { policies.add(it) }
        policy.redirectPackage = redirectPackage
        savePolicies(policies)
    }

    private fun savePolicies(policies: List<AppPolicy>) {
        val array = JSONArray()
        policies.forEach { policy ->
            val obj = JSONObject()
            obj.put("packageName", policy.packageName)
            obj.put("attemptCount", policy.attemptCount)
            obj.put("blocked", policy.blocked)
            obj.put("redirectPackage", policy.redirectPackage ?: "")
            array.put(obj)
        }
        prefs.edit().putString(KEY_POLICIES, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "block_store_links"
        private const val KEY_POLICIES = "policies"
    }
}
