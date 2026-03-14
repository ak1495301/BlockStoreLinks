package com.duda.blockstorelinks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AppPolicyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordAttempt(sourcePackage: String, url: String, timestamp: Long, outcome: LinkOutcome) {
        val policies = getPolicies().toMutableList()
        val policy = policies.find { it.packageName == sourcePackage }
        if (policy == null) {
            policies.add(
                AppPolicy(
                    packageName = sourcePackage,
                    attemptCount = 1,
                    linkAction = LinkAction.ALLOW,
                    redirectPackage = null,
                    allowlistPatterns = emptyList()
                )
            )
        } else {
            policy.attemptCount += 1
        }
        savePolicies(policies)
        recordAttemptLog(sourcePackage, url, timestamp, outcome)
    }

    fun updateAttemptOutcome(sourcePackage: String, timestamp: Long, outcome: LinkOutcome) {
        val logs = readAttemptLogs()
        val entries = logs[sourcePackage] ?: return
        val updated = entries.map {
            if (it.timestamp == timestamp) it.copy(outcome = outcome) else it
        }
        logs[sourcePackage] = updated
        saveAttemptLogs(logs)
    }

    fun getAttemptHistory(packageName: String): List<LinkAttempt> {
        return readAttemptLogs()[packageName].orEmpty()
            .sortedByDescending { it.timestamp }
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

    fun setLinkAction(packageName: String, action: LinkAction) {
        val policies = getPolicies().toMutableList()
        val policy = policies.find { it.packageName == packageName }
            ?: AppPolicy(packageName, 0, action, null, emptyList()).also { policies.add(it) }
        policy.linkAction = action
        savePolicies(policies)
    }

    fun setRedirect(packageName: String, redirectPackage: String?) {
        val policies = getPolicies().toMutableList()
        val policy = policies.find { it.packageName == packageName }
            ?: AppPolicy(packageName, 0, LinkAction.ALLOW, redirectPackage, emptyList()).also { policies.add(it) }
        policy.redirectPackage = redirectPackage
        savePolicies(policies)
    }

    fun setAllowlist(packageName: String, allowlistPatterns: List<String>) {
        val policies = getPolicies().toMutableList()
        val policy = policies.find { it.packageName == packageName }
            ?: AppPolicy(packageName, 0, LinkAction.ALLOW, null, allowlistPatterns).also { policies.add(it) }
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
        val payload = JSONObject()
        payload.put("version", 2)
        payload.put("globalRedirect", getGlobalRedirect() ?: "")
        val array = JSONArray()
        policies.forEach { policy ->
            array.put(writePolicy(policy))
        }
        payload.put("policies", array)
        payload.put("attemptLogs", writeAttemptLogs(readAttemptLogs()))
        return payload.toString()
    }

    fun countPoliciesFromJson(raw: String): Int {
        return parsePayload(raw).policies.size
    }

    fun replacePoliciesFromJson(raw: String): Boolean {
        val payload = parsePayload(raw)
        savePolicies(payload.policies)
        if (payload.hasGlobalRedirect) {
            setGlobalRedirect(payload.globalRedirect)
        }
        if (payload.hasLogs) {
            saveAttemptLogs(payload.attemptLogs)
        }
        return true
    }

    fun mergePoliciesFromJson(raw: String): Boolean {
        val payload = parsePayload(raw)
        val existing = getPolicies().associateBy { it.packageName }.toMutableMap()
        payload.policies.forEach { policy ->
            val current = existing[policy.packageName]
            if (current == null) {
                existing[policy.packageName] = policy
            } else {
                current.attemptCount += policy.attemptCount
                current.linkAction = policy.linkAction
                current.redirectPackage = policy.redirectPackage
                current.allowlistPatterns = policy.allowlistPatterns
            }
        }
        savePolicies(existing.values.toList())
        if (payload.hasGlobalRedirect) {
            setGlobalRedirect(payload.globalRedirect)
        }
        if (payload.hasLogs) {
            val mergedLogs = mergeAttemptLogs(readAttemptLogs(), payload.attemptLogs)
            saveAttemptLogs(mergedLogs)
        }
        return true
    }

    fun setGlobalRedirect(packageName: String?) {
        prefs.edit().putString(KEY_GLOBAL_REDIRECT, packageName.orEmpty()).apply()
    }

    fun getGlobalRedirect(): String? {
        return prefs.getString(KEY_GLOBAL_REDIRECT, "").takeIf { it?.isNotBlank() == true }
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
        val storedAction = obj.optString("linkAction", "").trim()
        val action = if (storedAction.isNotEmpty()) {
            LinkAction.fromStored(storedAction)
        } else if (obj.optBoolean("blocked", false)) {
            LinkAction.DENY
        } else {
            LinkAction.ALLOW
        }
        return AppPolicy(
            packageName = packageName,
            attemptCount = obj.optInt("attemptCount", 0),
            linkAction = action,
            redirectPackage = obj.optString("redirectPackage", "").takeIf { it.isNotBlank() },
            allowlistPatterns = readAllowlist(obj)
        )
    }

    private fun writePolicy(policy: AppPolicy): JSONObject {
        val obj = JSONObject()
        obj.put("packageName", policy.packageName)
        obj.put("attemptCount", policy.attemptCount)
        obj.put("blocked", policy.linkAction == LinkAction.DENY)
        obj.put("linkAction", policy.linkAction.name)
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

    private fun parsePayload(raw: String): ImportPayload {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            return ImportPayload(
                policies = readPoliciesFromArray(array),
                attemptLogs = emptyMap(),
                hasLogs = false,
                globalRedirect = null,
                hasGlobalRedirect = false
            )
        }
        val obj = JSONObject(trimmed)
        val policies = readPoliciesFromArray(obj.optJSONArray("policies") ?: JSONArray())
        val hasLogs = obj.has("attemptLogs")
        val hasGlobalRedirect = obj.has("globalRedirect")
        return ImportPayload(
            policies = policies,
            attemptLogs = readAttemptLogsFrom(obj.optJSONObject("attemptLogs")),
            hasLogs = hasLogs,
            globalRedirect = obj.optString("globalRedirect", "").takeIf { it.isNotBlank() },
            hasGlobalRedirect = hasGlobalRedirect
        )
    }

    private fun readPoliciesFromArray(array: JSONArray): List<AppPolicy> {
        val policies = mutableListOf<AppPolicy>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val policy = readPolicy(obj) ?: continue
            policies.add(policy)
        }
        return policies
    }

    private fun recordAttemptLog(
        sourcePackage: String,
        url: String,
        timestamp: Long,
        outcome: LinkOutcome
    ) {
        val logs = readAttemptLogs()
        val entries = logs[sourcePackage]?.toMutableList() ?: mutableListOf()
        entries.add(LinkAttempt(url = url, timestamp = timestamp, outcome = outcome))
        val trimmed = if (entries.size > MAX_HISTORY_PER_APP) {
            entries.takeLast(MAX_HISTORY_PER_APP)
        } else {
            entries
        }
        logs[sourcePackage] = trimmed
        saveAttemptLogs(logs)
    }

    private fun readAttemptLogs(): MutableMap<String, List<LinkAttempt>> {
        val raw = prefs.getString(KEY_ATTEMPT_LOGS, null) ?: return mutableMapOf()
        return readAttemptLogsFrom(JSONObject(raw)).toMutableMap()
    }

    private fun readAttemptLogsFrom(root: JSONObject?): Map<String, List<LinkAttempt>> {
        if (root == null) return emptyMap()
        val result = mutableMapOf<String, List<LinkAttempt>>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val packageName = keys.next()
            val array = root.optJSONArray(packageName) ?: continue
            val entries = mutableListOf<LinkAttempt>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val url = obj.optString("url", "")
                val timestamp = obj.optLong("timestamp", 0L)
                val outcome = LinkOutcome.fromStored(obj.optString("outcome", ""))
                if (url.isNotBlank() && timestamp > 0L) {
                    entries.add(LinkAttempt(url = url, timestamp = timestamp, outcome = outcome))
                }
            }
            result[packageName] = entries
        }
        return result
    }

    private fun saveAttemptLogs(logs: Map<String, List<LinkAttempt>>) {
        val root = writeAttemptLogs(logs)
        prefs.edit().putString(KEY_ATTEMPT_LOGS, root.toString()).apply()
    }

    private fun writeAttemptLogs(logs: Map<String, List<LinkAttempt>>): JSONObject {
        val root = JSONObject()
        logs.forEach { (packageName, entries) ->
            val array = JSONArray()
            entries.forEach { entry ->
                val obj = JSONObject()
                obj.put("url", entry.url)
                obj.put("timestamp", entry.timestamp)
                obj.put("outcome", entry.outcome.name)
                array.put(obj)
            }
            root.put(packageName, array)
        }
        return root
    }

    private fun mergeAttemptLogs(
        existing: Map<String, List<LinkAttempt>>,
        incoming: Map<String, List<LinkAttempt>>
    ): Map<String, List<LinkAttempt>> {
        val merged = existing.toMutableMap()
        incoming.forEach { (packageName, entries) ->
            val combined = (merged[packageName].orEmpty() + entries)
                .sortedBy { it.timestamp }
            val trimmed = if (combined.size > MAX_HISTORY_PER_APP) {
                combined.takeLast(MAX_HISTORY_PER_APP)
            } else {
                combined
            }
            merged[packageName] = trimmed
        }
        return merged
    }

    private data class ImportPayload(
        val policies: List<AppPolicy>,
        val attemptLogs: Map<String, List<LinkAttempt>>,
        val hasLogs: Boolean,
        val globalRedirect: String?,
        val hasGlobalRedirect: Boolean
    )

    companion object {
        private const val PREFS_NAME = "block_store_links"
        private const val KEY_POLICIES = "policies"
        private const val KEY_ATTEMPT_LOGS = "attempt_logs"
        private const val KEY_GLOBAL_REDIRECT = "global_redirect"
        private const val MAX_HISTORY_PER_APP = 200
    }
}
