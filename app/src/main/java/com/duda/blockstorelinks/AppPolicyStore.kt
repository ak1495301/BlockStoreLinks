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
                    redirectPackage = null,
                    allowlistPatterns = emptyList()
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
            val obj = array.optJSONObject(i) ?: continue
            val policy = readPolicy(obj) ?: continue
            policies.add(policy)
        }
        return policies.sortedByDescending { it.attemptCount }
    }

    fun getPolicy(packageName: String): AppPolicy? {
        return getPolicies().find { it.packageName == packageName }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        val policies = getPolicies().toMutableList()
        val policy = policies.find { it.packageName == packageName }
            ?: AppPolicy(packageName, 0, blocked, null, emptyList()).also { policies.add(it) }
        policy.blocked = blocked
        savePolicies(policies)
    }

    fun setRedirect(packageName: String, redirectPackage: String?) {
        val policies = getPolicies().toMutableList()
        val policy = policies.find { it.packageName == packageName }
            ?: AppPolicy(packageName, 0, false, redirectPackage, emptyList()).also { policies.add(it) }
        policy.redirectPackage = redirectPackage
        savePolicies(policies)
    }

    fun setAllowlist(packageName: String, allowlistPatterns: List<String>) {
        val policies = getPolicies().toMutableList()
        val policy = policies.find { it.packageName == packageName }
            ?: AppPolicy(packageName, 0, false, null, allowlistPatterns).also { policies.add(it) }
        policy.allowlistPatterns = allowlistPatterns
        savePolicies(policies)
    }

    fun resetAttempts(packageName: String) {
        val policies = getPolicies().toMutableList()
        val policy = policies.find { it.packageName == packageName } ?: return
        policy.attemptCount = 0
        savePolicies(policies)
    }

    fun resetAllAttempts() {
        val policies = getPolicies().toMutableList()
        policies.forEach { it.attemptCount = 0 }
        savePolicies(policies)
    }

    fun exportPoliciesJson(): String {
        val policies = getPolicies()
        val array = JSONArray()
        policies.forEach { policy ->
            array.put(writePolicy(policy))
        }
        return array.toString()
    }

    fun replacePoliciesFromJson(raw: String): Boolean {
        val array = JSONArray(raw)
        val policies = mutableListOf<AppPolicy>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val policy = readPolicy(obj) ?: continue
            policies.add(policy)
        }
        savePolicies(policies)
        return true
    }

    fun mergePoliciesFromJson(raw: String): Boolean {
        val array = JSONArray(raw)
        val existing = getPolicies().associateBy { it.packageName }.toMutableMap()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val policy = readPolicy(obj) ?: continue
            val current = existing[policy.packageName]
            if (current == null) {
                existing[policy.packageName] = policy
            } else {
                current.attemptCount += policy.attemptCount
                current.blocked = policy.blocked
                current.redirectPackage = policy.redirectPackage
                current.allowlistPatterns = policy.allowlistPatterns
            }
        }
        savePolicies(existing.values.toList())
        return true
    }

    private fun savePolicies(policies: List<AppPolicy>) {
        val array = JSONArray()
        policies.forEach { policy ->
            array.put(writePolicy(policy))
        }
        prefs.edit().putString(KEY_POLICIES, array.toString()).apply()
    }

    private fun readPolicy(obj: JSONObject): AppPolicy? {
        val packageName = obj.optString("packageName", "").trim()
        if (packageName.isEmpty()) return null
        return AppPolicy(
            packageName = packageName,
            attemptCount = obj.optInt("attemptCount", 0),
            blocked = obj.optBoolean("blocked", false),
            redirectPackage = obj.optString("redirectPackage", null).takeIf { it.isNotBlank() },
            allowlistPatterns = readAllowlist(obj)
        )
    }

    private fun writePolicy(policy: AppPolicy): JSONObject {
        val obj = JSONObject()
        obj.put("packageName", policy.packageName)
        obj.put("attemptCount", policy.attemptCount)
        obj.put("blocked", policy.blocked)
        obj.put("redirectPackage", policy.redirectPackage ?: "")
        obj.put("allowlist", JSONArray(policy.allowlistPatterns))
        return obj
    }

    private fun readAllowlist(obj: JSONObject): List<String> {
        val array = obj.optJSONArray("allowlist") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty()) {
                result.add(value)
            }
        }
        return result
    }

    companion object {
        private const val PREFS_NAME = "block_store_links"
        private const val KEY_POLICIES = "policies"
    }
}
